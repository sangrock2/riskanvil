"""
yfinance 데이터 소스 클라이언트
Alpha Vantage 시세/가격/펀더멘탈을 대체
"""
import asyncio
import logging
from datetime import datetime, timedelta

import httpx
import yfinance as yf
import pandas as pd

from cache import cache_get, cache_set
from analysis.technicals import _to_float
from config import POS_WORDS, NEG_WORDS

logger = logging.getLogger("app")

_SEARCH_FALLBACK_SYMBOLS = [
    {"symbol": "AAPL", "name": "Apple Inc.", "region": "United States"},
    {"symbol": "MSFT", "name": "Microsoft Corporation", "region": "United States"},
    {"symbol": "GOOGL", "name": "Alphabet Inc.", "region": "United States"},
    {"symbol": "AMZN", "name": "Amazon.com Inc.", "region": "United States"},
    {"symbol": "NVDA", "name": "NVIDIA Corporation", "region": "United States"},
    {"symbol": "TSLA", "name": "Tesla Inc.", "region": "United States"},
    {"symbol": "META", "name": "Meta Platforms Inc.", "region": "United States"},
    {"symbol": "SPY", "name": "SPDR S&P 500 ETF Trust", "region": "United States"},
    {"symbol": "QQQ", "name": "Invesco QQQ Trust", "region": "United States"},
    {"symbol": "005930.KS", "name": "Samsung Electronics Co., Ltd.", "region": "South Korea"},
    {"symbol": "000660.KS", "name": "SK hynix Inc.", "region": "South Korea"},
    {"symbol": "035420.KS", "name": "NAVER Corporation", "region": "South Korea"},
    {"symbol": "005380.KS", "name": "Hyundai Motor Company", "region": "South Korea"},
]


def _run_sync(func, *args, **kwargs):
    """동기 함수를 비동기로 래핑"""
    return asyncio.to_thread(func, *args, **kwargs)


def _yf_ticker_suffix(ticker: str, market: str) -> str:
    """한국 시장 티커 접미사 처리"""
    if market.upper() == "KR":
        if not ticker.endswith((".KS", ".KQ")):
            return ticker + ".KS"
    return ticker


def _safe_get(info: dict, key: str, default=None):
    """yfinance info dict에서 안전하게 값 추출"""
    val = info.get(key)
    if val is None or val == "":
        return default
    return val


def _normalize_news_item(raw: dict) -> dict:
    """yfinance 뉴스 아이템의 스키마 차이를 평탄화한다."""
    if not isinstance(raw, dict):
        return {}

    # 최신 yfinance는 content 내부에 대부분의 필드가 들어있다.
    content = raw.get("content")
    if isinstance(content, dict):
        item = dict(content)
        if "providerPublishTime" not in item and raw.get("providerPublishTime") is not None:
            item["providerPublishTime"] = raw.get("providerPublishTime")
        if "publisher" not in item and raw.get("publisher") is not None:
            item["publisher"] = raw.get("publisher")
        return item
    return raw


def _extract_news_url(item: dict) -> str | None:
    """뉴스 아이템에서 URL을 우선순위대로 추출한다."""
    if not isinstance(item, dict):
        return None

    direct = item.get("link") or item.get("url")
    if isinstance(direct, str) and direct.strip():
        return direct.strip()

    canonical = item.get("canonicalUrl")
    if isinstance(canonical, dict):
        u = canonical.get("url")
        if isinstance(u, str) and u.strip():
            return u.strip()

    clickthrough = item.get("clickThroughUrl")
    if isinstance(clickthrough, dict):
        u = clickthrough.get("url")
        if isinstance(u, str) and u.strip():
            return u.strip()

    return None


def _extract_news_published_at(item: dict) -> str | None:
    """뉴스 아이템 발행 시각을 ISO8601 문자열로 통일한다."""
    if not isinstance(item, dict):
        return None

    pub_date = item.get("pubDate")
    if isinstance(pub_date, str) and pub_date.strip():
        return pub_date.strip()

    ts = item.get("providerPublishTime")
    try:
        if ts is not None:
            ts_int = int(ts)
            return datetime.utcfromtimestamp(ts_int).isoformat() + "Z"
    except Exception:
        return None
    return None


def _headline_sentiment(title: str | None) -> dict | None:
    """헤드라인 기반의 단순 센티먼트 추정."""
    if not title:
        return None

    low = str(title).lower()
    pos_hits = sum(1 for w in POS_WORDS if w in low)
    neg_hits = sum(1 for w in NEG_WORDS if w in low)

    if pos_hits == 0 and neg_hits == 0:
        return {"label": "neutral", "score": 0.0}
    if pos_hits > neg_hits:
        score = min(1.0, 0.3 + (pos_hits - neg_hits) * 0.2)
        return {"label": "positive", "score": score}
    if neg_hits > pos_hits:
        score = max(-1.0, -0.3 - (neg_hits - pos_hits) * 0.2)
        return {"label": "negative", "score": score}
    return {"label": "neutral", "score": 0.0}


def _is_market_match(symbol: str, exchange: str, market: str) -> bool:
    mk = (market or "US").upper()
    sym = (symbol or "").upper()
    ex = (exchange or "").upper()
    if mk == "KR":
        return sym.endswith(".KS") or sym.endswith(".KQ") or "KOREA" in ex or "KOSPI" in ex or "KOSDAQ" in ex
    if mk == "US":
        return not sym.endswith(".KS") and not sym.endswith(".KQ")
    return True


async def search_symbols(keywords: str, market: str = "US", limit: int = 10) -> list[dict]:
    """
    Alpha Vantage 종목검색 실패 시 대체용 Yahoo/정적 폴백 검색.
    반환 스키마는 alpha_client.symbol_search와 맞춘다.
    """
    q = (keywords or "").strip()
    if not q:
        return []

    limit = max(1, min(int(limit or 10), 20))
    ck = f"yf:symbol_search:{q.lower()}:{(market or 'US').upper()}:{limit}"
    cached = cache_get(ck)
    if cached is not None:
        return cached

    items: list[dict] = []
    seen: set[str] = set()

    # 1) Yahoo search endpoint
    try:
        async with httpx.AsyncClient(timeout=8) as client:
            resp = await client.get(
                "https://query2.finance.yahoo.com/v1/finance/search",
                params={"q": q, "quotesCount": 25, "newsCount": 0},
            )
            if resp.status_code == 200:
                payload = resp.json()
                quotes = payload.get("quotes") or []
                for row in quotes:
                    symbol = str(row.get("symbol") or "").strip().upper()
                    if not symbol or symbol in seen:
                        continue

                    exchange = str(
                        row.get("exchange")
                        or row.get("exchangeDisplay")
                        or row.get("fullExchangeName")
                        or ""
                    )
                    if not _is_market_match(symbol, exchange, market):
                        continue

                    name = row.get("shortname") or row.get("longname") or row.get("name") or symbol
                    item = {
                        "symbol": symbol,
                        "name": name,
                        "type": row.get("quoteType") or "Equity",
                        "region": "United States" if (market or "US").upper() == "US" else "South Korea",
                        "currency": row.get("currency"),
                        "matchScore": 1.0,
                    }
                    items.append(item)
                    seen.add(symbol)
                    if len(items) >= limit:
                        break
    except Exception as e:
        logger.warning("Yahoo symbol search fallback failed for q=%s market=%s: %s", q, market, e)

    # 2) 정적 폴백
    if not items:
        ql = q.lower()
        for row in _SEARCH_FALLBACK_SYMBOLS:
            symbol = row["symbol"].upper()
            name = row["name"]
            if not _is_market_match(symbol, "", market):
                continue
            if ql not in symbol.lower() and ql not in name.lower():
                continue
            if symbol in seen:
                continue
            items.append(
                {
                    "symbol": symbol,
                    "name": name,
                    "type": "Equity",
                    "region": row["region"],
                    "currency": "USD" if (market or "US").upper() == "US" else "KRW",
                    "matchScore": 0.5,
                }
            )
            seen.add(symbol)
            if len(items) >= limit:
                break

    cache_set(ck, items[:limit], ttl_sec=300)
    return items[:limit]


# ── 가격 데이터 ──

async def get_prices(ticker: str, market: str = "US", days: int = 90) -> dict:
    """
    TIME_SERIES_DAILY 대체 - {ticker, market, points: [{date, close}]}
    """
    ck = f"yf:prices:{ticker}:{market}:{days}"
    cached = cache_get(ck)
    if cached is not None:
        return cached

    yf_ticker = _yf_ticker_suffix(ticker, market)
    period = "2y" if days > 365 else "1y" if days > 90 else "6mo" if days > 30 else "1mo"

    def _fetch():
        df = yf.download(yf_ticker, period=period, auto_adjust=True, progress=False)
        return df

    df = await _run_sync(_fetch)

    if df is None or df.empty:
        return {"ticker": ticker, "market": market, "points": []}

    # MultiIndex 처리
    if isinstance(df.columns, pd.MultiIndex):
        df.columns = df.columns.get_level_values(0)

    df = df.reset_index()
    col_map = {c: c.lower().strip() for c in df.columns}
    df.rename(columns=col_map, inplace=True)

    if "close" not in df.columns:
        return {"ticker": ticker, "market": market, "points": []}

    df = df.sort_values("date")
    df = df.tail(max(2, min(days, len(df))))

    pts = []
    for _, row in df.iterrows():
        d = row.get("date")
        c = row.get("close")
        if d is not None and c is not None:
            date_str = d.strftime("%Y-%m-%d") if hasattr(d, "strftime") else str(d)[:10]
            pts.append({"date": date_str, "close": float(c)})

    result = {"ticker": ticker, "market": market, "points": pts}
    cache_set(ck, result, ttl_sec=120)
    return result


async def get_ohlc(ticker: str, market: str = "US", days: int = 90) -> dict:
    """
    OHLC + Volume 데이터 - {ticker, market, data: [{date, open, high, low, close, volume}]}
    """
    ck = f"yf:ohlc:{ticker}:{market}:{days}"
    cached = cache_get(ck)
    if cached is not None:
        return cached

    yf_ticker = _yf_ticker_suffix(ticker, market)
    period = "2y" if days > 365 else "1y" if days > 90 else "6mo" if days > 30 else "1mo"

    def _fetch():
        df = yf.download(yf_ticker, period=period, auto_adjust=True, progress=False)
        return df

    df = await _run_sync(_fetch)

    if df is None or df.empty:
        return {"ticker": ticker, "market": market, "data": []}

    if isinstance(df.columns, pd.MultiIndex):
        df.columns = df.columns.get_level_values(0)

    df = df.reset_index()
    col_map = {c: c.lower().strip() for c in df.columns}
    df.rename(columns=col_map, inplace=True)
    df = df.sort_values("date")
    df = df.tail(max(2, min(days, len(df))))

    ohlc_data = []
    for _, row in df.iterrows():
        try:
            d = row.get("date")
            date_str = d.strftime("%Y-%m-%d") if hasattr(d, "strftime") else str(d)[:10]
            ohlc_data.append({
                "date": date_str,
                "open": float(row.get("open", 0)),
                "high": float(row.get("high", 0)),
                "low": float(row.get("low", 0)),
                "close": float(row.get("close", 0)),
                "volume": int(float(row.get("volume", 0)))
            })
        except (ValueError, TypeError):
            continue

    result = {"ticker": ticker, "market": market, "data": ohlc_data}
    cache_set(ck, result, ttl_sec=120)
    return result


async def get_quote(ticker: str, market: str = "US") -> dict:
    """
    GLOBAL_QUOTE 대체 - {ticker, market, price, change, changePercent, latestTradingDay}
    """
    ck = f"yf:quote:{ticker}:{market}"
    cached = cache_get(ck)
    if cached is not None:
        return cached

    yf_ticker = _yf_ticker_suffix(ticker, market)

    def _fetch():
        t = yf.Ticker(yf_ticker)
        try:
            fi = t.fast_info
            price = float(fi.last_price) if fi.last_price else None
            prev_close = float(fi.previous_close) if fi.previous_close else None
            change = None
            change_pct = None
            if price is not None and prev_close is not None and prev_close != 0:
                change = price - prev_close
                change_pct = f"{(change / prev_close) * 100:+.2f}%"
            return {
                "price": price,
                "previousClose": prev_close,
                "change": change,
                "changePercent": change_pct,
                "latestTradingDay": datetime.now().strftime("%Y-%m-%d"),
                "source": "yfinance fast_info",
            }
        except Exception as e:
            logger.warning(f"yfinance fast_info failed for {yf_ticker}: {e}")
            # fallback to history
            hist = t.history(period="2d")
            if hist is None or hist.empty:
                return None
            latest = hist.iloc[-1]
            prev = hist.iloc[-2] if len(hist) > 1 else None
            price = float(latest["Close"])
            prev_close = float(prev["Close"]) if prev is not None else None
            change = None
            change_pct = None
            if prev_close and prev_close != 0:
                change = price - prev_close
                change_pct = f"{(change / prev_close) * 100:+.2f}%"
            return {
                "price": price,
                "previousClose": prev_close,
                "change": change,
                "changePercent": change_pct,
                "latestTradingDay": hist.index[-1].strftime("%Y-%m-%d"),
                "source": "yfinance history",
            }

    raw = await _run_sync(_fetch)
    if raw is None:
        return {"ticker": ticker, "market": market, "price": None, "change": None, "changePercent": None, "latestTradingDay": None}

    result = {
        "ticker": ticker,
        "market": market,
        "price": raw.get("price"),
        "change": raw.get("change"),
        "changePercent": raw.get("changePercent"),
        "latestTradingDay": raw.get("latestTradingDay"),
    }
    cache_set(ck, result, ttl_sec=30)
    return result


async def get_quote_raw(ticker: str, market: str = "US") -> dict:
    """fetch_global_quote_raw 대체 - 내부용 원시 quote 데이터"""
    ck = f"yf:quote_raw:{ticker}:{market}"
    cached = cache_get(ck)
    if cached is not None:
        return cached

    yf_ticker = _yf_ticker_suffix(ticker, market)

    def _fetch():
        t = yf.Ticker(yf_ticker)
        try:
            fi = t.fast_info
            price = float(fi.last_price) if fi.last_price else None
            prev_close = float(fi.previous_close) if fi.previous_close else None
            change = None
            change_pct = None
            if price is not None and prev_close is not None and prev_close != 0:
                change = price - prev_close
                change_pct = f"{(change / prev_close) * 100:+.2f}%"
            return {
                "symbol": ticker,
                "open": float(fi.open) if fi.open else None,
                "high": float(fi.day_high) if fi.day_high else None,
                "low": float(fi.day_low) if fi.day_low else None,
                "price": price,
                "volume": int(fi.last_volume) if fi.last_volume else None,
                "latestTradingDay": datetime.now().strftime("%Y-%m-%d"),
                "previousClose": prev_close,
                "change": change,
                "changePercent": change_pct,
                "source": "yfinance fast_info",
            }
        except Exception:
            return {}

    raw = await _run_sync(_fetch)
    if raw:
        cache_set(ck, raw, ttl_sec=30)
    return raw or {}


async def get_fundamentals(ticker: str, market: str = "US") -> dict:
    """
    OVERVIEW + INCOME_STATEMENT 대체
    yfinance Ticker.info에서 펀더멘탈 추출
    """
    ck = f"yf:fundamentals:{ticker}:{market}"
    cached = cache_get(ck)
    if cached is not None:
        return cached

    yf_ticker = _yf_ticker_suffix(ticker, market)

    def _fetch():
        t = yf.Ticker(yf_ticker)
        info = t.info or {}

        # 현재가
        price = _safe_get(info, "currentPrice") or _safe_get(info, "regularMarketPrice")

        # 기본 밸류에이션
        pe = _safe_get(info, "trailingPE") or _safe_get(info, "forwardPE")
        ps = _safe_get(info, "priceToSalesTrailing12Months")
        pb = _safe_get(info, "priceToBook")
        market_cap = _safe_get(info, "marketCap")

        # 수익성
        roe = _safe_get(info, "returnOnEquity")
        roa = _safe_get(info, "returnOnAssets")
        operating_margin = _safe_get(info, "operatingMargins")
        profit_margin = _safe_get(info, "profitMargins")
        gross_margin = _safe_get(info, "grossMargins")

        # 추가 밸류에이션
        ev_to_ebitda = _safe_get(info, "enterpriseToEbitda")
        ev_to_revenue = _safe_get(info, "enterpriseToRevenue")
        dividend_yield = _safe_get(info, "dividendYield")
        beta = _safe_get(info, "beta")

        # 성장 지표
        earnings_growth = _safe_get(info, "earningsQuarterlyGrowth")
        revenue_growth = _safe_get(info, "revenueGrowth")

        # 매출 성장률 (Income Statement에서)
        rev_yoy = None
        rev_yoy_meta = None
        try:
            inc = t.income_stmt
            if inc is not None and not inc.empty:
                revenues = inc.loc["Total Revenue"] if "Total Revenue" in inc.index else None
                if revenues is not None and len(revenues) >= 2:
                    latest_rev = float(revenues.iloc[0])
                    prev_rev = float(revenues.iloc[1])
                    if prev_rev != 0:
                        rev_yoy = (latest_rev - prev_rev) / prev_rev
                        rev_yoy_meta = {
                            "method": "yfinance_annual_income_stmt",
                            "latestRevenue": latest_rev,
                            "compareRevenue": prev_rev,
                            "source": "yfinance income_stmt"
                        }
        except Exception:
            pass

        return {
            "sector": _safe_get(info, "sector"),
            "industry": _safe_get(info, "industry"),
            "marketCap": market_cap,
            "pe": _to_float(pe),
            "ps": _to_float(ps),
            "pb": _to_float(pb),
            "roe": _to_float(roe),
            "roa": _to_float(roa),
            "operatingMargin": _to_float(operating_margin),
            "profitMargin": _to_float(profit_margin),
            "grossMargin": _to_float(gross_margin),
            "evToEbitda": _to_float(ev_to_ebitda),
            "evToRevenue": _to_float(ev_to_revenue),
            "dividendYield": _to_float(dividend_yield),
            "beta": _to_float(beta),
            "quarterlyEarningsGrowth": _to_float(earnings_growth),
            "quarterlyRevenueGrowth": _to_float(revenue_growth),
            "close": _to_float(price),
            "closeMeta": {"asOf": datetime.now().strftime("%Y-%m-%d"), "source": "yfinance info"},
            "revYoY": rev_yoy,
            "revYoYMeta": rev_yoy_meta,
            "ticker": ticker,
            "market": market,
        }

    result = await _run_sync(_fetch)
    cache_set(ck, result, ttl_sec=3600)
    return result


async def get_news_sentiment(ticker: str, market: str = "US", limit: int = 20) -> dict:
    """
    yfinance 뉴스 기반 대체 센티먼트.
    Alpha Vantage 실패/레이트리밋 시 폴백으로 사용한다.
    """
    safe_limit = max(1, min(limit, 50))
    ck = f"yf:news:{ticker}:{market}:{safe_limit}"
    cached = cache_get(ck)
    if cached is not None:
        return cached

    yf_ticker = _yf_ticker_suffix(ticker, market)

    def _fetch():
        t = yf.Ticker(yf_ticker)
        try:
            raw = t.news
            if raw is None:
                raw = []
        except Exception:
            raw = []
        if not raw:
            try:
                # 일부 버전에서는 get_news()만 동작
                raw = t.get_news()
            except Exception:
                raw = []
        return raw or []

    raw_items = await _run_sync(_fetch)

    if not raw_items:
        result = {
            "ticker": ticker,
            "market": market,
            "positiveRatio": None,
            "headlines": [],
            "items": [],
            "source": "yfinance news",
        }
        cache_set(ck, result, ttl_sec=180)
        return result

    items = []
    pos_count = 0
    sentiment_count = 0

    for raw in raw_items[:safe_limit]:
        item = _normalize_news_item(raw)
        title = item.get("title")
        if not isinstance(title, str) or not title.strip():
            continue

        sentiment = _headline_sentiment(title)
        if sentiment is not None:
            sentiment_count += 1
            if sentiment.get("label") == "positive":
                pos_count += 1

        items.append({
            "title": title.strip(),
            "url": _extract_news_url(item),
            "source": item.get("provider") or item.get("publisher"),
            "publishedAt": _extract_news_published_at(item),
            "sentiment": sentiment,
        })

    headlines = [it["title"] for it in items[:10]]
    positive_ratio = (pos_count / sentiment_count) if sentiment_count > 0 else None

    result = {
        "ticker": ticker,
        "market": market,
        "positiveRatio": positive_ratio,
        "headlines": headlines,
        "items": items[:10],
        "source": "yfinance news",
    }
    cache_set(ck, result, ttl_sec=300)
    return result


async def get_obv(ticker: str, market: str = "US") -> dict:
    """
    OBV (On-Balance Volume) - yfinance OHLCV에서 직접 계산
    """
    ck = f"yf:obv:{ticker}:{market}"
    cached = cache_get(ck)
    if cached is not None:
        return cached

    yf_ticker = _yf_ticker_suffix(ticker, market)

    def _fetch():
        df = yf.download(yf_ticker, period="3mo", auto_adjust=True, progress=False)
        return df

    df = await _run_sync(_fetch)

    if df is None or df.empty:
        return {"ticker": ticker, "market": market, "obv": None, "obvTrend": None, "obvMomentum": None}

    if isinstance(df.columns, pd.MultiIndex):
        df.columns = df.columns.get_level_values(0)

    df = df.reset_index()
    col_map = {c: c.lower().strip() for c in df.columns}
    df.rename(columns=col_map, inplace=True)
    df = df.sort_values("date")

    if "close" not in df.columns or "volume" not in df.columns:
        return {"ticker": ticker, "market": market, "obv": None, "obvTrend": None, "obvMomentum": None}

    # OBV 계산
    closes = df["close"].values
    volumes = df["volume"].values
    obv_values = [0.0]
    for i in range(1, len(closes)):
        if closes[i] > closes[i-1]:
            obv_values.append(obv_values[-1] + volumes[i])
        elif closes[i] < closes[i-1]:
            obv_values.append(obv_values[-1] - volumes[i])
        else:
            obv_values.append(obv_values[-1])

    if len(obv_values) < 5:
        return {"ticker": ticker, "market": market, "obv": None, "obvTrend": None, "obvMomentum": None}

    latest_obv = obv_values[-1]
    obv_5d_ago = obv_values[-min(5, len(obv_values))]

    obv_change = (latest_obv - obv_5d_ago) / abs(obv_5d_ago) if obv_5d_ago != 0 else 0
    if obv_change > 0.05:
        trend = "RISING"
    elif obv_change < -0.05:
        trend = "FALLING"
    else:
        trend = "NEUTRAL"

    result = {
        "ticker": ticker,
        "market": market,
        "obv": latest_obv,
        "obvTrend": trend,
        "obvMomentum": obv_change,
        "asOf": df["date"].iloc[-1].strftime("%Y-%m-%d") if hasattr(df["date"].iloc[-1], "strftime") else str(df["date"].iloc[-1])[:10],
        "source": "yfinance OBV (자체 계산)"
    }
    cache_set(ck, result, ttl_sec=300)
    return result


async def get_market_env() -> dict:
    """
    시장 환경 지표: Treasury Yield (^TNX), SPY 변동성
    """
    ck = "yf:market_env"
    cached = cache_get(ck)
    if cached is not None:
        return cached

    env_data = {}

    # 1) 10년 국채 수익률 (^TNX)
    try:
        def _fetch_tnx():
            df = yf.download("^TNX", period="5d", auto_adjust=True, progress=False)
            return df

        tnx_df = await _run_sync(_fetch_tnx)
        if tnx_df is not None and not tnx_df.empty:
            if isinstance(tnx_df.columns, pd.MultiIndex):
                tnx_df.columns = tnx_df.columns.get_level_values(0)
            tnx_df = tnx_df.reset_index()
            col_map = {c: c.lower().strip() for c in tnx_df.columns}
            tnx_df.rename(columns=col_map, inplace=True)
            if "close" in tnx_df.columns:
                latest_yield = float(tnx_df["close"].iloc[-1])
                env_data["treasuryYield10Y"] = latest_yield
                date_val = tnx_df["date"].iloc[-1]
                env_data["treasuryYieldDate"] = date_val.strftime("%Y-%m-%d") if hasattr(date_val, "strftime") else str(date_val)[:10]
    except Exception as e:
        logger.warning(f"Failed to fetch ^TNX: {e}")
        env_data["treasuryYield10Y"] = None

    # 2) SPY 20일 변동성
    try:
        spy_prices = await get_prices("SPY", "US", 30)
        pts = spy_prices.get("points") or []
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
    except Exception as e:
        logger.warning(f"Failed to compute SPY volatility: {e}")
        env_data["spyVolatility20d"] = None
        env_data["fearLevel"] = None

    result = {
        "treasuryYield10Y": env_data.get("treasuryYield10Y"),
        "treasuryYieldDate": env_data.get("treasuryYieldDate"),
        "spyVolatility20d": env_data.get("spyVolatility20d"),
        "fearLevel": env_data.get("fearLevel"),
        "source": "yfinance ^TNX + SPY volatility"
    }
    cache_set(ck, result, ttl_sec=3600)
    return result
