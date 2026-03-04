"""Real-time price fetching endpoint"""
import asyncio
import logging
from typing import Any, List

import yfinance as yf
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

router = APIRouter()
logger = logging.getLogger("app")

# yfinance는 내부적으로 동기 I/O를 사용하므로 이벤트 루프 블로킹을 피하기 위해
# 스레드 오프로딩 + 제한된 동시성으로 실행한다.
MAX_YF_CONCURRENCY = 8
_yf_semaphore = asyncio.Semaphore(MAX_YF_CONCURRENCY)


class PriceFetchRequest(BaseModel):
    tickers: List[str]
    market: str = "US"


def _unique_tickers(tickers: List[str]) -> List[str]:
    """입력 순서를 유지한 중복 제거."""
    return list(dict.fromkeys(tickers))


def _format_ticker_for_market(ticker: str, market: str) -> str:
    """시장별 티커 포맷 정규화."""
    if market == "KR" and not ticker.endswith((".KS", ".KQ")):
        return f"{ticker}.KS"
    return ticker


def _pick_current_price(info: dict | None):
    """info payload에서 현재가 후보를 우선순위대로 선택."""
    if not info:
        return None
    return info.get("currentPrice") or info.get("regularMarketPrice") or info.get("previousClose")


def _market_currency(market: str) -> str:
    """시장 코드 기반 기본 통화."""
    return "KRW" if market == "KR" else "USD"


def _calc_change_fields(price: Any, previous_close: Any) -> tuple[float | None, float | None]:
    """현재가/전일종가로 change, changePercent를 계산."""
    try:
        p = float(price) if price is not None else None
        prev = float(previous_close) if previous_close is not None else None
        if p is None or prev in (None, 0):
            return None, None
        change = p - prev
        return change, (change / prev) * 100.0
    except Exception:
        return None, None


async def _run_yf(sync_fn):
    """동기 yfinance 호출을 제한된 스레드 풀로 오프로딩."""
    async with _yf_semaphore:
        return await asyncio.to_thread(sync_fn)


@router.post("/prices/batch")
async def fetch_prices_batch(req: PriceFetchRequest):
    """Fetch current prices for multiple tickers in batch"""
    tickers = _unique_tickers(req.tickers)
    prices: dict[str, dict | None] = {}

    async def _fetch_ticker(ticker: str):
        try:
            def _fetch_one():
                formatted_ticker = _format_ticker_for_market(ticker, req.market)
                stock = yf.Ticker(formatted_ticker)

                fi = None
                try:
                    fi = getattr(stock, "fast_info", None)
                except Exception:
                    fi = None

                last_price = getattr(fi, "last_price", None) if fi is not None else None
                previous_close = getattr(fi, "previous_close", None) if fi is not None else None
                currency = getattr(fi, "currency", None) if fi is not None else None

                current_price = last_price or previous_close
                info = None
                if current_price is None or currency is None:
                    info = stock.info
                    current_price = current_price or _pick_current_price(info)
                    previous_close = previous_close or (info.get("previousClose") if info else None)
                    currency = currency or (info.get("currency") if info else None)

                if current_price is None:
                    return None

                change, change_percent = _calc_change_fields(current_price, previous_close)
                return {
                    "price": float(current_price),
                    "currency": currency or _market_currency(req.market),
                    "symbol": ticker,
                    "name": (info.get("shortName") if info else None) or ticker,
                    "change": change,
                    "changePercent": change_percent,
                }
            return ticker, await _run_yf(_fetch_one)
        except Exception as e:
            logger.warning("prices/batch fetch failed for ticker=%s market=%s: %s", ticker, req.market, e)
            return ticker, None

    results = await asyncio.gather(*[_fetch_ticker(ticker) for ticker in tickers])
    for ticker, payload in results:
        prices[ticker] = payload

    return {"prices": prices}


@router.post("/prices/info")
async def fetch_stock_info(req: PriceFetchRequest):
    """Fetch detailed stock info including sector for multiple tickers"""
    tickers = _unique_tickers(req.tickers)
    stock_info: dict[str, dict | None] = {}

    async def _fetch_ticker(ticker: str):
        try:
            def _fetch_one():
                formatted_ticker = _format_ticker_for_market(ticker, req.market)
                info = yf.Ticker(formatted_ticker).info
                current_price = _pick_current_price(info)
                previous_close = info.get("previousClose")
                return {
                    "price": float(current_price) if current_price is not None else None,
                    "previousClose": float(previous_close) if previous_close is not None else None,
                    "currency": info.get("currency", _market_currency(req.market)),
                    "symbol": ticker,
                    "name": info.get("shortName", ticker),
                    "sector": info.get("sector", "Unknown"),
                    "industry": info.get("industry", "Unknown"),
                    "marketCap": info.get("marketCap"),
                    "change": info.get("regularMarketChange"),
                    "changePercent": info.get("regularMarketChangePercent"),
                }
            return ticker, await _run_yf(_fetch_one)
        except Exception as e:
            logger.warning("prices/info fetch failed for ticker=%s market=%s: %s", ticker, req.market, e)
            return ticker, None

    results = await asyncio.gather(*[_fetch_ticker(ticker) for ticker in tickers])
    for ticker, payload in results:
        stock_info[ticker] = payload

    return {"stockInfo": stock_info}


@router.get("/prices/{ticker}")
async def fetch_single_price(ticker: str, market: str = "US"):
    """Fetch current price for a single ticker"""
    try:
        def _fetch_one():
            formatted_ticker = _format_ticker_for_market(ticker, market)
            info = yf.Ticker(formatted_ticker).info
            current_price = _pick_current_price(info)
            return info, current_price

        info, current_price = await _run_yf(_fetch_one)

        if not current_price:
            raise HTTPException(status_code=404, detail="Price not available")

        return {
            "ticker": ticker,
            "price": float(current_price),
            "currency": info.get("currency", "USD"),
            "name": info.get("shortName", ticker),
            "change": info.get("regularMarketChange"),
            "changePercent": info.get("regularMarketChangePercent"),
            "volume": info.get("volume"),
            "marketCap": info.get("marketCap"),
            "previousClose": info.get("previousClose")
        }
    except HTTPException:
        raise
    except Exception as e:
        # 내부 예외 문자열 노출 방지: 클라이언트에는 표준 메시지만 전달한다.
        logger.warning("prices/single fetch failed for ticker=%s market=%s: %s", ticker, market, e)
        raise HTTPException(status_code=502, detail="Price fetch failed")


@router.post("/prices/historical")
async def fetch_historical_prices(req: PriceFetchRequest):
    """Fetch historical closing prices for multiple tickers"""
    tickers = _unique_tickers(req.tickers)
    historical_data: dict[str, dict | None] = {}

    async def _fetch_ticker(ticker: str):
        try:
            def _fetch_one():
                formatted_ticker = _format_ticker_for_market(ticker, req.market)
                hist = yf.Ticker(formatted_ticker).history(period="35d")
                if hist.empty:
                    return None

                close = hist["Close"]
                current_price = float(close.iloc[-1])
                week_price = float(close.iloc[max(0, len(close) - 6)]) if len(close) >= 5 else None
                month_price = float(close.iloc[max(0, len(close) - 23)]) if len(close) >= 20 else None
                return {
                    "ticker": ticker,
                    "currentPrice": current_price,
                    "weekAgoPrice": week_price,
                    "monthAgoPrice": month_price,
                    "dataPoints": len(close),
                }
            return ticker, await _run_yf(_fetch_one)
        except Exception as e:
            logger.warning("prices/historical fetch failed for ticker=%s market=%s: %s", ticker, req.market, e)
            return ticker, None

    results = await asyncio.gather(*[_fetch_ticker(ticker) for ticker in tickers])
    for ticker, payload in results:
        historical_data[ticker] = payload

    return {"historicalPrices": historical_data}
