"""
Alpha Vantage 클라이언트 - 종목 검색 + 뉴스 감성 분석만 유지
"""
import re
import logging
from datetime import datetime, timezone

from fastapi import HTTPException

from cache import alpha_get

logger = logging.getLogger("app")


def _to_iso8601_from_time_published(tp: str | None):
    """AlphaVantage time_published -> ISO8601"""
    if not tp:
        return None
    s = str(tp).strip()
    if re.fullmatch(r"\d{8}T\d{6}", s):
        dt = datetime.strptime(s, "%Y%m%dT%H%M%S").replace(tzinfo=timezone.utc)
        return dt.isoformat().replace("+00:00", "Z")
    return None


def _map_av_label_to_sentiment(label: str | None):
    """AlphaVantage sentiment label -> 표준 sentiment"""
    if not label:
        return None
    s = str(label).lower()
    if "bullish" in s:
        score = 0.6 if "somewhat" in s else 1.0
        return {"label": "positive", "score": score}
    if "bearish" in s:
        score = -0.6 if "somewhat" in s else -1.0
        return {"label": "negative", "score": score}
    if "neutral" in s:
        return {"label": "neutral", "score": 0.0}
    return None


async def symbol_search(keywords: str, market: str = "US", test: bool = False) -> list:
    """Alpha Vantage SYMBOL_SEARCH"""
    data = await alpha_get({"function": "SYMBOL_SEARCH", "keywords": keywords}, ttl_sec=300, test=test)
    matches = data.get("bestMatches") or []

    def ok(m):
        region = (m.get("4. region") or "").upper()
        if market.upper() == "US":
            return "UNITED STATES" in region
        if market.upper() == "KR":
            return "KOREA" in region
        return True

    items = []
    for m in matches:
        if not ok(m):
            continue
        items.append({
            "symbol": m.get("1. symbol"),
            "name": m.get("2. name"),
            "type": m.get("3. type"),
            "region": m.get("4. region"),
            "currency": m.get("8. currency"),
            "matchScore": float(m.get("9. matchScore") or 0.0),
        })
    return items[:10]


async def get_news_sentiment(ticker: str, market: str = "US", limit: int = 20, test: bool = False) -> dict:
    """Alpha Vantage NEWS_SENTIMENT"""
    data = await alpha_get(
        {"function": "NEWS_SENTIMENT", "tickers": ticker, "sort": "LATEST", "limit": str(max(1, min(limit, 50)))},
        ttl_sec=300,
        test=test
    )

    feed = data.get("feed") or []
    if not feed:
        return {"ticker": ticker, "market": market, "positiveRatio": None, "headlines": [], "items": []}

    pos = 0
    total = 0
    headlines = []
    items = []

    for it in feed:
        lab = (it.get("overall_sentiment_label") or "").lower()
        if lab:
            total += 1
            if "bullish" in lab:
                pos += 1

        title = it.get("title") or ""
        url = it.get("url")
        source = it.get("source")
        time_published = it.get("time_published")
        av_label = it.get("overall_sentiment_label")

        if title:
            headlines.append(title)

        published_at = _to_iso8601_from_time_published(time_published)
        sentiment = _map_av_label_to_sentiment(av_label)

        items.append({
            "title": title or None,
            "url": url,
            "source": source,
            "publishedAt": published_at,
            "sentiment": sentiment,
            "avTimePublished": time_published,
            "avSentimentLabel": av_label,
        })

    positive_ratio = (pos / total) if total > 0 else None

    return {
        "ticker": ticker,
        "market": market,
        "positiveRatio": positive_ratio,
        "headlines": headlines[:10],
        "items": items[:10],
    }
