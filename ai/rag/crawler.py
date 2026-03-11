"""
뉴스 크롤러 — URL에서 기사 본문 추출

httpx + BeautifulSoup로 HTML 페이지에서 본문을 추출합니다.
동시 요청을 Semaphore로 제한하여 서버 부하를 방지합니다.
"""
import asyncio
import ipaddress
import logging
import socket
from typing import Optional
from urllib.parse import urljoin, urlparse

import httpx
from bs4 import BeautifulSoup

logger = logging.getLogger("app")

# Reusable async client
_client: Optional[httpx.AsyncClient] = None

_HEADERS = {
    "User-Agent": "Mozilla/5.0 (compatible; StockAI-RAG/1.0)",
    "Accept": "text/html,application/xhtml+xml",
}
_MAX_REDIRECTS = 3
_BLOCKED_HOSTNAMES = {"localhost", "127.0.0.1", "::1"}


def _get_client() -> httpx.AsyncClient:
    global _client
    if _client is None or _client.is_closed:
        _client = httpx.AsyncClient(
            timeout=15.0,
            follow_redirects=False,
            headers=_HEADERS,
        )
    return _client


def _is_blocked_ip(ip_text: str) -> bool:
    ip_obj = ipaddress.ip_address(ip_text)
    return (
        ip_obj.is_private
        or ip_obj.is_loopback
        or ip_obj.is_link_local
        or ip_obj.is_multicast
        or ip_obj.is_reserved
        or ip_obj.is_unspecified
    )


def _validate_target_url(url: str) -> None:
    parsed = urlparse(url)
    if parsed.scheme not in {"http", "https"}:
        raise ValueError("unsupported_url_scheme")
    if parsed.username or parsed.password:
        raise ValueError("url_credentials_not_allowed")

    host = (parsed.hostname or "").strip().lower()
    if not host:
        raise ValueError("missing_url_host")
    if host in _BLOCKED_HOSTNAMES or host.endswith(".local"):
        raise ValueError("blocked_host")

    # Direct IP literal check
    try:
        ip_literal = ipaddress.ip_address(host)
        if _is_blocked_ip(str(ip_literal)):
            raise ValueError("blocked_ip")
        return
    except ValueError:
        # not an IP literal -> resolve DNS below
        pass

    # DNS resolution check (fail-closed)
    try:
        infos = socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)
    except Exception as ex:
        raise ValueError("dns_resolution_failed") from ex

    if not infos:
        raise ValueError("dns_resolution_empty")

    for info in infos:
        ip = info[4][0]
        if _is_blocked_ip(ip):
            raise ValueError("blocked_resolved_ip")


async def _safe_get(url: str) -> httpx.Response:
    current = url
    client = _get_client()

    for _ in range(_MAX_REDIRECTS + 1):
        _validate_target_url(current)
        resp = await client.get(current)

        if 300 <= resp.status_code < 400:
            location = resp.headers.get("Location")
            if not location:
                resp.raise_for_status()
            current = urljoin(current, location)
            continue

        resp.raise_for_status()
        return resp

    raise ValueError("too_many_redirects")


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
        resp = await _safe_get(url)

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
