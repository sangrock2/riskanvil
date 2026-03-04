"""
뉴스 크롤러 — URL에서 기사 본문 추출

httpx + BeautifulSoup로 HTML 페이지에서 본문을 추출합니다.
동시 요청을 Semaphore로 제한하여 서버 부하를 방지합니다.
"""
import asyncio
import logging
from typing import Optional

import httpx
from bs4 import BeautifulSoup

logger = logging.getLogger("app")

# Reusable async client
_client: Optional[httpx.AsyncClient] = None

_HEADERS = {
    "User-Agent": "Mozilla/5.0 (compatible; StockAI-RAG/1.0)",
    "Accept": "text/html,application/xhtml+xml",
}


def _get_client() -> httpx.AsyncClient:
    global _client
    if _client is None or _client.is_closed:
        _client = httpx.AsyncClient(
            timeout=15.0,
            follow_redirects=True,
            headers=_HEADERS,
        )
    return _client


def _extract_body(html: str) -> str:
    """Extract article body text from HTML using common patterns."""
    soup = BeautifulSoup(html, "html.parser")

    # Remove noise elements
    for tag in soup.find_all(["script", "style", "nav", "footer", "header", "aside", "iframe"]):
        tag.decompose()

    # Try common article selectors
    selectors = [
        "article",
        '[role="main"]',
        ".article-body",
        ".article-content",
        ".post-content",
        ".entry-content",
        "#article-body",
        ".story-body",
        "main",
    ]

    for sel in selectors:
        el = soup.select_one(sel)
        if el:
            text = el.get_text(separator="\n", strip=True)
            if len(text) > 100:
                return text[:5000]

    # Fallback: concatenate all <p> tags
    paragraphs = soup.find_all("p")
    text = "\n".join(p.get_text(strip=True) for p in paragraphs if len(p.get_text(strip=True)) > 30)

    return text[:5000] if text else ""


async def crawl_article(url: str) -> Optional[dict]:
    """Crawl a single article URL and extract its body text.

    Returns {url, title, content} or None on failure.
    """
    try:
        client = _get_client()
        resp = await client.get(url)
        resp.raise_for_status()

        html = resp.text
        soup = BeautifulSoup(html, "html.parser")

        title_tag = soup.find("title")
        title = title_tag.get_text(strip=True) if title_tag else ""

        content = _extract_body(html)

        if not content:
            logger.debug("No content extracted from %s", url)
            return {"url": url, "title": title, "content": ""}

        return {"url": url, "title": title, "content": content}

    except Exception as e:
        logger.warning("Failed to crawl %s: %s", url, e)
        return None


async def crawl_batch(items: list[dict], max_concurrent: int = 3) -> list[dict]:
    """Crawl a batch of news items concurrently.

    Each item should have at least {url, title}.
    Returns list of enriched items with 'content' field added.
    Respects max_concurrent limit via Semaphore.
    """
    sem = asyncio.Semaphore(max_concurrent)

    async def _crawl_one(item: dict) -> dict:
        url = item.get("url", "")
        if not url:
            return {**item, "content": ""}

        async with sem:
            result = await crawl_article(url)

        if result and result.get("content"):
            return {**item, "content": result["content"]}

        # Fallback: title-only
        return {**item, "content": item.get("title", "")}

    tasks = [_crawl_one(it) for it in items]
    return await asyncio.gather(*tasks)
