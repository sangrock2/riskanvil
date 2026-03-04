"""Dividend data endpoint"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
import yfinance as yf
import pandas as pd
from datetime import datetime, timedelta
from typing import Optional, List

router = APIRouter()


class DividendRequest(BaseModel):
    ticker: str
    market: str = "US"


class DividendInfo(BaseModel):
    exDate: str
    paymentDate: Optional[str]
    recordDate: Optional[str]
    declaredDate: Optional[str]
    amount: float
    currency: str = "USD"
    frequency: Optional[str] = None


@router.post("/dividend/history")
async def get_dividend_history(req: DividendRequest):
    """Get historical dividend data for a ticker"""

    try:
        ticker_symbol = req.ticker

        # For Korean market, adjust ticker format
        if req.market == "KR" and not ticker_symbol.endswith(".KS") and not ticker_symbol.endswith(".KQ"):
            ticker_symbol = f"{ticker_symbol}.KS"  # Default to KOSPI

        # Download dividend data
        stock = yf.Ticker(ticker_symbol)
        dividends = stock.dividends

        if dividends is None or len(dividends) == 0:
            return {
                "ticker": req.ticker,
                "market": req.market,
                "dividends": [],
                "summary": {
                    "totalDividends": 0.0,
                    "averageDividend": 0.0,
                    "frequency": "none",
                    "lastDividend": None
                }
            }

        # Convert to list of dividend events
        dividend_list = []
        for date, amount in dividends.items():
            dividend_list.append({
                "exDate": date.strftime("%Y-%m-%d"),
                "amount": float(amount),
                "currency": "USD" if req.market == "US" else "KRW"
            })

        # Sort by date (most recent first)
        dividend_list.sort(key=lambda x: x["exDate"], reverse=True)

        # Calculate frequency
        frequency = estimate_dividend_frequency(dividends)

        # Get stock info for currency
        try:
            info = stock.info
            currency = info.get("currency", "USD" if req.market == "US" else "KRW")
        except:
            currency = "USD" if req.market == "US" else "KRW"

        # Update currency in dividend list
        for div in dividend_list:
            div["currency"] = currency

        # Calculate summary
        total_dividends = float(dividends.sum())
        avg_dividend = float(dividends.mean())
        last_dividend = dividend_list[0] if dividend_list else None

        return {
            "ticker": req.ticker,
            "market": req.market,
            "dividends": dividend_list,
            "summary": {
                "totalDividends": total_dividends,
                "averageDividend": avg_dividend,
                "frequency": frequency,
                "lastDividend": last_dividend,
                "currency": currency
            }
        }

    except Exception as e:
        raise HTTPException(500, f"Failed to fetch dividend data: {str(e)}")


@router.post("/dividend/upcoming")
async def get_upcoming_dividends(req: DividendRequest):
    """Estimate upcoming dividend based on historical pattern"""

    try:
        ticker_symbol = req.ticker

        if req.market == "KR" and not ticker_symbol.endswith(".KS") and not ticker_symbol.endswith(".KQ"):
            ticker_symbol = f"{ticker_symbol}.KS"

        stock = yf.Ticker(ticker_symbol)
        dividends = stock.dividends

        if dividends is None or len(dividends) == 0:
            return {
                "ticker": req.ticker,
                "market": req.market,
                "hasUpcoming": False,
                "estimated": None
            }

        # Get last few dividends to estimate pattern
        recent = dividends.tail(4)
        if len(recent) < 2:
            return {
                "ticker": req.ticker,
                "market": req.market,
                "hasUpcoming": False,
                "estimated": None
            }

        # Calculate average interval between dividends
        dates = [d.to_pydatetime() for d in recent.index]
        intervals = [(dates[i] - dates[i-1]).days for i in range(1, len(dates))]
        avg_interval = sum(intervals) / len(intervals)

        # Estimate next dividend date
        last_date = dates[-1]
        estimated_next = last_date + timedelta(days=avg_interval)

        # Get average amount from recent dividends
        avg_amount = float(recent.mean())

        # Get currency
        try:
            info = stock.info
            currency = info.get("currency", "USD" if req.market == "US" else "KRW")
        except:
            currency = "USD" if req.market == "US" else "KRW"

        return {
            "ticker": req.ticker,
            "market": req.market,
            "hasUpcoming": True,
            "estimated": {
                "exDate": estimated_next.strftime("%Y-%m-%d"),
                "amount": avg_amount,
                "currency": currency,
                "confidence": "estimated"
            }
        }

    except Exception as e:
        raise HTTPException(500, f"Failed to estimate upcoming dividend: {str(e)}")


def estimate_dividend_frequency(dividends: pd.Series) -> str:
    """Estimate dividend payment frequency"""

    if len(dividends) < 2:
        return "unknown"

    # Get dates
    dates = [d.to_pydatetime() for d in dividends.index]

    # Calculate average interval in days
    intervals = [(dates[i] - dates[i-1]).days for i in range(1, len(dates))]
    avg_interval = sum(intervals) / len(intervals)

    # Classify frequency
    if avg_interval < 45:
        return "monthly"
    elif avg_interval < 120:
        return "quarterly"
    elif avg_interval < 210:
        return "semi-annually"
    elif avg_interval < 400:
        return "annually"
    else:
        return "irregular"
