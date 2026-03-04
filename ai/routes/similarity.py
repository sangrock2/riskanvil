"""
유사 종목 추천 엔드포인트
- 펀더멘탈 지표 벡터의 코사인 유사도 기반 종목 추천
- S&P 500 상위 100개 종목 중 유사도 상위 N개 반환
"""
import logging
from typing import Optional

import numpy as np
import yfinance as yf
import pandas as pd
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from concurrent.futures import ThreadPoolExecutor, as_completed

router = APIRouter(prefix="/similarity", tags=["Similar Stocks"])
logger = logging.getLogger("app")

_WORKERS = 15

# 펀더멘탈 비교에 사용할 지표 (순서 고정)
_FEATURE_KEYS = [
    "forwardPE",
    "priceToBook",
    "priceToSalesTrailing12Months",
    "returnOnEquity",
    "revenueGrowth",
    "grossMargins",
    "operatingMargins",
    "debtToEquity",
    "dividendYield",
]

# S&P 500 대표 100 종목 (Wikipedia 조회 실패 시 폴백)
_SP500_FALLBACK = [
    "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA", "JPM", "V", "JNJ",
    "WMT", "PG", "UNH", "HD", "BAC", "XOM", "MA", "DIS", "NFLX", "ADBE",
    "PYPL", "CRM", "INTC", "AMD", "QCOM", "TXN", "AVGO", "MU", "NOW", "INTU",
    "COST", "LLY", "ABBV", "PFE", "MRK", "ABT", "TMO", "DHR", "NEE", "LIN",
    "PM", "RTX", "HON", "UPS", "CAT", "BA", "GE", "MMM", "IBM", "CVX",
    "COP", "SLB", "BKR", "EOG", "MPC", "PSX", "VLO", "HAL", "OXY", "DVN",
    "GS", "MS", "BLK", "SPGI", "CME", "ICE", "CB", "AIG", "MET", "PRU",
    "AMGN", "GILD", "BIIB", "VRTX", "REGN", "ISRG", "SYK", "MDT", "EW", "ZBH",
    "SBUX", "MCD", "YUM", "CMG", "DPZ", "QSR", "HLT", "MAR", "H", "WH",
    "ORCL", "SAP", "CSCO", "ACN", "AMAT", "KLAC", "LRCX", "MRVL", "SNPS", "CDNS",
]


class SimilarStocksRequest(BaseModel):
    ticker: str                      # 기준 종목
    market: str = "US"               # US만 지원 (확장 가능)
    topN: int = 10                   # 반환할 유사 종목 수 (최대 20)
    excludeSector: bool = False      # True: 같은 섹터 종목 제외


class SimilarStock(BaseModel):
    ticker: str
    name: Optional[str]
    sector: Optional[str]
    similarity: float                # 코사인 유사도 (0~1)
    pe: Optional[float]
    pb: Optional[float]
    roe: Optional[float]
    revenueGrowth: Optional[float]
    marketCap: Optional[int]


def _get_info(ticker: str) -> Optional[dict]:
    try:
        return yf.Ticker(ticker).info
    except Exception as e:
        logger.debug("Info fetch failed for %s: %s", ticker, e)
        return None


def _to_feature_vector(info: dict) -> np.ndarray:
    """지표를 정규화된 피처 벡터로 변환 (누락값 → 0)"""
    vec = []
    for key in _FEATURE_KEYS:
        val = info.get(key)
        vec.append(float(val) if val is not None and np.isfinite(float(val)) else 0.0)
    return np.array(vec, dtype=float)


def _cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    norm_a = np.linalg.norm(a)
    norm_b = np.linalg.norm(b)
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return float(np.dot(a, b) / (norm_a * norm_b))


def _get_sp500_tickers() -> list[str]:
    try:
        tables = pd.read_html("https://en.wikipedia.org/wiki/List_of_S%26P_500_companies")
        return tables[0]["Symbol"].tolist()[:150]
    except Exception:
        return _SP500_FALLBACK


@router.post("/find", response_model=list[SimilarStock])
def find_similar_stocks(req: SimilarStocksRequest):
    """펀더멘탈 기반 유사 종목 추천 (코사인 유사도)"""
    ticker = req.ticker.strip().upper()
    top_n = max(1, min(req.topN, 20))

    # 기준 종목 정보 조회
    base_info = _get_info(ticker)
    if not base_info or not base_info.get("regularMarketPrice") and not base_info.get("currentPrice"):
        raise HTTPException(404, f"종목 정보를 찾을 수 없습니다: {ticker}")

    base_vec = _to_feature_vector(base_info)
    base_sector = base_info.get("sector")

    # 비교 대상 종목 풀
    candidates = [t for t in _get_sp500_tickers() if t != ticker]

    # 병렬 조회
    info_map: dict[str, dict] = {}
    with ThreadPoolExecutor(max_workers=_WORKERS) as executor:
        futures = {executor.submit(_get_info, t): t for t in candidates}
        for future in as_completed(futures):
            t = futures[future]
            result = future.result()
            if result:
                info_map[t] = result

    # 유사도 계산
    similarities: list[tuple[float, str, dict]] = []
    for t, info in info_map.items():
        if req.excludeSector and base_sector and info.get("sector") == base_sector:
            continue
        vec = _to_feature_vector(info)
        sim = _cosine_similarity(base_vec, vec)
        similarities.append((sim, t, info))

    similarities.sort(key=lambda x: x[0], reverse=True)

    return [
        SimilarStock(
            ticker=t,
            name=info.get("shortName"),
            sector=info.get("sector"),
            similarity=round(sim, 4),
            pe=info.get("forwardPE"),
            pb=info.get("priceToBook"),
            roe=info.get("returnOnEquity"),
            revenueGrowth=info.get("revenueGrowth"),
            marketCap=info.get("marketCap"),
        )
        for sim, t, info in similarities[:top_n]
    ]
