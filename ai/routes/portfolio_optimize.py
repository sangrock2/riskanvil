"""
효율적 프론티어 (Efficient Frontier) 엔드포인트
- scipy.optimize 기반 최소분산 / 최대샤프 포트폴리오 계산
- 몬테카를로 랜덤 포트폴리오로 프론티어 시각화 데이터 제공
"""
import logging
from typing import Optional

import numpy as np
import yfinance as yf
import pandas as pd
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from scipy.optimize import minimize

router = APIRouter(prefix="/portfolio", tags=["Portfolio Optimize"])
logger = logging.getLogger("app")

_RISK_FREE_RATE = 0.05  # 연 5% (미국 단기국채 기준)
_TRADING_DAYS = 252


class EfficientFrontierRequest(BaseModel):
    tickers: list[str]           # 포트폴리오 구성 종목 (최소 2개, 최대 20개)
    start: Optional[str] = None  # 수익률 계산 시작일 (YYYY-MM-DD)
    end: Optional[str] = None
    nSimulations: int = 3000     # 몬테카를로 시뮬레이션 횟수


class EfficientFrontierResponse(BaseModel):
    tickers: list[str]
    minVarianceWeights: dict      # 최소분산 포트폴리오 비중
    maxSharpeWeights: dict        # 최대샤프 포트폴리오 비중
    minVarianceStats: dict        # 연수익률, 연변동성, 샤프비율
    maxSharpeStats: dict
    frontier: list[dict]          # 몬테카를로 포인트 [{ret, vol, sharpe, weights}]


def _fetch_returns(tickers: list[str], start: Optional[str], end: Optional[str]) -> pd.DataFrame:
    raw = yf.download(tickers, start=start, end=end, auto_adjust=True, progress=False)
    if raw is None or raw.empty:
        raise HTTPException(400, "가격 데이터를 가져올 수 없습니다.")

    # MultiIndex → Close 컬럼만 추출
    if isinstance(raw.columns, pd.MultiIndex):
        close = raw["Close"] if "Close" in raw.columns.get_level_values(0) else raw.iloc[:, :len(tickers)]
    else:
        close = raw[["Close"]] if "Close" in raw.columns else raw

    close = close.dropna(how="all")
    returns = close.pct_change().dropna()

    if returns.shape[0] < 60:
        raise HTTPException(400, f"수익률 계산에 필요한 데이터가 부족합니다 (최소 60거래일 필요, 현재 {returns.shape[0]}일).")

    return returns


def _portfolio_stats(weights: np.ndarray, mean_ret: np.ndarray, cov: np.ndarray) -> tuple[float, float, float]:
    ann_ret = float(np.dot(weights, mean_ret) * _TRADING_DAYS)
    ann_vol = float(np.sqrt(weights @ cov @ weights) * np.sqrt(_TRADING_DAYS))
    sharpe = (ann_ret - _RISK_FREE_RATE) / ann_vol if ann_vol > 0 else 0.0
    return ann_ret, ann_vol, sharpe


def _optimize(mean_ret: np.ndarray, cov: np.ndarray, objective: str) -> np.ndarray:
    n = len(mean_ret)
    init = np.ones(n) / n
    bounds = [(0.0, 1.0)] * n
    constraints = [{"type": "eq", "fun": lambda w: np.sum(w) - 1.0}]

    if objective == "min_variance":
        def neg_fn(w):
            return float(w @ cov @ w)
    else:  # max_sharpe
        def neg_fn(w):
            ret, vol, _ = _portfolio_stats(w, mean_ret, cov)
            return -((ret - _RISK_FREE_RATE) / vol) if vol > 0 else 0.0

    result = minimize(neg_fn, init, method="SLSQP", bounds=bounds, constraints=constraints,
                      options={"ftol": 1e-9, "maxiter": 1000})
    if not result.success:
        logger.warning("최적화 수렴 실패: %s", result.message)
    return result.x


@router.post("/efficient-frontier", response_model=EfficientFrontierResponse)
def efficient_frontier(req: EfficientFrontierRequest):
    """효율적 프론티어 계산 — 최소분산 / 최대샤프 포트폴리오 + 몬테카를로 시뮬레이션"""
    tickers = [t.strip().upper() for t in req.tickers]
    if len(tickers) < 2:
        raise HTTPException(400, "최소 2개 이상의 종목이 필요합니다.")
    if len(tickers) > 20:
        raise HTTPException(400, "최대 20개까지 지원합니다.")

    n_sim = max(100, min(req.nSimulations, 10000))

    returns = _fetch_returns(tickers, req.start, req.end)

    # 실제로 수집된 티커만 사용 (yfinance가 일부 종목을 누락할 수 있음)
    available = [t for t in tickers if t in returns.columns]
    if len(available) < 2:
        raise HTTPException(400, f"유효한 가격 데이터가 있는 종목이 부족합니다: {available}")
    returns = returns[available]

    mean_ret = returns.mean().values
    cov = returns.cov().values * _TRADING_DAYS  # 연환산 공분산

    # ── 최적화 ──
    mv_weights = _optimize(mean_ret, cov / _TRADING_DAYS, "min_variance")
    ms_weights = _optimize(mean_ret, cov / _TRADING_DAYS, "max_sharpe")

    mv_ret, mv_vol, mv_sharpe = _portfolio_stats(mv_weights, mean_ret, cov / _TRADING_DAYS)
    ms_ret, ms_vol, ms_sharpe = _portfolio_stats(ms_weights, mean_ret, cov / _TRADING_DAYS)

    # ── 몬테카를로 시뮬레이션 ──
    rng = np.random.default_rng(42)
    frontier_points = []
    for _ in range(n_sim):
        w = rng.dirichlet(np.ones(len(available)))
        r, v, s = _portfolio_stats(w, mean_ret, cov / _TRADING_DAYS)
        frontier_points.append({
            "ret": round(r, 6),
            "vol": round(v, 6),
            "sharpe": round(s, 4),
            "weights": {t: round(float(wt), 4) for t, wt in zip(available, w)},
        })

    def _fmt_weights(w: np.ndarray) -> dict:
        return {t: round(float(wt), 4) for t, wt in zip(available, w)}

    return EfficientFrontierResponse(
        tickers=available,
        minVarianceWeights=_fmt_weights(mv_weights),
        maxSharpeWeights=_fmt_weights(ms_weights),
        minVarianceStats={"annReturn": round(mv_ret, 6), "annVol": round(mv_vol, 6), "sharpe": round(mv_sharpe, 4)},
        maxSharpeStats={"annReturn": round(ms_ret, 6), "annVol": round(ms_vol, 6), "sharpe": round(ms_sharpe, 4)},
        frontier=frontier_points,
    )
