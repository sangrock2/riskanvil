"""
RAG 벡터 스토어 — ChromaDB 기반 뉴스/문서 인덱싱 및 시맨틱 검색

ChromaDB 내장 ONNX 임베딩(default embedding function)을 사용하여
torch 없이도 가볍게 동작합니다.
"""
import logging
from typing import Optional
from pathlib import Path

from config import RAG_DATA_DIR, RAG_ENABLED

logger = logging.getLogger("app")

_client = None
_collection = None


def _get_collection():
    """Lazy-init ChromaDB client and collection."""
    global _client, _collection

    if _collection is not None:
        return _collection

    if not RAG_ENABLED:
        return None

    try:
        import chromadb

        RAG_DATA_DIR.mkdir(parents=True, exist_ok=True)
        _client = chromadb.PersistentClient(path=str(RAG_DATA_DIR))
        _collection = _client.get_or_create_collection(
            name="stock_news",
            metadata={"hnsw:space": "cosine"},
        )
        logger.info("ChromaDB collection initialized at %s (%d docs)", RAG_DATA_DIR, _collection.count())
        return _collection
    except Exception as e:
        logger.warning("Failed to initialize ChromaDB: %s", e)
        return None


def index_articles(ticker: str, articles: list[dict]) -> int:
    """Index news articles into ChromaDB.

    Each article dict should have: url, title, content, (optional) date, source, sentiment.
    Uses url as the document ID to prevent duplicates.

    Returns the number of newly indexed documents.
    """
    col = _get_collection()
    if col is None:
        return 0

    added = 0
    ids = []
    documents = []
    metadatas = []

    for art in articles:
        url = art.get("url", "")
        title = art.get("title", "")
        content = art.get("content", "")
        text = f"{title}\n\n{content}".strip()

        if not text or not url:
            continue

        doc_id = f"{ticker}:{url}"

        ids.append(doc_id)
        documents.append(text[:8000])  # ChromaDB max doc size
        metadatas.append({
            "ticker": ticker.upper(),
            "url": url,
            "title": title[:500],
            "date": art.get("date", ""),
            "source": art.get("source", ""),
            "sentiment": art.get("sentiment", ""),
        })

    if not ids:
        return 0

    # Upsert to avoid duplicates
    try:
        col.upsert(ids=ids, documents=documents, metadatas=metadatas)
        added = len(ids)
        logger.info("Indexed %d articles for %s", added, ticker)
    except Exception as e:
        logger.error("Failed to index articles for %s: %s", ticker, e)

    return added


def search(ticker: str, query: str, n_results: int = 5) -> list[dict]:
    """Semantic search for articles related to ticker + query.

    Returns list of {title, content, url, date, source, sentiment, distance}.
    """
    col = _get_collection()
    if col is None:
        return []

    try:
        results = col.query(
            query_texts=[query],
            n_results=n_results,
            where={"ticker": ticker.upper()},
        )
    except Exception as e:
        logger.warning("RAG search failed for %s: %s", ticker, e)
        return []

    items = []
    if results and results.get("documents"):
        docs = results["documents"][0]
        metas = results["metadatas"][0] if results.get("metadatas") else [{}] * len(docs)
        distances = results["distances"][0] if results.get("distances") else [0.0] * len(docs)

        for doc, meta, dist in zip(docs, metas, distances):
            items.append({
                "title": meta.get("title", ""),
                "content": doc[:1000],  # truncate for response
                "url": meta.get("url", ""),
                "date": meta.get("date", ""),
                "source": meta.get("source", ""),
                "sentiment": meta.get("sentiment", ""),
                "distance": round(dist, 4),
            })

    return items


def count(ticker: Optional[str] = None) -> int:
    """Return the number of indexed documents, optionally filtered by ticker."""
    col = _get_collection()
    if col is None:
        return 0

    try:
        if ticker:
            result = col.get(where={"ticker": ticker.upper()})
            return len(result["ids"]) if result else 0
        return col.count()
    except Exception as e:
        logger.warning("RAG count failed: %s", e)
        return 0
