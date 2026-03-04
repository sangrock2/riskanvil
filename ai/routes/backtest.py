"""
백테스트 라우트 - SMA_CROSS / RSI_REVERSAL / MACD_CROSS / BOLLINGER_BAND
미국(US), 한국(KR), 암호화폐(CRYPTO) 지원
"""
import logging
from typing import Optional, Literal

import pandas as pd
import pandas_ta as ta
import numpy as np
import yfinance as yf

try:
    import FinanceDataReader as fdr
    HAS_FDR = True
except ImportError:
    HAS_FDR = False
    fdr = None

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from itertools import product as iterproduct

logger = logging.getLogger("app")

router = APIRouter()


# ── Models ──

Market = Literal["US", "KR", "CRYPTO"]
Strategy = Literal["SMA_CROSS", "RSI_REVERSAL", "MACD_CROSS", "BOLLINGER_BAND"]


class BacktestRequest(BaseModel):
    """단일 전략 백테스트 입력 모델."""
    ticker: str
    market: Market
    strategy: Strategy = "SMA_CROSS"
    start: Optional[str] = None
    end: Optional[str] = None
    initialCapital: float = 1_000_000
    feeBps: float = 5.0
    maxPoints: int = 600


class AnalyzeReq(BaseModel):
    """`/analyze` 호환 요청 모델 (insights 래핑용)."""
    ticker: str
    market: str | None = None
    horizonDays: int | None = 252
    riskProfile: str | None = "balanced"


class GridSearchRequest(BaseModel):
    """전략 파라미터 그리드 서치 입력 모델."""
    ticker: str
    market: Market
    strategy: Strategy = "SMA_CROSS"
    start: Optional[str] = None
    end: Optional[str] = None
    initialCapital: float = 1_000_000
    feeBps: float = 5.0
    # SMA_CROSS 파라미터 범위
    fastPeriods: list[int] = [10, 15, 20, 25, 30]
    slowPeriods: list[int] = [40, 50, 60, 80, 100]
    # RSI_REVERSAL 파라미터 범위
    rsiPeriods: list[int] = [10, 14, 20]
    oversoldThresholds: list[int] = [25, 30, 35]
    overboughtThresholds: list[int] = [65, 70, 75]
    # BOLLINGER_BAND 파라미터 범위
    bbPeriods: list[int] = [15, 20, 25]
    bbStds: list[float] = [1.5, 2.0, 2.5]


# ── Helpers ──

def _normalize_columns(df: pd.DataFrame, ticker: str) -> pd.DataFrame:
    """데이터 공급원별 컬럼 구조 차이를 `close` 중심 단일 스키마로 정규화한다."""
    if isinstance(df.columns, pd.MultiIndex):
        lvl0 = df.columns.get_level_values(0)
        if any(str(x).upper() == ticker.upper() for x in lvl0):
            df = df[ticker]
        else:
            df.columns = df.columns.get_level_values(0)
    elif len(df.columns) > 0 and isinstance(df.columns[0], tuple):
        df.columns = [c[0] for c in df.columns]
    df.columns = [str(c).strip().lower().replace(" ", "_") for c in df.columns]
    return df


def _load_prices(ticker: str, market: Market, start: Optional[str], end: Optional[str]) -> pd.DataFrame:
    """시장 구분(US/KR/CRYPTO)에 맞춰 가격을 조회하고 `dt`, `close`만 반환한다."""
    if market == "CRYPTO":
        t = ticker.upper()
        if not t.endswith("-USD") and not t.endswith("-USDT"):
            t = f"{t}-USD"
        df = yf.download(t, start=start, end=end, auto_adjust=True, progress=False)
        if df is None or df.empty:
            raise HTTPException(400, f"Crypto price data not found for {t}")
        df = df.rename(columns=str.lower).reset_index()
        df = _normalize_columns(df, t)
        df.rename(columns={"date": "dt"}, inplace=True)

    elif market == "US":
        df = yf.download(ticker, start=start, end=end, auto_adjust=True, progress=False)
        if df is None or df.empty:
            raise HTTPException(400, "price data not found")
        df = df.rename(columns=str.lower).reset_index()
        df = _normalize_columns(df, ticker)
        df.rename(columns={"date": "dt"}, inplace=True)

    else:  # KR
        if not HAS_FDR:
            raise HTTPException(500, "FinanceDataReader not available for Korean market data")
        df = fdr.DataReader(ticker, start, end)
        if df is None or df.empty:
            raise HTTPException(400, "price data not found")
        df = df.reset_index()
        df.columns = [c.lower() for c in df.columns]
        df.rename(columns={"date": "dt"}, inplace=True)

    if "close" not in df.columns:
        raise HTTPException(500, "close column missing")

    df["dt"] = pd.to_datetime(df["dt"])
    df = df.sort_values("dt")
    df = df[["dt", "close"]].dropna()
    return df


def _max_drawdown(equity: pd.Series) -> float:
    """자산곡선에서 최대 낙폭(MDD)을 계산한다."""
    peak = equity.cummax()
    dd = equity / peak - 1.0
    return float(dd.min())


def _annualized_return(equity: pd.Series, dates: pd.Series) -> float:
    """기간 전체 수익률을 연환산 수익률(CAGR)로 변환한다."""
    if len(equity) < 2:
        return 0.0
    days = (dates.iloc[-1] - dates.iloc[0]).days
    if days <= 0:
        return 0.0
    total = equity.iloc[-1] / equity.iloc[0]
    years = days / 365.25
    return float(total ** (1 / years) - 1)


def _sharpe(daily_ret: pd.Series) -> float:
    """일간 수익률 기준 단순 Sharpe ratio(무위험수익률 0 가정)를 계산한다."""
    r = daily_ret.dropna()
    if len(r) < 20:
        return 0.0
    mu = r.mean()
    sd = r.std(ddof=1)
    if sd == 0 or np.isnan(sd):
        return 0.0
    return float((mu / sd) * np.sqrt(252))


def _downsample_equity(df: pd.DataFrame, max_points: int) -> list[dict]:
    """차트 응답 크기를 제한하기 위해 균등 간격 샘플링을 적용한다."""
    if len(df) <= max_points:
        return [{"date": d.strftime("%Y-%m-%d"), "equity": float(v)} for d, v in zip(df["dt"], df["equity"])]
    step = max(1, len(df) // max_points)
    sampled = df.iloc[::step].copy()
    return [{"date": d.strftime("%Y-%m-%d"), "equity": float(v)} for d, v in zip(sampled["dt"], sampled["equity"])]


# ── Strategy Functions ──

def _apply_sma_cross(df: pd.DataFrame, close: pd.Series) -> pd.DataFrame:
    """골든크로스/데드크로스 (SMA 20 vs SMA 60)"""
    sma_fast = pd.to_numeric(ta.sma(close, length=20), errors="coerce")
    sma_slow = pd.to_numeric(ta.sma(close, length=60), errors="coerce")
    signal = (sma_fast > sma_slow).fillna(False).astype(int)
    df["pos"] = signal.shift(1).fillna(0)
    df["strategy_note"] = "SMA Cross (20/60)"
    return df


def _apply_rsi_reversal(df: pd.DataFrame, close: pd.Series) -> pd.DataFrame:
    """RSI 역추세: RSI < 30 매수, RSI > 70 청산"""
    rsi = pd.to_numeric(ta.rsi(close, length=14), errors="coerce").fillna(50)
    pos_arr = []
    current = 0
    for r in rsi:
        if r < 30:
            current = 1
        elif r > 70:
            current = 0
        pos_arr.append(current)
    df["pos"] = pd.Series(pos_arr, index=df.index).shift(1).fillna(0)
    df["strategy_note"] = "RSI Reversal (buy RSI<30, sell RSI>70)"
    return df


def _apply_macd_cross(df: pd.DataFrame, close: pd.Series) -> pd.DataFrame:
    """MACD 히스토그램 전환: 양전환 매수, 음전환 청산 (12/26/9)"""
    macd_df = ta.macd(close, fast=12, slow=26, signal=9)
    if macd_df is None or macd_df.empty:
        df["pos"] = 0.0
        df["strategy_note"] = "MACD Cross (insufficient data)"
        return df
    hist_col = next((c for c in macd_df.columns if "MACDh" in str(c)), None)
    hist = pd.to_numeric(
        macd_df[hist_col] if hist_col else macd_df.iloc[:, 1],
        errors="coerce"
    ).fillna(0)
    df["pos"] = (hist > 0).astype(int).shift(1).fillna(0)
    df["strategy_note"] = "MACD Histogram Cross (12/26/9)"
    return df


def _apply_bollinger_band(df: pd.DataFrame, close: pd.Series) -> pd.DataFrame:
    """볼린저 밴드 평균회귀: 하단 터치 매수, 상단 터치 청산 (20일/2σ)"""
    bb = ta.bbands(close, length=20, std=2.0)
    if bb is None or bb.empty:
        df["pos"] = 1.0
        df["strategy_note"] = "Bollinger Band (insufficient data)"
        return df
    lower_col = next((c for c in bb.columns if str(c).startswith("BBL")), None)
    upper_col = next((c for c in bb.columns if str(c).startswith("BBU")), None)
    lower = pd.to_numeric(bb[lower_col] if lower_col else bb.iloc[:, 0], errors="coerce")
    upper = pd.to_numeric(bb[upper_col] if upper_col else bb.iloc[:, 2], errors="coerce")

    pos_arr = []
    current = 0
    for c_val, lo, up in zip(df["close"].astype(float), lower.fillna(0), upper.fillna(1e12)):
        if c_val <= lo:
            current = 1
        elif c_val >= up:
            current = 0
        pos_arr.append(current)
    df["pos"] = pd.Series(pos_arr, index=df.index).shift(1).fillna(0)
    df["strategy_note"] = "Bollinger Band Mean Reversion (20/2σ)"
    return df


_STRATEGY_DISPATCH = {
    # 전략 문자열을 실제 시그널 생성 함수로 매핑해 공통 실행 경로를 유지한다.
    "SMA_CROSS":      _apply_sma_cross,
    "RSI_REVERSAL":   _apply_rsi_reversal,
    "MACD_CROSS":     _apply_macd_cross,
    "BOLLINGER_BAND": _apply_bollinger_band,
}


# ── Endpoints ──

def _calc_equity(df: pd.DataFrame, initial_capital: float, fee_bps: float) -> pd.DataFrame:
    """포지션/수익률 컬럼으로 자산 곡선 계산 (Grid Search 공통 사용)"""
    df["trade"] = (df["pos"].diff().abs().fillna(0) > 0).astype(int)
    fee_rate = fee_bps / 10000.0
    df["str_ret"] = df["pos"] * df["ret"] - df["trade"] * fee_rate
    df["equity"] = initial_capital * (1.0 + df["str_ret"]).cumprod()
    return df


def _run_strategy(df: pd.DataFrame, close: pd.Series, strategy: Strategy,
                  initial_capital: float, fee_bps: float) -> pd.DataFrame:
    """전략 적용 + 자산 곡선 계산 공통 함수"""
    apply_fn = _STRATEGY_DISPATCH[strategy]
    df = apply_fn(df.copy(), close)
    df["trade"] = (df["pos"].diff().abs().fillna(0) > 0).astype(int)
    fee_rate = fee_bps / 10000.0
    df["str_ret"] = df["pos"] * df["ret"] - df["trade"] * fee_rate
    df["equity"] = initial_capital * (1.0 + df["str_ret"]).cumprod()
    return df


@router.post("/backtest")
def backtest(req: BacktestRequest):
    """백테스트 실행 - Buy & Hold 벤치마크 포함"""
    df = _load_prices(req.ticker.strip(), req.market, req.start, req.end)
    close = df["close"].astype(float)
    df["ret"] = close.pct_change().fillna(0.0)

    apply_fn = _STRATEGY_DISPATCH.get(req.strategy)
    if apply_fn is None:
        raise HTTPException(400, f"Unknown strategy: {req.strategy}")

    df = _run_strategy(df, close, req.strategy, req.initialCapital, req.feeBps)

    total_return = float(df["equity"].iloc[-1] / df["equity"].iloc[0] - 1.0)
    mdd = _max_drawdown(df["equity"])
    cagr = _annualized_return(df["equity"], df["dt"])
    sharpe = _sharpe(df["str_ret"])
    num_trades = int(df["trade"].sum())
    note = df["strategy_note"].iloc[0] if "strategy_note" in df.columns else None

    # ── Buy & Hold 벤치마크 ──
    bh_equity = req.initialCapital * (1.0 + df["ret"]).cumprod()
    bh_return = float(bh_equity.iloc[-1] / bh_equity.iloc[0] - 1.0)
    bh_mdd = _max_drawdown(bh_equity)
    bh_cagr = _annualized_return(bh_equity, df["dt"])
    bh_sharpe = _sharpe(df["ret"])
    df["bh_equity"] = bh_equity

    summary = {
        "ticker": req.ticker,
        "market": req.market,
        "strategy": req.strategy,
        "start": df["dt"].iloc[0].strftime("%Y-%m-%d"),
        "end": df["dt"].iloc[-1].strftime("%Y-%m-%d"),
        "initialCapital": req.initialCapital,
        "feeBps": req.feeBps,
        "totalReturn": total_return,
        "cagr": cagr,
        "maxDrawdown": mdd,
        "sharpe": sharpe,
        "numTrades": num_trades,
        "note": note,
        "benchmark": {
            "totalReturn": bh_return,
            "cagr": bh_cagr,
            "maxDrawdown": bh_mdd,
            "sharpe": bh_sharpe,
        },
    }

    max_pts = max(50, min(req.maxPoints, 2000))
    equity_curve = _downsample_equity(df[["dt", "equity"]], max_pts)

    # Buy & Hold 곡선도 같은 인덱스로 다운샘플
    bh_df = df[["dt"]].copy()
    bh_df["equity"] = df["bh_equity"]
    bh_curve = _downsample_equity(bh_df, max_pts)

    return {"summary": summary, "equityCurve": equity_curve, "benchmarkCurve": bh_curve}


@router.post("/backtest/optimize")
def optimize_backtest(req: GridSearchRequest):
    """Grid Search 파라미터 최적화 - 전략별 최적 파라미터 탐색"""
    df_raw = _load_prices(req.ticker.strip(), req.market, req.start, req.end)
    close = df_raw["close"].astype(float)

    results = []

    if req.strategy == "SMA_CROSS":
        for fast, slow in iterproduct(req.fastPeriods, req.slowPeriods):
            if fast >= slow:
                continue
            df = df_raw.copy()
            df["ret"] = close.pct_change().fillna(0.0)
            sma_f = pd.to_numeric(ta.sma(close, length=fast), errors="coerce")
            sma_s = pd.to_numeric(ta.sma(close, length=slow), errors="coerce")
            df["pos"] = (sma_f > sma_s).fillna(False).astype(int).shift(1).fillna(0)
            df = _calc_equity(df, req.initialCapital, req.feeBps)
            results.append({
                "params": {"fast": fast, "slow": slow},
                "totalReturn": float(df["equity"].iloc[-1] / df["equity"].iloc[0] - 1.0),
                "sharpe": _sharpe(df["str_ret"]),
                "maxDrawdown": _max_drawdown(df["equity"]),
                "numTrades": int(df["trade"].sum()),
            })

    elif req.strategy == "RSI_REVERSAL":
        for period, oversold, overbought in iterproduct(
            req.rsiPeriods, req.oversoldThresholds, req.overboughtThresholds
        ):
            if oversold >= overbought:
                continue
            df = df_raw.copy()
            df["ret"] = close.pct_change().fillna(0.0)
            rsi = pd.to_numeric(ta.rsi(close, length=period), errors="coerce").fillna(50)
            pos_arr, cur = [], 0
            for r in rsi:
                if r < oversold: cur = 1
                elif r > overbought: cur = 0
                pos_arr.append(cur)
            df["pos"] = pd.Series(pos_arr, index=df.index).shift(1).fillna(0)
            df = _calc_equity(df, req.initialCapital, req.feeBps)
            results.append({
                "params": {"period": period, "oversold": oversold, "overbought": overbought},
                "totalReturn": float(df["equity"].iloc[-1] / df["equity"].iloc[0] - 1.0),
                "sharpe": _sharpe(df["str_ret"]),
                "maxDrawdown": _max_drawdown(df["equity"]),
                "numTrades": int(df["trade"].sum()),
            })

    elif req.strategy == "BOLLINGER_BAND":
        for period, std in iterproduct(req.bbPeriods, req.bbStds):
            df = df_raw.copy()
            df["ret"] = close.pct_change().fillna(0.0)
            bb = ta.bbands(close, length=period, std=std)
            if bb is None or bb.empty:
                continue
            lower_col = next((c for c in bb.columns if str(c).startswith("BBL")), None)
            upper_col = next((c for c in bb.columns if str(c).startswith("BBU")), None)
            lower = pd.to_numeric(bb[lower_col] if lower_col else bb.iloc[:, 0], errors="coerce")
            upper = pd.to_numeric(bb[upper_col] if upper_col else bb.iloc[:, 2], errors="coerce")
            pos_arr, cur = [], 0
            for c_val, lo, up in zip(df["close"].astype(float), lower.fillna(0), upper.fillna(1e12)):
                if c_val <= lo: cur = 1
                elif c_val >= up: cur = 0
                pos_arr.append(cur)
            df["pos"] = pd.Series(pos_arr, index=df.index).shift(1).fillna(0)
            df = _calc_equity(df, req.initialCapital, req.feeBps)
            results.append({
                "params": {"period": period, "std": std},
                "totalReturn": float(df["equity"].iloc[-1] / df["equity"].iloc[0] - 1.0),
                "sharpe": _sharpe(df["str_ret"]),
                "maxDrawdown": _max_drawdown(df["equity"]),
                "numTrades": int(df["trade"].sum()),
            })
    else:
        raise HTTPException(400, f"Grid search not supported for strategy: {req.strategy}")

    results.sort(key=lambda x: x["sharpe"], reverse=True)
    best = results[0] if results else None

    return {
        "ticker": req.ticker,
        "market": req.market,
        "strategy": req.strategy,
        "best": best,
        "grid": results,
    }


@router.post("/analyze")
async def analyze(req: AnalyzeReq):
    """insights 엔드포인트 기반의 요약 의사결정 응답"""
    ticker = req.ticker.strip().upper()
    market = (req.market or "US").strip().upper()

    if not ticker:
        raise HTTPException(status_code=400, detail="ticker required")

    # horizonDays를 insights의 days로 매핑 (과도한 요청 방지)
    horizon = req.horizonDays if isinstance(req.horizonDays, int) else 252
    days = max(30, min(horizon, 365))

    # 순환 import 방지를 위해 함수 내부에서 import
    from routes.intelligence import InsightRequest, insights

    ins = await insights(
        InsightRequest(
            ticker=ticker,
            market=market,
            days=days,
            newsLimit=20
        ),
        test=False
    )

    rec = ins.get("recommendation") or {}
    action_raw = str(rec.get("action") or "NEUTRAL").upper()

    # 백엔드/프론트 호환용 정규화 액션
    if action_raw in ("STRONG_BUY", "BUY"):
        action = "BUY"
    elif action_raw in ("STRONG_SELL", "SELL"):
        action = "SELL"
    else:
        action = "HOLD"

    confidence = rec.get("confidence")
    if not isinstance(confidence, (int, float)):
        confidence = 0.5

    reasons = rec.get("reasons") if isinstance(rec.get("reasons"), list) else []
    top_reasons = [str(r) for r in reasons[:3]]
    reason_text = rec.get("text") or ("; ".join(top_reasons) if top_reasons else "insufficient data")

    return {
        "ticker": ticker,
        "market": market,
        "horizonDays": horizon,
        "riskProfile": req.riskProfile or "balanced",
        "decision": {
            "action": action,
            "rawAction": action_raw,
            "confidence": float(confidence),
            "score": rec.get("score"),
            "confidenceGrade": rec.get("confidenceGrade"),
            "dataCompleteness": rec.get("dataCompleteness"),
        },
        "reason": reason_text,
        "recommendation": rec,
    }
