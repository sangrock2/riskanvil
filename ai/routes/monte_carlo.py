"""Monte Carlo simulation endpoint — GBM, Jump-Diffusion, Historical Bootstrap"""
from typing import Optional
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
import yfinance as yf
import numpy as np
import pandas as pd
from scipy import stats as sp_stats

# Optional Korean market data support
try:
    import FinanceDataReader as fdr
    HAS_FDR = True
except ImportError:
    HAS_FDR = False
    fdr = None

router = APIRouter()


# ── Request / Response models ──

class MonteCarloRequest(BaseModel):
    ticker: str
    market: str = "US"
    days: int = 90
    simulations: int = 1000
    forecastDays: int = 30
    model: str = "gbm"                # "gbm" | "jump_diffusion" | "historical_bootstrap"
    scenarios: bool = False            # bull/bear/base scenario analysis
    confidenceBands: bool = True       # daily confidence bands


# ── Helpers ──

def safe_float(value):
    """Safely convert numpy scalar to Python float."""
    if np.isnan(value) or np.isinf(value):
        return 0.0
    return float(value)


def _fetch_prices(ticker: str, market: str, days: int) -> np.ndarray:
    """Download close prices, return 1-d numpy array."""
    if market == "US":
        data = yf.download(ticker, period=f"{days}d", progress=False)
    else:
        if not HAS_FDR:
            raise HTTPException(500, "FinanceDataReader not available for Korean market data")
        end = pd.Timestamp.now()
        start = end - pd.Timedelta(days=days)
        data = fdr.DataReader(ticker, start, end)

    if data is None or data.empty or "Close" not in data.columns:
        raise HTTPException(400, f"No price data found for {ticker}")

    close_data = data["Close"]
    if isinstance(close_data, pd.DataFrame):
        close_data = close_data.iloc[:, 0]

    prices = close_data.dropna().values.astype(float)

    if len(prices) < 30:
        raise HTTPException(400, f"Insufficient price data for {ticker}. Need at least 30 days.")

    return prices


def calibrate_jump_params(log_returns: np.ndarray):
    """Estimate Merton jump-diffusion parameters from historical log returns.

    Uses a simple threshold approach: returns beyond ±2σ are classified as jumps.
    """
    mu_r = np.mean(log_returns)
    sigma_r = np.std(log_returns)

    threshold = 2.0 * sigma_r
    jump_mask = np.abs(log_returns - mu_r) > threshold
    normal_mask = ~jump_mask

    # Jump frequency (annualized)
    jump_count = int(np.sum(jump_mask))
    total_days = len(log_returns)
    lam = max(jump_count / total_days * 252, 0.5)  # floor at 0.5 per year

    # Jump size distribution
    if jump_count >= 2:
        jump_returns = log_returns[jump_mask]
        jump_mu = float(np.mean(jump_returns))
        jump_sigma = float(np.std(jump_returns))
    else:
        jump_mu = -0.02
        jump_sigma = 0.05

    # Diffusion component (non-jump returns)
    if np.sum(normal_mask) > 10:
        diff_mu = float(np.mean(log_returns[normal_mask]))
        diff_sigma = float(np.std(log_returns[normal_mask]))
    else:
        diff_mu = mu_r
        diff_sigma = sigma_r

    return {
        "lambda": round(lam, 4),
        "mu": round(jump_mu, 6),
        "sigma": round(jump_sigma, 6),
    }, diff_mu, diff_sigma


# ── Simulation engines (vectorized) ──

def simulate_gbm(last_price: float, mu: float, sigma: float,
                 n_sims: int, n_days: int) -> np.ndarray:
    """Vectorized GBM.  Returns (n_sims, n_days+1) price matrix."""
    dt = 1.0
    drift = (mu - 0.5 * sigma ** 2) * dt
    shock = sigma * np.sqrt(dt) * np.random.standard_normal((n_sims, n_days))
    log_increments = drift + shock
    log_paths = np.concatenate(
        [np.zeros((n_sims, 1)), np.cumsum(log_increments, axis=1)], axis=1
    )
    return last_price * np.exp(log_paths)


def simulate_jump_diffusion(last_price: float, diff_mu: float, diff_sigma: float,
                            jump_lam: float, jump_mu: float, jump_sigma: float,
                            n_sims: int, n_days: int) -> np.ndarray:
    """Merton jump-diffusion (vectorized)."""
    dt = 1.0
    # diffusion
    drift = (diff_mu - 0.5 * diff_sigma ** 2) * dt
    diffusion = diff_sigma * np.sqrt(dt) * np.random.standard_normal((n_sims, n_days))

    # jumps — Poisson number of jumps per day
    lam_daily = jump_lam / 252.0
    n_jumps = np.random.poisson(lam_daily, (n_sims, n_days))
    # total jump size: sum of n_jumps normal draws ≈ N(n*mu, n*sigma²)
    jump_sizes = n_jumps * jump_mu + np.sqrt(np.maximum(n_jumps, 0)) * jump_sigma * np.random.standard_normal((n_sims, n_days))

    log_increments = drift + diffusion + jump_sizes
    log_paths = np.concatenate(
        [np.zeros((n_sims, 1)), np.cumsum(log_increments, axis=1)], axis=1
    )
    return last_price * np.exp(log_paths)


def simulate_bootstrap(last_price: float, log_returns: np.ndarray,
                       n_sims: int, n_days: int) -> np.ndarray:
    """Historical bootstrap: resample actual daily log returns."""
    indices = np.random.randint(0, len(log_returns), (n_sims, n_days))
    sampled = log_returns[indices]
    log_paths = np.concatenate(
        [np.zeros((n_sims, 1)), np.cumsum(sampled, axis=1)], axis=1
    )
    return last_price * np.exp(log_paths)


# ── Statistics helpers ──

def _compute_stats(price_matrix: np.ndarray, last_price: float) -> dict:
    """Compute statistics from (n_sims, n_days+1) price matrix."""
    final_prices = price_matrix[:, -1]
    returns_pct = (final_prices / last_price - 1) * 100

    mean_final = np.mean(final_prices)
    expected_return = safe_float(mean_final - last_price)
    expected_return_pct = safe_float((mean_final / last_price - 1) * 100)

    # VaR / CVaR
    p5 = np.percentile(final_prices, 5)
    var_95 = safe_float(last_price - p5)
    worst_5 = final_prices[final_prices <= p5]
    cvar_95 = safe_float(last_price - np.mean(worst_5)) if len(worst_5) > 0 else var_95

    # Max drawdown per path
    running_max = np.maximum.accumulate(price_matrix, axis=1)
    drawdowns = (price_matrix - running_max) / running_max
    max_dd_per_path = np.min(drawdowns, axis=1)
    avg_max_dd = safe_float(np.mean(np.abs(max_dd_per_path)) * 100)

    # Volatility: annualized σ from daily log returns of all paths
    log_rets = np.diff(np.log(price_matrix), axis=1)
    vol = safe_float(np.std(log_rets) * np.sqrt(252) * 100)

    # Additional stats
    skew = safe_float(float(sp_stats.skew(returns_pct)))
    kurt = safe_float(float(sp_stats.kurtosis(returns_pct)))
    prob_profit = safe_float(float(np.mean(final_prices > last_price) * 100))

    return {
        "expectedReturn": expected_return,
        "expectedReturnPercent": expected_return_pct,
        "volatility": vol,
        "maxDrawdown": avg_max_dd,
        "valueAtRisk95": var_95,
        "conditionalVaR95": cvar_95,
        "skewness": skew,
        "kurtosis": kurt,
        "probabilityProfit": prob_profit,
    }


def _compute_confidence_bands(price_matrix: np.ndarray) -> dict:
    """Compute daily percentile bands from (n_sims, n_days+1) matrix."""
    return {
        "p5": [safe_float(v) for v in np.percentile(price_matrix, 5, axis=0)],
        "p25": [safe_float(v) for v in np.percentile(price_matrix, 25, axis=0)],
        "p50": [safe_float(v) for v in np.percentile(price_matrix, 50, axis=0)],
        "p75": [safe_float(v) for v in np.percentile(price_matrix, 75, axis=0)],
        "p95": [safe_float(v) for v in np.percentile(price_matrix, 95, axis=0)],
    }


def _compute_scenarios(price_matrix: np.ndarray, last_price: float) -> dict:
    """Bull / Base / Bear scenario paths + summary stats."""
    final_prices = price_matrix[:, -1]
    scenarios = {}

    for label, pct in [("bull", 90), ("base", 50), ("bear", 10)]:
        target = np.percentile(final_prices, pct)
        # pick the path whose final price is closest to the target percentile
        idx = int(np.argmin(np.abs(final_prices - target)))
        path = price_matrix[idx]
        ret = (path[-1] / last_price - 1) * 100

        running_max = np.maximum.accumulate(path)
        dd = (path - running_max) / running_max
        max_dd = safe_float(np.min(dd) * 100)

        scenarios[label] = {
            "finalPrice": safe_float(path[-1]),
            "returnPercent": safe_float(ret),
            "maxDrawdown": max_dd,
            "path": [safe_float(v) for v in path],
        }

    return scenarios


# ── Main endpoint ──

@router.post("/monte-carlo")
async def monte_carlo_simulation(req: MonteCarloRequest):
    """Run Monte Carlo price simulation (GBM / Jump-Diffusion / Bootstrap)."""

    prices = _fetch_prices(req.ticker, req.market, req.days)
    log_returns = np.diff(np.log(prices))

    mu = float(np.mean(log_returns))
    sigma = float(np.std(log_returns))
    last_price = float(prices[-1])

    if np.isnan(mu) or np.isnan(sigma):
        raise HTTPException(400, f"Unable to calculate valid statistics for {req.ticker}")

    model = req.model.lower()
    jump_params: Optional[dict] = None

    # ── Run simulations ──
    if model == "jump_diffusion":
        jp, diff_mu, diff_sigma = calibrate_jump_params(log_returns)
        jump_params = jp
        price_matrix = simulate_jump_diffusion(
            last_price, diff_mu, diff_sigma,
            jp["lambda"], jp["mu"], jp["sigma"],
            req.simulations, req.forecastDays,
        )
    elif model == "historical_bootstrap":
        price_matrix = simulate_bootstrap(last_price, log_returns, req.simulations, req.forecastDays)
    else:  # default: gbm
        model = "gbm"
        price_matrix = simulate_gbm(last_price, mu, sigma, req.simulations, req.forecastDays)

    # ── Build paths for response (limit to 100) ──
    max_paths = min(100, req.simulations)
    simulation_paths = [
        {"pathId": i, "prices": [safe_float(v) for v in price_matrix[i]]}
        for i in range(max_paths)
    ]

    # ── Distribution (final prices) ──
    final_prices = price_matrix[:, -1]
    distribution = {
        "percentile5": safe_float(np.percentile(final_prices, 5)),
        "percentile25": safe_float(np.percentile(final_prices, 25)),
        "median": safe_float(np.median(final_prices)),
        "percentile75": safe_float(np.percentile(final_prices, 75)),
        "percentile95": safe_float(np.percentile(final_prices, 95)),
    }

    # ── Stats ──
    stats = _compute_stats(price_matrix, last_price)

    # ── Build response ──
    result = {
        "ticker": req.ticker,
        "model": model,
        "paths": simulation_paths,
        "distribution": distribution,
        "stats": stats,
    }

    # Optional: confidence bands
    if req.confidenceBands:
        result["confidenceBands"] = _compute_confidence_bands(price_matrix)

    # Optional: scenario analysis
    if req.scenarios:
        result["scenarios"] = _compute_scenarios(price_matrix, last_price)

    # Optional: jump params
    if jump_params:
        result["jumpParams"] = jump_params

    return result
