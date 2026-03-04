"""Earnings calendar endpoints."""
from datetime import datetime, timedelta
from typing import Optional

from fastapi import APIRouter
from pydantic import BaseModel, Field
import pandas as pd
import yfinance as yf

router = APIRouter()


class EarningsCalendarRequest(BaseModel):
    tickers: list[str] = Field(default_factory=list)
    market: str = "US"
    daysAhead: int = 90


def _format_ticker(ticker: str, market: str) -> str:
    symbol = ticker.upper().strip()
    if market == "KR" and not symbol.endswith(".KS") and not symbol.endswith(".KQ"):
        return f"{symbol}.KS"
    return symbol


def _to_float(value) -> Optional[float]:
    try:
        if value is None or pd.isna(value):
            return None
        return float(value)
    except Exception:
        return None


@router.post("/earnings/calendar")
async def earnings_calendar(req: EarningsCalendarRequest):
    days_ahead = min(max(req.daysAhead, 7), 365)
    today = datetime.utcnow().date()
    end = today + timedelta(days=days_ahead)
    events = []

    seen = set()
    for ticker in req.tickers:
        symbol = _format_ticker(ticker, req.market)

        try:
            stock = yf.Ticker(symbol)
            earnings = stock.get_earnings_dates(limit=12)

            if earnings is None or earnings.empty:
                continue

            for idx, row in earnings.iterrows():
                event_dt = pd.Timestamp(idx).date()
                if event_dt < today or event_dt > end:
                    continue

                key = (ticker.upper(), str(event_dt))
                if key in seen:
                    continue
                seen.add(key)

                events.append({
                    "ticker": ticker.upper(),
                    "market": req.market,
                    "earningsDate": event_dt.isoformat(),
                    "fiscalDateEnding": None,
                    "time": None,
                    "epsEstimate": _to_float(row.get("EPS Estimate")),
                    "epsActual": _to_float(row.get("Reported EPS")),
                    "revenueEstimate": None,
                    "revenueActual": None,
                })
        except Exception:
            continue

    events.sort(key=lambda e: e["earningsDate"])

    return {
        "daysAhead": days_ahead,
        "generatedAt": datetime.utcnow().isoformat(),
        "events": events,
    }
