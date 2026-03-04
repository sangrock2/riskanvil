"""
RAG (검색 증강 생성) 라우터

- POST /rag/index   — 뉴스 크롤링 + 임베딩 인덱싱
- GET  /rag/search  — 시맨틱 검색
- GET  /rag/status  — 인덱싱 현황 조회
"""
import logging
from typing import Optional

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel

from config import RAG_ENABLED
from rag import store, crawler

logger = logging.getLogger("app")
router = APIRouter(prefix="/rag", tags=["rag"])


class IndexRequest(BaseModel):
    ticker: str
    articles: list[dict]  # [{url, title, date?, source?, sentiment?}]
    crawl: bool = True    # whether to crawl URLs for full content


class IndexResponse(BaseModel):
    ticker: str
    indexed: int
    total: int


class SearchResult(BaseModel):
    title: str
    content: str
    url: str
    date: str
    source: str
    sentiment: str
    distance: float


@router.post("/index", response_model=IndexResponse)
async def index_articles(req: IndexRequest):
    """Crawl news articles and index them into the vector store."""
    if not RAG_ENABLED:
        raise HTTPException(503, "RAG is disabled")

    ticker = req.ticker.strip().upper()
    if not ticker:
        raise HTTPException(400, "ticker required")

    articles = req.articles
    if not articles:
        raise HTTPException(400, "No articles provided")

    # Optionally crawl URLs for full content
    if req.crawl:
        articles = await crawler.crawl_batch(articles, max_concurrent=3)

    indexed = store.index_articles(ticker, articles)
    total = store.count(ticker)

    logger.info("RAG index: %s — indexed %d, total %d", ticker, indexed, total)

    return IndexResponse(ticker=ticker, indexed=indexed, total=total)


@router.get("/search")
async def search_articles(
    ticker: str = Query(..., description="Stock ticker"),
    query: str = Query(..., description="Search query"),
    n: int = Query(5, ge=1, le=20, description="Number of results"),
):
    """Semantic search for indexed articles."""
    if not RAG_ENABLED:
        raise HTTPException(503, "RAG is disabled")

    ticker = ticker.strip().upper()
    if not ticker or not query.strip():
        raise HTTPException(400, "ticker and query required")

    results = store.search(ticker, query.strip(), n_results=n)
    return {"ticker": ticker, "query": query, "results": results}


@router.get("/status")
async def rag_status(ticker: Optional[str] = Query(None)):
    """Check RAG indexing status."""
    return {
        "enabled": RAG_ENABLED,
        "totalDocuments": store.count(),
        "tickerDocuments": store.count(ticker) if ticker else None,
        "ticker": ticker,
    }
