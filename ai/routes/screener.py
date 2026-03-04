"""
Stock screener endpoint
- ROE max 필터 버그 수정
- ThreadPoolExecutor 병렬 처리로 성능 개선
"""
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from fastapi import APIRouter
from pydantic import BaseModel
from typing import Optional
import yfinance as yf
import pandas as pd

router = APIRouter()
logger = logging.getLogger("app")

_SCREENER_WORKERS = 10  # 동시 yfinance 요청 수


class ScreenerFilters(BaseModel):
    peMin: Optional[float] = None
    peMax: Optional[float] = None
    psMin: Optional[float] = None
    psMax: Optional[float] = None
    pbMin: Optional[float] = None
    pbMax: Optional[float] = None
    roeMin: Optional[float] = None
    roeMax: Optional[float] = None
    revenueGrowthMin: Optional[float] = None
    dividendYieldMin: Optional[float] = None
    marketCapMin: Optional[int] = None
    marketCapMax: Optional[int] = None
    sector: Optional[str] = None
    rsiMin: Optional[int] = None
    rsiMax: Optional[int] = None
    limit: Optional[int] = 50


class ScreenerRequest(BaseModel):
    market: str = "US"
    filters: ScreenerFilters
    sortBy: str = "pe"
    sortOrder: str = "asc"


def _fetch_and_filter(ticker: str, filters: ScreenerFilters) -> Optional[dict]:
    """단일 티커 정보 조회 및 필터 적용 (스레드 풀에서 실행)"""
    try:
        info = yf.Ticker(ticker).info

        # ── 밸류에이션 필터 ──
        if filters.peMin is not None and (info.get("forwardPE") or 0) < filters.peMin:
            return None
        if filters.peMax is not None and (info.get("forwardPE") or float("inf")) > filters.peMax:
            return None
        if filters.psMin is not None and (info.get("priceToSalesTrailing12Months") or 0) < filters.psMin:
            return None
        if filters.psMax is not None and (info.get("priceToSalesTrailing12Months") or float("inf")) > filters.psMax:
            return None
        if filters.pbMin is not None and (info.get("priceToBook") or 0) < filters.pbMin:
            return None
        if filters.pbMax is not None and (info.get("priceToBook") or float("inf")) > filters.pbMax:
            return None

        # ── ROE 필터 (버그 수정: max 필터에 float("inf") 기본값 사용) ──
        roe = info.get("returnOnEquity")
        if filters.roeMin is not None and (roe or 0) < filters.roeMin:
            return None
        if filters.roeMax is not None and (roe if roe is not None else float("inf")) > filters.roeMax:
            return None

        # ── 성장/배당/시가총액 필터 ──
        if filters.revenueGrowthMin is not None and (info.get("revenueGrowth") or 0) < filters.revenueGrowthMin:
            return None
        if filters.dividendYieldMin is not None and (info.get("dividendYield") or 0) < filters.dividendYieldMin:
            return None
        if filters.marketCapMin is not None and (info.get("marketCap") or 0) < filters.marketCapMin:
            return None
        if filters.marketCapMax is not None and (info.get("marketCap") or float("inf")) > filters.marketCapMax:
            return None
        if filters.sector and info.get("sector") != filters.sector:
            return None

        return {
            "ticker": ticker,
            "name": info.get("shortName"),
            "sector": info.get("sector"),
            "price": info.get("currentPrice"),
            "pe": info.get("forwardPE"),
            "ps": info.get("priceToSalesTrailing12Months"),
            "pb": info.get("priceToBook"),
            "roe": roe,
            "revenueGrowth": info.get("revenueGrowth"),
            "dividendYield": info.get("dividendYield"),
            "marketCap": info.get("marketCap"),
            "overallScore": int(calculate_score(info)),
        }
    except Exception as e:
        logger.debug("Skipping %s: %s", ticker, e)
        return None


@router.post("/screener")
async def screen_stocks(req: ScreenerRequest):
    """종목 스크리너 - ThreadPoolExecutor 병렬 처리"""
    if req.market == "US":
        tickers = get_sp500_tickers()[:100]
    else:
        tickers = []

    limit = req.filters.limit or 50
    results = []

    # 병렬 처리로 대기 시간 대폭 단축 (순차 ~100초 → 병렬 ~10초)
    with ThreadPoolExecutor(max_workers=_SCREENER_WORKERS) as executor:
        futures = {executor.submit(_fetch_and_filter, t, req.filters): t for t in tickers}
        for future in as_completed(futures):
            result = future.result()
            if result is not None:
                results.append(result)

    # 정렬 (허용된 필드만)
    allowed_sort = {"pe", "ps", "pb", "roe", "price", "marketCap", "overallScore"}
    sort_key = req.sortBy if req.sortBy in allowed_sort else "pe"
    results.sort(
        key=lambda x: x.get(sort_key) or 0,
        reverse=(req.sortOrder == "desc"),
    )

    return results[:limit]


def get_sp500_tickers() -> list[str]:
    """S&P 500 티커 목록 조회 (위키피디아 → 하드코딩 폴백)"""
    try:
        tables = pd.read_html("https://en.wikipedia.org/wiki/List_of_S%26P_500_companies")
        return tables[0]["Symbol"].tolist()
    except Exception as e:
        logger.warning("Failed to fetch S&P 500 tickers from Wikipedia: %s", e)
        return [
            "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA", "JPM", "V", "JNJ",
            "WMT", "PG", "UNH", "HD", "BAC", "XOM", "MA", "DIS", "NFLX", "ADBE",
            "PYPL", "CRM", "INTC", "AMD", "QCOM", "TXN", "AVGO", "MU", "NOW", "INTU",
        ]


def calculate_score(info: dict) -> float:
    """펀더멘탈 기반 종합 점수 (0~100)"""
    score = 50.0

    pe = info.get("forwardPE")
    if pe and pe < 15:
        score += 10
    elif pe and pe > 30:
        score -= 10

    roe = info.get("returnOnEquity")
    if roe and roe > 0.15:
        score += 15
    elif roe and roe < 0.05:
        score -= 15

    revenue_growth = info.get("revenueGrowth")
    if revenue_growth and revenue_growth > 0.1:
        score += 10
    elif revenue_growth and revenue_growth < 0:
        score -= 10

    div_yield = info.get("dividendYield")
    if div_yield and div_yield > 0.02:
        score += 10

    return max(0.0, min(100.0, score))
