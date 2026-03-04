"""
밸류에이션, 모멘텀, 품질 점수 계산 함수들
"""
import math
import statistics
from datetime import datetime, timedelta
from typing import Optional


def continuous_score(value: float | None, low: float, high: float, reverse: bool = False) -> float | None:
    """
    연속 스코어 계산 (0~100)
    - reverse=False: 낮을수록 좋음 (PE, PS, PB)
    - reverse=True: 높을수록 좋음 (ROE, Margin)
    """
    if value is None:
        return None
    if high <= low:
        return 50.0
    if reverse:
        if value >= high:
            return 100.0
        if value <= low:
            return 0.0
        return 100.0 * (value - low) / (high - low)
    else:
        if value <= low:
            return 100.0
        if value >= high:
            return 0.0
        return 100.0 * (high - value) / (high - low)


def weighted_score(scores: list[tuple[float | None, float]]) -> float:
    """가중 평균 점수 계산 (None은 제외)"""
    valid = [(s, w) for s, w in scores if s is not None]
    if not valid:
        return 50.0
    total_weight = sum(w for _, w in valid)
    if total_weight == 0:
        return 50.0
    return sum(s * w for s, w in valid) / total_weight


def sigmoid_score(value: float | None, center: float, scale: float, reverse: bool = False) -> float | None:
    """시그모이드 기반 연속 스코어 (0~100)"""
    if value is None:
        return None
    x = (value - center) / max(center * 0.5, 1)
    if reverse:
        x = -x
    raw = 1 / (1 + math.exp(scale * x))
    return raw * 100


def calculate_multi_timeframe_momentum(closes: list[float]) -> dict:
    """다중 시간대 모멘텀 계산 (20/60/120/200일)"""
    result = {
        "mom20": None, "mom60": None, "mom120": None, "mom200": None,
        "composite": None, "trend_strength": None, "trend_direction": None
    }
    if len(closes) < 21:
        return result

    def calc_return(n: int) -> float | None:
        if len(closes) < n + 1:
            return None
        base = closes[-(n + 1)]
        if base == 0:
            return None
        return (closes[-1] - base) / base

    result["mom20"] = calc_return(20)
    result["mom60"] = calc_return(60) if len(closes) >= 61 else None
    result["mom120"] = calc_return(120) if len(closes) >= 121 else None
    result["mom200"] = calc_return(200) if len(closes) >= 201 else None

    weights = {"mom20": 0.40, "mom60": 0.30, "mom120": 0.20, "mom200": 0.10}
    valid_moms = []
    total_weight = 0

    for key, w in weights.items():
        if result[key] is not None:
            valid_moms.append((result[key], w))
            total_weight += w

    if valid_moms and total_weight > 0:
        result["composite"] = sum(m * w for m, w in valid_moms) / total_weight
        directions = [1 if m > 0 else -1 if m < 0 else 0 for m, _ in valid_moms]
        if all(d > 0 for d in directions):
            result["trend_strength"] = "STRONG_UP"
            result["trend_direction"] = 1
        elif all(d < 0 for d in directions):
            result["trend_strength"] = "STRONG_DOWN"
            result["trend_direction"] = -1
        elif sum(directions) > 0:
            result["trend_strength"] = "WEAK_UP"
            result["trend_direction"] = 0.5
        elif sum(directions) < 0:
            result["trend_strength"] = "WEAK_DOWN"
            result["trend_direction"] = -0.5
        else:
            result["trend_strength"] = "MIXED"
            result["trend_direction"] = 0

    return result


def calculate_rsi_score(rsi: float | None) -> tuple[float | None, str]:
    """개선된 RSI 스코어링 (연속적 가중치)"""
    if rsi is None or not isinstance(rsi, (int, float)):
        return None, "N/A"
    if rsi <= 30:
        score = 70 + (30 - rsi) * (30 / 30)
        zone = "과매도"
    elif rsi >= 70:
        score = 30 - (rsi - 70) * (30 / 30)
        zone = "과매수"
    else:
        if rsi < 40:
            score = 50 + (40 - rsi) * 1
            zone = "중립 (약과매도)"
        elif rsi > 60:
            score = 50 - (rsi - 60) * 1
            zone = "중립 (약과매수)"
        else:
            score = 50
            zone = "중립"
    return max(0, min(100, score)), zone


def calculate_macd_score(macd_hist: float | None, hist_history: list[float] = None) -> tuple[float | None, str]:
    """개선된 MACD 스코어링 (표준편차 기반 정규화)"""
    if macd_hist is None:
        return None, "N/A"
    if hist_history and len(hist_history) >= 10:
        mean = statistics.mean(hist_history)
        stdev = statistics.stdev(hist_history) if len(hist_history) > 1 else 1
        if stdev == 0:
            stdev = 1
        z_score = (macd_hist - mean) / stdev
        score = 50 + z_score * 25
    else:
        score = 50 + min(50, max(-50, macd_hist * 500))
    score = max(0, min(100, score))
    if macd_hist > 0:
        signal = "상승" if score > 60 else "약상승"
    elif macd_hist < 0:
        signal = "하락" if score < 40 else "약하락"
    else:
        signal = "중립"
    return score, signal


def calculate_obv_score(obv_data: dict | None, price_momentum: float | None) -> tuple[float | None, str]:
    """개선된 OBV 스코어링 (가격-거래량 발산 감지)"""
    if not obv_data or not obv_data.get("obvTrend"):
        return None, "N/A"
    trend = obv_data["obvTrend"]
    obv_momentum = obv_data.get("obvMomentum", 0) or 0
    base_score = 50
    if trend == "RISING":
        base_score = 70 + min(30, abs(obv_momentum) * 200)
    elif trend == "FALLING":
        base_score = 30 - min(30, abs(obv_momentum) * 200)
    divergence = None
    if price_momentum is not None:
        if trend == "RISING" and price_momentum < -0.02:
            divergence = "BULLISH_DIVERGENCE"
            base_score = min(100, base_score + 10)
        elif trend == "FALLING" and price_momentum > 0.02:
            divergence = "BEARISH_DIVERGENCE"
            base_score = max(0, base_score - 10)
    description = f"{trend}"
    if divergence:
        description += f" ({divergence})"
    return max(0, min(100, base_score)), description


def calculate_quality_score(fund: dict, bands: dict) -> tuple[float | None, list[str]]:
    """기업 품질 점수 계산"""
    quality_criteria = bands.get("quality", {})
    min_roe = quality_criteria.get("min_roe", 0.10)
    score = 50
    flags = []

    roe = fund.get("roe")
    if roe is not None:
        if roe >= min_roe * 1.5:
            score += 15
            flags.append(f"ROE 우수 ({roe*100:.1f}%)")
        elif roe >= min_roe:
            score += 8
            flags.append(f"ROE 양호 ({roe*100:.1f}%)")
        elif roe > 0:
            flags.append(f"ROE 저조 ({roe*100:.1f}%)")
        else:
            score -= 15
            flags.append(f"ROE 적자 ({roe*100:.1f}%)")

    opm = fund.get("operatingMargin")
    if opm is not None:
        if opm >= 0.20:
            score += 12
            flags.append(f"영업이익률 높음 ({opm*100:.1f}%)")
        elif opm >= 0.10:
            score += 6
        elif opm < 0:
            score -= 12
            flags.append(f"영업적자 ({opm*100:.1f}%)")

    npm = fund.get("profitMargin")
    if npm is not None:
        if npm >= 0.15:
            score += 8
            flags.append(f"순이익률 우수 ({npm*100:.1f}%)")
        elif npm < 0:
            score -= 10
            flags.append(f"순손실 ({npm*100:.1f}%)")

    div_yield = fund.get("dividendYield")
    if div_yield is not None and div_yield > 0.01:
        score += 5
        flags.append(f"배당 {div_yield*100:.1f}%")

    return max(0, min(100, score)), flags


def calculate_confidence_v2(
    available_metrics: int,
    total_metrics: int,
    critical_metrics_present: list[bool]
) -> tuple[float, str]:
    """개선된 신뢰도 계산"""
    data_ratio = available_metrics / max(total_metrics, 1)
    base_confidence = data_ratio * 0.30

    critical_weights = [0.20, 0.20, 0.15, 0.15]
    critical_score = 0
    for present, weight in zip(critical_metrics_present, critical_weights):
        if present:
            critical_score += weight

    confidence = base_confidence + critical_score
    confidence = max(0.20, min(1.0, confidence))

    if confidence >= 0.85:
        grade = "HIGH"
    elif confidence >= 0.65:
        grade = "MEDIUM"
    elif confidence >= 0.45:
        grade = "LOW"
    else:
        grade = "VERY_LOW"

    return round(confidence, 2), grade


def calculate_smoothed_revenue_growth(quarterly_reports: list) -> tuple[float | None, dict]:
    """개선된 매출 성장률 계산 (2-3분기 평균으로 스무딩)"""
    if not quarterly_reports or len(quarterly_reports) < 5:
        return None, {"error": "insufficient_data"}

    from analysis.technicals import _to_int

    quarters = []
    for r in quarterly_reports:
        d = (r.get("fiscalDateEnding") or "").strip()
        rev = _to_int(r.get("totalRevenue"))
        if not d or rev is None or rev <= 0:
            continue
        try:
            dt = datetime.strptime(d, "%Y-%m-%d").date()
            quarters.append((dt, rev))
        except Exception:
            continue

    if len(quarters) < 5:
        return None, {"error": "insufficient_valid_quarters"}

    quarters.sort(key=lambda x: x[0])

    recent_2q = [q[1] for q in quarters[-2:]]
    latest_date = quarters[-1][0]
    one_year_ago = latest_date - timedelta(days=365)

    old_quarters = []
    for dt, rev in quarters[:-2]:
        days_diff = abs((dt - one_year_ago).days)
        if days_diff < 120:
            old_quarters.append((days_diff, rev))

    old_quarters.sort(key=lambda x: x[0])

    if len(old_quarters) < 2:
        if len(quarters) >= 5:
            return _simple_yoy_growth(quarters)
        return None, {"error": "no_comparable_period"}

    old_2q = [q[1] for q in old_quarters[:2]]
    recent_avg = sum(recent_2q) / len(recent_2q)
    old_avg = sum(old_2q) / len(old_2q)

    if old_avg == 0:
        return None, {"error": "zero_base_revenue"}

    growth = (recent_avg - old_avg) / old_avg

    meta = {
        "method": "smoothed_2q_average",
        "recent_quarters": [q[0].isoformat() for q in quarters[-2:]],
        "recent_avg_revenue": recent_avg,
        "compare_avg_revenue": old_avg,
        "source": "AlphaVantage INCOME_STATEMENT.quarterlyReports"
    }

    return growth, meta


def _simple_yoy_growth(quarters: list) -> tuple[float | None, dict]:
    """단순 YoY 성장률 (fallback)"""
    if len(quarters) < 5:
        return None, {"error": "insufficient_data"}
    latest_dt, latest_rev = quarters[-1]
    for dt, rev in reversed(quarters[:-1]):
        days_diff = (latest_dt - dt).days
        if 300 <= days_diff <= 430:
            if rev > 0:
                growth = (latest_rev - rev) / rev
                return growth, {
                    "method": "simple_yoy",
                    "latest_quarter": latest_dt.isoformat(),
                    "compare_quarter": dt.isoformat()
                }
    return None, {"error": "no_yoy_match"}
