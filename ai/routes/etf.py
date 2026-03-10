"""
ETF-specific endpoints for holdings and information.
Rate-limit or upstream 데이터 누락 시에도 500 대신 최소 스키마를 반환한다.
"""
from __future__ import annotations

import logging
from datetime import datetime
from typing import Any

import yfinance as yf
from fastapi import APIRouter

router = APIRouter(prefix="/etf", tags=["ETF"])
logger = logging.getLogger("app")

# info가 비어도 US 대표 ETF는 ETF로 판정한다.
_US_ETF_HINTS = {"SPY", "QQQ", "VOO", "VTI", "IVV", "DIA", "IWM", "ARKK"}


def _normalize_ticker(ticker: str, market: str) -> str:
    mk = (market or "US").upper()
    sym = (ticker or "").strip().upper()
    if mk == "KR" and not sym.endswith(".KS") and not sym.endswith(".KQ"):
        return f"{sym}.KS"
    return sym


def _safe_info(etf: yf.Ticker, ticker: str) -> dict[str, Any]:
    try:
        return etf.info or {}
    except Exception as e:
        logger.warning("ETF info fetch failed for %s: %s", ticker, e)
        return {}


def _safe_last_close(etf: yf.Ticker) -> float | None:
    try:
        hist = etf.history(period="5d")
        if hist is not None and not hist.empty and "Close" in hist.columns:
            return float(hist["Close"].iloc[-1])
    except Exception:
        return None
    return None


def _to_jsonable(value: Any) -> Any:
    if value is None:
        return None
    if hasattr(value, "to_dict"):
        try:
            return value.to_dict(orient="records")  # pandas DataFrame
        except TypeError:
            try:
                return value.to_dict()
            except Exception:
                return None
        except Exception:
            return None
    if isinstance(value, (dict, list, str, int, float, bool)):
        return value
    return None


@router.get("/holdings/{ticker}")
def get_etf_holdings(ticker: str, market: str = "US"):
    """
    ETF holdings/composition 조회.
    yfinance 실패 시에도 200으로 최소 응답을 반환한다.
    """
    symbol = _normalize_ticker(ticker, market)
    etf = yf.Ticker(symbol)
    info = _safe_info(etf, symbol)

    quote_type = str(info.get("quoteType") or "").upper()
    is_etf = quote_type in {"ETF", "MUTUALFUND"} or symbol in _US_ETF_HINTS

    result: dict[str, Any] = {
        "ticker": symbol,
        "isETF": is_etf,
        "name": info.get("longName") or info.get("shortName") or symbol,
        "category": info.get("category"),
        "fundFamily": info.get("fundFamily"),
        "totalAssets": info.get("totalAssets"),
        "ytdReturn": info.get("ytdReturn"),
        "threeYearAverageReturn": info.get("threeYearAverageReturn"),
        "fiveYearAverageReturn": info.get("fiveYearAverageReturn"),
        "expenseRatio": info.get("annualReportExpenseRatio") or info.get("expenseRatio"),
        "yield": info.get("yield"),
        "beta": info.get("beta3Year"),
        "holdings": [],
        "sectorWeightings": {},
        "topHoldings": [],
        "asOf": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
    }

    try:
        funds_data = etf.funds_data
        if isinstance(funds_data, dict):
            result["holdings"] = _to_jsonable(funds_data.get("holdings")) or []
            result["sectorWeightings"] = _to_jsonable(funds_data.get("sectorWeightings")) or {}
            result["topHoldings"] = _to_jsonable(funds_data.get("topHoldings")) or []
        else:
            # yfinance versions may expose fields as attributes
            result["holdings"] = _to_jsonable(getattr(funds_data, "holdings", None)) or []
            result["sectorWeightings"] = _to_jsonable(getattr(funds_data, "sector_weightings", None)) or {}
            result["topHoldings"] = _to_jsonable(getattr(funds_data, "top_holdings", None)) or []
    except Exception as e:
        logger.warning("ETF holdings fetch degraded for %s: %s", symbol, e)
        result["_degraded"] = {
            "reason": "holdings_unavailable",
            "message": str(e),
            "source": "yfinance",
        }

    return result


@router.get("/info/{ticker}")
def get_etf_info(ticker: str, market: str = "US"):
    """
    ETF 기본 정보 조회.
    yfinance 레이트리밋 시에도 최소 필드로 응답한다.
    """
    symbol = _normalize_ticker(ticker, market)
    etf = yf.Ticker(symbol)
    info = _safe_info(etf, symbol)

    quote_type = str(info.get("quoteType") or "").upper()
    is_etf = quote_type in {"ETF", "MUTUALFUND"} or symbol in _US_ETF_HINTS

    result: dict[str, Any] = {
        "ticker": symbol,
        "isETF": is_etf,
        "quoteType": quote_type or None,
        "name": info.get("longName") or info.get("shortName") or symbol,
        "category": info.get("category"),
        "expenseRatio": info.get("annualReportExpenseRatio") or info.get("expenseRatio"),
        "totalAssets": info.get("totalAssets"),
        "ytdReturn": info.get("ytdReturn"),
        "asOf": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
    }

    # info가 비거나 rate-limit이면 최소한 최근 종가를 넣어준다.
    if not info:
        last_close = _safe_last_close(etf)
        result["lastClose"] = last_close
        result["_degraded"] = {
            "reason": "info_unavailable",
            "source": "yfinance",
        }

    return result
