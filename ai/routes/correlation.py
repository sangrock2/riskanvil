"""Correlation analysis endpoint"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
import yfinance as yf
import pandas as pd
import numpy as np

# Optional Korean market data support
try:
    import FinanceDataReader as fdr
    HAS_FDR = True
except ImportError:
    HAS_FDR = False
    fdr = None

router = APIRouter()


class CorrelationRequest(BaseModel):
    tickers: list[str]
    market: str = "US"
    days: int = 90


@router.post("/correlation")
async def correlation_analysis(req: CorrelationRequest):
    """Analyze correlation between stocks"""

    # Download price data
    prices = {}
    for ticker in req.tickers:
        try:
            if req.market == "US":
                data = yf.download(ticker, period=f"{req.days}d", progress=False)
            else:
                # For KR market, use FinanceDataReader if available
                if not HAS_FDR:
                    raise HTTPException(500, "FinanceDataReader not available for Korean market data")
                end = pd.Timestamp.now()
                start = end - pd.Timedelta(days=req.days)
                data = fdr.DataReader(ticker, start, end)

            if data is not None and not data.empty and 'Close' in data.columns:
                # Extract Close prices as Series (handle MultiIndex columns from yfinance)
                close_data = data['Close']
                if isinstance(close_data, pd.DataFrame):
                    # If it's a DataFrame (single ticker with MultiIndex), get the first column
                    close_data = close_data.iloc[:, 0]
                prices[ticker] = close_data
        except Exception:
            continue

    # Validate we have enough data
    if len(prices) < 2:
        raise HTTPException(400, "Need at least 2 tickers with valid price data")

    # Create DataFrame
    df = pd.DataFrame(prices)

    # Calculate correlation matrix
    corr_matrix = df.corr()

    # Download market index data for beta calculation
    market_index_ticker = "^GSPC" if req.market == "US" else "^KS11"  # S&P 500 or KOSPI
    try:
        market_data = yf.download(market_index_ticker, period=f"{req.days}d", progress=False)
        if market_data is not None and not market_data.empty and 'Close' in market_data.columns:
            market_close = market_data['Close']
            if isinstance(market_close, pd.DataFrame):
                market_close = market_close.iloc[:, 0]
            market_returns = market_close.pct_change().dropna()
        else:
            market_returns = None
    except:
        market_returns = None

    # Calculate stats for each ticker
    stats = {}
    for ticker in req.tickers:
        if ticker in df.columns:
            returns = df[ticker].pct_change().dropna()

            # Handle empty or invalid returns
            if len(returns) == 0:
                stats[ticker] = {
                    "ticker": ticker,
                    "mean": 0.0,
                    "stdDev": 0.0,
                    "sharpe": 0.0,
                    "beta": 1.0
                }
                continue

            mean_return = float(returns.mean()) if not np.isnan(returns.mean()) else 0.0
            std_dev = float(returns.std()) if not np.isnan(returns.std()) else 0.0

            # Sharpe ratio (annualized, assuming 252 trading days)
            if std_dev > 0 and not np.isnan(mean_return) and not np.isnan(std_dev):
                sharpe = float(mean_return / std_dev * np.sqrt(252))
            else:
                sharpe = 0.0

            # Calculate Beta vs market index
            beta = 1.0
            if market_returns is not None and len(market_returns) > 0:
                # Align returns dates
                aligned_returns = returns.align(market_returns, join='inner')[0]
                aligned_market = returns.align(market_returns, join='inner')[1]

                if len(aligned_returns) > 1:
                    # Beta = Cov(stock, market) / Var(market)
                    covariance = np.cov(aligned_returns, aligned_market)[0, 1]
                    market_variance = np.var(aligned_market)

                    if market_variance > 0 and not np.isnan(covariance) and not np.isnan(market_variance):
                        beta = float(covariance / market_variance)
                        if np.isnan(beta) or np.isinf(beta):
                            beta = 1.0

            stats[ticker] = {
                "ticker": ticker,
                "mean": mean_return,
                "stdDev": std_dev,
                "sharpe": sharpe if not np.isnan(sharpe) else 0.0,
                "beta": beta
            }

    return {
        "tickers": req.tickers,
        "correlationMatrix": corr_matrix.fillna(0).values.tolist(),
        "stats": stats
    }
