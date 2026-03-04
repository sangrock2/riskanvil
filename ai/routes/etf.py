"""
ETF-specific endpoints for holdings and information
"""
from fastapi import APIRouter, HTTPException
import yfinance as yf
from typing import Optional

router = APIRouter(prefix="/etf", tags=["ETF"])


@router.get("/holdings/{ticker}")
def get_etf_holdings(ticker: str, market: str = "US"):
    """
    Get ETF holdings/composition

    Args:
        ticker: ETF ticker symbol (e.g., SPY, QQQ, VOO)
        market: Market (US or KR)

    Returns:
        ETF holdings information including top holdings and sector allocation
    """
    try:
        # Add market suffix for KR stocks
        if market == "KR" and not ticker.endswith(".KS") and not ticker.endswith(".KQ"):
            ticker = f"{ticker}.KS"

        etf = yf.Ticker(ticker)
        info = etf.info

        # Check if it's actually an ETF
        quote_type = info.get("quoteType", "")
        if quote_type not in ["ETF", "MUTUALFUND"]:
            return {
                "ticker": ticker,
                "isETF": False,
                "message": f"{ticker} does not appear to be an ETF (type: {quote_type})"
            }

        # Extract ETF-specific information
        result = {
            "ticker": ticker,
            "isETF": True,
            "name": info.get("longName") or info.get("shortName"),
            "category": info.get("category"),
            "fundFamily": info.get("fundFamily"),
            "totalAssets": info.get("totalAssets"),
            "ytdReturn": info.get("ytdReturn"),
            "threeYearAverageReturn": info.get("threeYearAverageReturn"),
            "fiveYearAverageReturn": info.get("fiveYearAverageReturn"),
            "expenseRatio": info.get("annualReportExpenseRatio") or info.get("expenseRatio"),
            "yield": info.get("yield"),
            "beta": info.get("beta3Year"),
        }

        # Try to get holdings
        try:
            holdings = etf.funds_data
            if holdings:
                result["holdings"] = holdings.get("holdings", [])
                result["sectorWeightings"] = holdings.get("sectorWeightings", {})
                result["topHoldings"] = holdings.get("topHoldings", [])
        except Exception as e:
            print(f"Could not fetch holdings for {ticker}: {e}")
            result["holdings"] = []

        return result

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to fetch ETF data: {str(e)}")


@router.get("/info/{ticker}")
def get_etf_info(ticker: str, market: str = "US"):
    """
    Get basic ETF information (lighter than /holdings)

    Args:
        ticker: ETF ticker symbol
        market: Market (US or KR)

    Returns:
        Basic ETF information
    """
    try:
        if market == "KR" and not ticker.endswith(".KS") and not ticker.endswith(".KQ"):
            ticker = f"{ticker}.KS"

        etf = yf.Ticker(ticker)
        info = etf.info

        quote_type = info.get("quoteType", "")

        return {
            "ticker": ticker,
            "isETF": quote_type in ["ETF", "MUTUALFUND"],
            "quoteType": quote_type,
            "name": info.get("longName") or info.get("shortName"),
            "category": info.get("category"),
            "expenseRatio": info.get("annualReportExpenseRatio") or info.get("expenseRatio"),
            "totalAssets": info.get("totalAssets"),
            "ytdReturn": info.get("ytdReturn"),
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to fetch ETF info: {str(e)}")
