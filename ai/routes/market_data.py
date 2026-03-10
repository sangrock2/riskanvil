"""
시장 데이터 라우트: 가격, 시세, 펀더멘탈, 뉴스, OBV, 시장환경
"""
import logging
from fastapi import APIRouter, HTTPException, Query

from config import DATA_PROVIDER
from data_sources import yfinance_client, alpha_client
from cache import alpha_get
from analysis.technicals import _to_float

logger = logging.getLogger("app")

router = APIRouter()


# ── 종목 검색 (Alpha Vantage 유지) ──

@router.get("/symbol_search")
async def symbol_search(keywords: str = Query(..., min_length=1), market: str = "US", test: bool = False):
    """
    종목 검색.
    1) Alpha Vantage 우선
    2) 실패/키 미설정 시 Yahoo 검색 폴백
    """
    try:
        items = await alpha_client.symbol_search(keywords, market, test)
        if items:
            return items
        logger.warning("Alpha symbol search returned empty. fallback to yfinance. q=%s market=%s", keywords, market)
    except Exception as e:
        logger.warning("Alpha symbol search failed. fallback to yfinance. q=%s market=%s reason=%s", keywords, market, e)

    try:
        return await yfinance_client.search_symbols(keywords, market, limit=10)
    except Exception as e:
        logger.warning("yfinance symbol search fallback failed. q=%s market=%s reason=%s", keywords, market, e)
        return []


# ── 가격 데이터 ──

@router.get("/prices")
async def prices(ticker: str, market: str = "US", days: int = 90, test: bool = False):
    """일별 종가 데이터"""
    if test and DATA_PROVIDER != "yfinance":
        # 테스트 모드에서는 Alpha Vantage 테스트 데이터 사용
        data = await alpha_get(
            {"function": "TIME_SERIES_DAILY", "symbol": ticker, "outputsize": "compact"},
            ttl_sec=120, test=True
        )
        ts = data.get("Time Series (Daily)") or {}
        if not ts:
            raise HTTPException(status_code=404, detail="no daily series")
        dates = sorted(ts.keys())[-max(2, min(days, 200)):]
        pts = []
        for d in dates:
            row = ts[d] or {}
            close_str = row.get("4. close") or row.get("5. adjusted close")
            if close_str is None:
                continue
            pts.append({"date": d, "close": float(close_str)})
        if len(pts) < 2:
            raise HTTPException(status_code=404, detail="not enough price points")
        return {"ticker": ticker, "market": market, "points": pts}

    # yfinance 사용
    result = await yfinance_client.get_prices(ticker, market, days)
    if not result.get("points") or len(result["points"]) < 2:
        raise HTTPException(status_code=404, detail="not enough price points")
    return result


@router.get("/ohlc")
async def ohlc(ticker: str, market: str = "US", days: int = 90, test: bool = False):
    """OHLC + Volume 데이터"""
    if test and DATA_PROVIDER != "yfinance":
        data = await alpha_get(
            {"function": "TIME_SERIES_DAILY", "symbol": ticker, "outputsize": "compact"},
            ttl_sec=120, test=True
        )
        ts = data.get("Time Series (Daily)") or {}
        if not ts:
            raise HTTPException(status_code=404, detail="no daily series")
        dates = sorted(ts.keys())[-max(2, min(days, 200)):]
        ohlc_data = []
        for d in dates:
            row = ts[d] or {}
            try:
                ohlc_data.append({
                    "date": d,
                    "open": float(row.get("1. open", 0)),
                    "high": float(row.get("2. high", 0)),
                    "low": float(row.get("3. low", 0)),
                    "close": float(row.get("4. close", 0)),
                    "volume": int(float(row.get("5. volume", 0)))
                })
            except (ValueError, TypeError):
                continue
        if len(ohlc_data) < 2:
            raise HTTPException(status_code=404, detail="not enough OHLC data points")
        return {"ticker": ticker, "market": market, "data": ohlc_data}

    result = await yfinance_client.get_ohlc(ticker, market, days)
    if not result.get("data") or len(result["data"]) < 2:
        raise HTTPException(status_code=404, detail="not enough OHLC data points")
    return result


# ── 시세 ──

@router.get("/quote")
async def quote(ticker: str, market: str = "US", test: bool = False):
    """실시간 시세"""
    if test and DATA_PROVIDER != "yfinance":
        from cache import alpha_get
        data = await alpha_get({"function": "GLOBAL_QUOTE", "symbol": ticker}, ttl_sec=30, test=True)
        q = (data or {}).get("Global Quote") or {}
        if not q:
            raise HTTPException(status_code=404, detail="no quote")
        return {
            "ticker": ticker,
            "market": market,
            "price": float((q.get("05. price") or "0").strip()),
            "change": float((q.get("09. change") or "0").strip()),
            "changePercent": (q.get("10. change percent") or "").strip(),
            "latestTradingDay": (q.get("07. latest trading day") or "").strip(),
        }

    result = await yfinance_client.get_quote(ticker, market)
    if result.get("price") is None:
        raise HTTPException(status_code=404, detail="no quote")
    return result


# ── 펀더멘탈 ──

@router.get("/fundamentals")
async def fundamentals(ticker: str, market: str = "US", test: bool = False):
    """펀더멘탈 데이터"""
    if test and DATA_PROVIDER != "yfinance":
        from cache import alpha_get
        from analysis.technicals import _to_int, _pick_quarter_pair

        ov = await alpha_get({"function": "OVERVIEW", "symbol": ticker}, ttl_sec=3600, test=True)
        inc = await alpha_get({"function": "INCOME_STATEMENT", "symbol": ticker}, ttl_sec=3600, test=True)

        # fetch quote for close price
        data = await alpha_get({"function": "GLOBAL_QUOTE", "symbol": ticker}, ttl_sec=30, test=True)
        gq = (data or {}).get("Global Quote") or {}
        close = _to_float((gq.get("05. price") or "").strip()) if gq else None
        close_asof = (gq.get("07. latest trading day") or "").strip() if gq else None

        pe = _to_float((ov or {}).get("PERatio"))
        ps = _to_float((ov or {}).get("PriceToSalesRatioTTM"))
        pb = _to_float((ov or {}).get("PriceToBookRatio"))
        market_cap = _to_int((ov or {}).get("MarketCapitalization"))
        roe = _to_float((ov or {}).get("ReturnOnEquityTTM"))
        roa = _to_float((ov or {}).get("ReturnOnAssetsTTM"))
        operating_margin = _to_float((ov or {}).get("OperatingMarginTTM"))
        profit_margin = _to_float((ov or {}).get("ProfitMargin"))
        gross_margin = _to_float((ov or {}).get("GrossProfitTTM"))
        ev_to_ebitda = _to_float((ov or {}).get("EVToEBITDA"))
        ev_to_revenue = _to_float((ov or {}).get("EVToRevenue"))
        dividend_yield = _to_float((ov or {}).get("DividendYield"))
        beta = _to_float((ov or {}).get("Beta"))
        quarterly_earnings_growth = _to_float((ov or {}).get("QuarterlyEarningsGrowthYOY"))
        quarterly_revenue_growth = _to_float((ov or {}).get("QuarterlyRevenueGrowthYOY"))

        qr = (inc or {}).get("quarterlyReports") or []
        pair = _pick_quarter_pair(qr)

        fund = {
            "sector": (ov or {}).get("Sector"),
            "industry": (ov or {}).get("Industry"),
            "marketCap": market_cap,
            "pe": pe, "ps": ps, "pb": pb,
            "roe": roe, "roa": roa,
            "operatingMargin": operating_margin,
            "profitMargin": profit_margin,
            "grossMargin": gross_margin,
            "evToEbitda": ev_to_ebitda,
            "evToRevenue": ev_to_revenue,
            "dividendYield": dividend_yield,
            "beta": beta,
            "quarterlyEarningsGrowth": quarterly_earnings_growth,
            "quarterlyRevenueGrowth": quarterly_revenue_growth,
            "close": close,
            "closeMeta": None if not gq else {"asOf": close_asof, "source": "AlphaVantage GLOBAL_QUOTE"},
            "revYoY": None, "revYoYMeta": None,
            "ticker": ticker, "market": market,
        }
        if pair:
            yoy, meta = pair
            fund["revYoY"] = yoy
            fund["revYoYMeta"] = meta
        return fund

    return await yfinance_client.get_fundamentals(ticker, market)


# ── 뉴스 (Alpha Vantage 유지) ──

@router.get("/news")
async def news(ticker: str, market: str = "US", limit: int = 20, test: bool = False):
    """뉴스 감성 분석"""
    try:
        result = await alpha_client.get_news_sentiment(ticker, market, limit, test)
        if "summary" not in result:
            result["summary"] = ""
        result.setdefault("source", "alpha_vantage")
        return result
    except Exception as e:
        logger.warning("Alpha news unavailable for %s. Falling back to yfinance news. reason=%s", ticker, e)

    # Alpha Vantage API 키 미설정/레이트리밋 시에도 분석이 멈추지 않도록 폴백 제공
    fallback = await yfinance_client.get_news_sentiment(ticker, market, limit)
    if "summary" not in fallback:
        fallback["summary"] = ""
    return fallback


# ── OBV ──

@router.get("/obv")
async def obv(ticker: str, market: str = "US", test: bool = False):
    """On-Balance Volume"""
    if test and DATA_PROVIDER != "yfinance":
        data = await alpha_get(
            {"function": "OBV", "symbol": ticker, "interval": "daily"},
            ttl_sec=300, test=True
        )
        ta_data = data.get("Technical Analysis: OBV") or {}
        if not ta_data:
            return {"ticker": ticker, "market": market, "obv": None, "obvTrend": None, "obvMomentum": None}
        dates = sorted(ta_data.keys(), reverse=True)[:20]
        obv_values = []
        for d in dates:
            val = _to_float(ta_data[d].get("OBV"))
            if val is not None:
                obv_values.append((d, val))
        if len(obv_values) < 5:
            return {"ticker": ticker, "market": market, "obv": None, "obvTrend": None, "obvMomentum": None}
        latest_obv = obv_values[0][1]
        obv_5d_ago = obv_values[min(4, len(obv_values)-1)][1]
        obv_change = (latest_obv - obv_5d_ago) / abs(obv_5d_ago) if obv_5d_ago != 0 else 0
        if obv_change > 0.05:
            trend = "RISING"
        elif obv_change < -0.05:
            trend = "FALLING"
        else:
            trend = "NEUTRAL"
        return {
            "ticker": ticker, "market": market,
            "obv": latest_obv, "obvTrend": trend, "obvMomentum": obv_change,
            "asOf": obv_values[0][0], "source": "AlphaVantage OBV"
        }

    return await yfinance_client.get_obv(ticker, market)


# ── 시장 환경 ──

@router.get("/market_env")
async def market_env(test: bool = False):
    """시장 환경 지표"""
    if test and DATA_PROVIDER != "yfinance":
        env_data = {}
        try:
            from cache import alpha_get
            treasury = await alpha_get(
                {"function": "TREASURY_YIELD", "interval": "daily", "maturity": "10year"},
                ttl_sec=3600, test=True
            )
            t_data = treasury.get("data") or []
            if t_data and len(t_data) > 0:
                latest = t_data[0]
                env_data["treasuryYield10Y"] = _to_float(latest.get("value"))
                env_data["treasuryYieldDate"] = latest.get("date")
        except Exception:
            env_data["treasuryYield10Y"] = None

        try:
            spy_data = await prices("SPY", "US", 30, test=True)
            pts = spy_data.get("points") or []
            if len(pts) >= 20:
                closes = [p["close"] for p in pts[-20:]]
                rets = [(closes[i] - closes[i-1]) / closes[i-1] for i in range(1, len(closes))]
                if len(rets) >= 2:
                    mean = sum(rets) / len(rets)
                    var = sum((x - mean) ** 2 for x in rets) / (len(rets) - 1)
                    vol20 = (var ** 0.5) * (252 ** 0.5)
                    env_data["spyVolatility20d"] = vol20
                    if vol20 > 0.30:
                        env_data["fearLevel"] = "HIGH"
                    elif vol20 > 0.18:
                        env_data["fearLevel"] = "MODERATE"
                    else:
                        env_data["fearLevel"] = "LOW"
        except Exception:
            env_data["spyVolatility20d"] = None
            env_data["fearLevel"] = None

        return {
            "treasuryYield10Y": env_data.get("treasuryYield10Y"),
            "treasuryYieldDate": env_data.get("treasuryYieldDate"),
            "spyVolatility20d": env_data.get("spyVolatility20d"),
            "fearLevel": env_data.get("fearLevel"),
            "source": "AlphaVantage TREASURY_YIELD + SPY volatility"
        }

    return await yfinance_client.get_market_env()
