"""
기술적 분석 지표 계산 (RSI, MACD, Bollinger, SMA 등)
"""
from typing import Optional, List
from datetime import datetime, timedelta
from datetime import date as _date


def _to_int(x):
    """문자열/숫자 값을 안전하게 int로 변환한다. 실패 시 None."""
    if x in (None, "", "None"):
        return None
    try:
        return int(str(x).strip().replace(",", ""))
    except Exception:
        return None


def _to_float(x):
    """문자열/숫자 값을 안전하게 float으로 변환한다. 실패 시 None."""
    if x in (None, "", "None"):
        return None
    try:
        return float(str(x).strip().replace(",", ""))
    except Exception:
        return None


def _parse_date(d: str):
    """`YYYY-MM-DD` 문자열을 date로 파싱한다."""
    return datetime.strptime(d, "%Y-%m-%d").date()


def _parse_iso_date(s: str) -> Optional[_date]:
    """ISO 형식 날짜 문자열을 수동 파싱한다. 실패 시 None."""
    try:
        parts = s.split("-")
        return _date(int(parts[0]), int(parts[1]), int(parts[2]))
    except Exception:
        return None


def _sma(values: List[float], n: int) -> Optional[float]:
    """앞 n개 값에 대한 단순이동평균을 계산한다."""
    if n <= 0 or len(values) < n:
        return None
    return sum(values[:n]) / float(n)


def compute_technicals_from_daily(daily: list[tuple[str, float]], overview: dict | None = None) -> dict:
    """일봉 데이터에서 기술적 지표 계산 (Alpha Vantage 호환)"""
    overview = overview or {}
    closes = [c for _, c in daily]

    if not closes:
        return {"ok": False, "reason": "no_daily_points"}

    latest_date = daily[0][0]
    latest_close = closes[0]

    def sma(xs: list[float], n: int) -> float | None:
        if len(xs) < n:
            return None
        return sum(xs[:n]) / float(n)

    sma50 = sma(closes, 50)
    sma200 = sma(closes, 200)

    if sma50 is None:
        sma50 = _to_float(overview.get("50DayMovingAverage"))
    if sma200 is None:
        sma200 = _to_float(overview.get("200DayMovingAverage"))

    high52 = None
    low52 = None
    if len(closes) >= 252:
        window = closes[:252]
        high52 = max(window)
        low52 = min(window)
    else:
        high52 = _to_float(overview.get("52WeekHigh"))
        low52 = _to_float(overview.get("52WeekLow"))

    return {
        "ok": True,
        "asOf": latest_date,
        "close": latest_close,
        "sma50": sma50,
        "sma200": sma200,
        "week52High": high52,
        "week52Low": low52,
        "source": "TIME_SERIES_DAILY + OVERVIEW(50/200MA,52W)",
    }


def compute_technicals_from_points(points: list[dict], quote: dict | None = None, overview: dict | None = None) -> dict:
    """가격 포인트에서 기술적 지표 계산"""
    quote = quote or {}
    overview = overview or {}

    closes = []
    for p in points:
        c = p.get("close")
        if isinstance(c, (int, float)):
            closes.append(float(c))

    as_of = quote.get("latestTradingDay") or (points[-1].get("date") if points else None)
    latest_close = quote.get("price")
    if not isinstance(latest_close, (int, float)):
        latest_close = closes[-1] if closes else None

    # RSI14 (Wilder)
    rsi14 = None
    if len(closes) >= 15:
        deltas = [closes[i] - closes[i-1] for i in range(1, len(closes))]
        gains = [max(d, 0.0) for d in deltas]
        losses = [max(-d, 0.0) for d in deltas]
        period = 14
        avg_gain = sum(gains[:period]) / period
        avg_loss = sum(losses[:period]) / period
        for i in range(period, len(gains)):
            avg_gain = (avg_gain * (period - 1) + gains[i]) / period
            avg_loss = (avg_loss * (period - 1) + losses[i]) / period
        if avg_loss == 0:
            rsi14 = 100.0
        else:
            rs = avg_gain / avg_loss
            rsi14 = 100.0 - (100.0 / (1.0 + rs))

    # MACD (12/26/9)
    macd_data = None
    if len(closes) >= 35:
        def ema(values: list[float], period: int) -> list[float]:
            if len(values) < period:
                return []
            alpha = 2 / (period + 1)
            result = [sum(values[:period]) / period]
            for i in range(period, len(values)):
                result.append(alpha * values[i] + (1 - alpha) * result[-1])
            return result

        ema12 = ema(closes, 12)
        ema26 = ema(closes, 26)

        if len(ema12) > 0 and len(ema26) > 0:
            offset = 26 - 12
            macd_line = []
            for i in range(len(ema26)):
                if i + offset < len(ema12):
                    macd_line.append(ema12[i + offset] - ema26[i])

            if len(macd_line) >= 9:
                signal_line = ema(macd_line, 9)
                if signal_line:
                    hist_offset = 9
                    if len(macd_line) > hist_offset and len(signal_line) > 0:
                        latest_macd = macd_line[-1]
                        latest_signal = signal_line[-1]
                        latest_hist = latest_macd - latest_signal

                        hist_history = []
                        for i in range(max(0, len(macd_line) - 20), len(macd_line)):
                            sig_idx = i - hist_offset
                            if 0 <= sig_idx < len(signal_line):
                                hist_history.append(macd_line[i] - signal_line[sig_idx])

                        macd_data = {
                            "macd": latest_macd,
                            "signal": latest_signal,
                            "hist": latest_hist,
                            "histHistory": hist_history[-10:] if hist_history else [],
                            "trend": "BULLISH" if latest_hist > 0 else "BEARISH"
                        }

    # Bollinger Bands (20일)
    bollinger = None
    if len(closes) >= 20:
        sma20 = sum(closes[-20:]) / 20
        variance = sum((c - sma20) ** 2 for c in closes[-20:]) / 20
        std20 = variance ** 0.5
        bollinger = {
            "middle": sma20,
            "upper": sma20 + 2 * std20,
            "lower": sma20 - 2 * std20,
            "width": (4 * std20) / sma20 if sma20 > 0 else None,
            "position": None
        }
        if latest_close and std20 > 0:
            bollinger["position"] = (latest_close - sma20) / (2 * std20)

    # Returns
    def ret_n(n: int) -> float | None:
        if len(closes) < n + 1:
            return None
        base = closes[-(n+1)]
        if base == 0:
            return None
        return (closes[-1] - base) / base

    ret5 = ret_n(5)
    ret20 = ret_n(20)
    ret60 = ret_n(60)
    ret120 = ret_n(120)

    # Volatility 20d
    vol20 = None
    vol20_annual = None
    if len(closes) >= 21:
        rets = []
        for i in range(-20, 0):
            prev = closes[i-1]
            cur = closes[i]
            if prev != 0:
                rets.append((cur - prev) / prev)
        if len(rets) >= 2:
            mean = sum(rets) / len(rets)
            var = sum((x - mean) ** 2 for x in rets) / (len(rets) - 1)
            vol20 = var ** 0.5
            vol20_annual = vol20 * (252 ** 0.5)

    # SMA 직접 계산
    sma20_calc = sum(closes[-20:]) / 20 if len(closes) >= 20 else None
    sma50_calc = sum(closes[-50:]) / 50 if len(closes) >= 50 else None
    sma200_calc = sum(closes[-200:]) / 200 if len(closes) >= 200 else None

    ma50 = sma50_calc or _to_float(overview.get("50DayMovingAverage"))
    ma200 = sma200_calc or _to_float(overview.get("200DayMovingAverage"))
    w52h = _to_float(overview.get("52WeekHigh"))
    w52l = _to_float(overview.get("52WeekLow"))

    if len(closes) >= 252:
        w52h = max(closes[-252:])
        w52l = min(closes[-252:])

    price_above_ma200 = None
    if isinstance(latest_close, (int, float)) and isinstance(ma200, (int, float)) and ma200 != 0:
        price_above_ma200 = (latest_close >= ma200)

    ma_cross = None
    if ma50 and ma200:
        ma_cross = "GOLDEN" if ma50 > ma200 else "DEATH"

    w52_position = None
    if w52h and w52l and latest_close and w52h > w52l:
        w52_position = (latest_close - w52l) / (w52h - w52l)

    return {
        "asOf": as_of,
        "close": latest_close,
        "rsi14": rsi14,
        "macd": macd_data,
        "bollinger": bollinger,
        "return5d": ret5,
        "return20d": ret20,
        "return60d": ret60,
        "return120d": ret120,
        "volatility20d": vol20,
        "volatility20dAnnual": vol20_annual,
        "sma20": sma20_calc,
        "ma50": ma50,
        "ma200": ma200,
        "maCross": ma_cross,
        "priceAboveMa200": price_above_ma200,
        "week52High": w52h,
        "week52Low": w52l,
        "week52Position": w52_position,
        "source": "prices + OVERVIEW(MA/52W) + quote(latest)",
    }


def _pick_quarter_pair(quarterly_reports: list):
    """YoY 비교용 분기 쌍 선택"""
    q = []
    for r in quarterly_reports or []:
        d = (r.get("fiscalDateEnding") or "").strip()
        rev = _to_int(r.get("totalRevenue"))
        if not d or rev is None:
            continue
        try:
            q.append((_parse_date(d), rev))
        except Exception:
            continue

    if len(q) < 2:
        return None

    q.sort(key=lambda x: x[0])
    latest_dt, latest_rev = q[-1]

    target = None
    for dt, rev in q:
        if dt.year == latest_dt.year - 1 and dt.month == latest_dt.month and dt.day == latest_dt.day:
            target = (dt, rev)
            break

    if target is None:
        one_year_ago = latest_dt - timedelta(days=365)
        cands = []
        for dt, rev in q[:-1]:
            diff = abs((dt - one_year_ago).days)
            if 300 <= abs((latest_dt - dt).days) <= 430:
                cands.append((diff, dt, rev))
            if cands:
                cands.sort(key=lambda x: x[0])
                _, dt, rev = cands[0]
                target = (dt, rev)

    if not target:
        return None

    prev_dt, prev_rev = target
    if prev_rev == 0:
        return None

    yoy = (latest_rev - prev_rev) / prev_rev
    meta = {
        "latestQuarter": latest_dt.isoformat(),
        "compareQuarter": prev_dt.isoformat(),
        "latestRevenue": latest_rev,
        "compareRevenue": prev_rev,
        "method": "same_quarter_yoy_or_nearest_1y",
        "source": "INCOME_STATEMENT.quarterlyReports.totalRevenue",
    }
    return yoy, meta
