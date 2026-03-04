"""Portfolio risk dashboard endpoint."""
from datetime import datetime
from typing import Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
import numpy as np
import pandas as pd
import yfinance as yf

# Optional Korean market data support
try:
    import FinanceDataReader as fdr
    HAS_FDR = True
except ImportError:
    HAS_FDR = False
    fdr = None

router = APIRouter()


class HoldingInput(BaseModel):
    ticker: str
    market: str = "US"
    value: float
    weightPct: Optional[float] = None


class PortfolioRiskRequest(BaseModel):
    holdings: list[HoldingInput] = Field(default_factory=list)
    lookbackDays: int = 252


def _format_ticker(ticker: str, market: str) -> str:
    symbol = ticker.upper().strip()
    if market == "KR" and not symbol.endswith(".KS") and not symbol.endswith(".KQ"):
        return f"{symbol}.KS"
    return symbol


def _fetch_close_series(ticker: str, market: str, days: int) -> Optional[pd.Series]:
    symbol = _format_ticker(ticker, market)

    try:
        if market == "KR" and HAS_FDR:
            end = pd.Timestamp.now()
            start = end - pd.Timedelta(days=days + 15)
            data = fdr.DataReader(symbol.replace(".KS", "").replace(".KQ", ""), start, end)
        else:
            data = yf.download(symbol, period=f"{days}d", auto_adjust=True, progress=False)

        if data is None or data.empty or "Close" not in data.columns:
            return None

        close = data["Close"]
        if isinstance(close, pd.DataFrame):
            close = close.iloc[:, 0]

        close = close.dropna()
        if len(close) < 30:
            return None
        return close
    except Exception:
        return None


def _safe(value: float) -> float:
    if value is None or np.isnan(value) or np.isinf(value):
        return 0.0
    return float(value)


def _safe_or_none(value: float):
    if value is None or np.isnan(value) or np.isinf(value):
        return None
    return float(value)


def _compute_beta(portfolio_returns: pd.Series, market: str, lookback_days: int) -> float:
    market_symbol = "^KS11" if market == "KR" else "^GSPC"
    market_close = _fetch_close_series(market_symbol, "US", lookback_days)
    if market_close is None:
        return 0.0

    market_returns = market_close.pct_change().dropna()
    aligned_port, aligned_market = portfolio_returns.align(market_returns, join="inner")
    if len(aligned_port) < 10:
        return 0.0

    market_var = np.var(aligned_market)
    if market_var <= 0:
        return 0.0

    covariance = np.cov(aligned_port, aligned_market)[0, 1]
    return _safe(covariance / market_var)


@router.post("/portfolio/risk")
async def portfolio_risk(req: PortfolioRiskRequest):
    if not req.holdings:
        raise HTTPException(400, "Holdings are required")

    lookback = int(np.clip(req.lookbackDays, 60, 365))

    prices = {}
    input_weights = []
    total_value = 0.0
    dominant_market = "US"
    market_counts = {"US": 0, "KR": 0}

    for holding in req.holdings:
        close = _fetch_close_series(holding.ticker, holding.market, lookback)
        if close is not None:
            prices[holding.ticker.upper()] = close
            market_counts[holding.market] = market_counts.get(holding.market, 0) + 1

        value = max(float(holding.value), 0.0)
        total_value += value
        input_weights.append(value)

    if market_counts.get("KR", 0) > market_counts.get("US", 0):
        dominant_market = "KR"

    if len(prices) < 1:
        raise HTTPException(400, "Unable to load price history for holdings")

    df = pd.DataFrame(prices).sort_index().ffill().dropna(how="all")
    returns = df.pct_change().dropna(how="all")
    if returns.empty or len(returns) < 20:
        raise HTTPException(400, "Insufficient return history for risk analysis")

    # Build weights aligned to available return columns.
    aligned_weights = []
    for holding in req.holdings:
        t = holding.ticker.upper()
        if t in returns.columns:
            if total_value > 0:
                aligned_weights.append(max(float(holding.value), 0.0) / total_value)
            else:
                aligned_weights.append(1.0 / len(req.holdings))

    weight_sum = float(np.sum(aligned_weights))
    if weight_sum <= 0:
        aligned_weights = [1.0 / len(returns.columns)] * len(returns.columns)
    else:
        aligned_weights = [w / weight_sum for w in aligned_weights]

    portfolio_returns = returns.dot(np.array(aligned_weights))

    vol_pct = _safe(portfolio_returns.std() * np.sqrt(252) * 100)
    cumulative = (1 + portfolio_returns).cumprod()
    drawdown = cumulative / cumulative.cummax() - 1
    max_dd_pct = _safe(abs(drawdown.min()) * 100)

    var_q = portfolio_returns.quantile(0.05)
    var_95_pct = _safe(-var_q * 100)
    tail = portfolio_returns[portfolio_returns <= var_q]
    es_95_pct = _safe((-tail.mean() * 100) if len(tail) else var_95_pct)

    sharpe = 0.0
    if portfolio_returns.std() > 0:
        sharpe = _safe((portfolio_returns.mean() / portfolio_returns.std()) * np.sqrt(252))

    beta = _compute_beta(portfolio_returns, dominant_market, lookback)

    rolling_window = max(5, min(20, len(portfolio_returns)))
    rolling_vol = portfolio_returns.rolling(window=rolling_window).std() * np.sqrt(252) * 100

    time_series = []
    for idx, r in portfolio_returns.items():
        ts = pd.Timestamp(idx)
        time_series.append({
            "date": ts.date().isoformat(),
            "portfolioIndex": _safe_or_none(cumulative.loc[idx] * 100),
            "drawdownPct": _safe_or_none(drawdown.loc[idx] * 100),
            "rollingVolatilityPct": _safe_or_none(rolling_vol.loc[idx]),
        })

    # Diversification score (0-100): lower average abs correlation => higher score
    if returns.shape[1] <= 1:
        diversification = 0.0
    else:
        corr = returns.corr().abs()
        mask = ~np.eye(corr.shape[0], dtype=bool)
        avg_abs_corr = corr.where(mask).stack().mean()
        diversification = _safe((1 - avg_abs_corr) * 100)
        diversification = float(np.clip(diversification, 0, 100))

    holding_weights_pct = []
    for h in req.holdings:
        pct = (h.value / total_value * 100) if total_value > 0 else 0.0
        holding_weights_pct.append({
            "ticker": h.ticker.upper(),
            "market": h.market,
            "weightPct": _safe(pct),
            "value": _safe(h.value),
        })

    holding_weights_pct.sort(key=lambda x: x["weightPct"], reverse=True)
    concentration = _safe(holding_weights_pct[0]["weightPct"] if holding_weights_pct else 0.0)

    risk_level = "LOW"
    if vol_pct >= 35 or var_95_pct >= 4:
        risk_level = "HIGH"
    elif vol_pct >= 20 or var_95_pct >= 2:
        risk_level = "MEDIUM"

    return {
        "generatedAt": datetime.utcnow().isoformat(),
        "riskLevel": risk_level,
        "annualizedVolatilityPct": vol_pct,
        "maxDrawdownPct": max_dd_pct,
        "valueAtRisk95Pct": var_95_pct,
        "expectedShortfall95Pct": es_95_pct,
        "sharpeRatio": sharpe,
        "betaToMarket": beta,
        "diversificationScore": diversification,
        "concentrationScore": concentration,
        "holdings": holding_weights_pct,
        "timeSeries": time_series,
    }
