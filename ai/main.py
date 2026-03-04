"""
Stock AI - FastAPI 메인 엔트리포인트
라우터 등록, 미들웨어, 앱 초기화만 담당
"""
import asyncio
import os
import time
import uuid
import logging
from contextlib import asynccontextmanager

import sentry_sdk
from fastapi import FastAPI, Request

from request_id import setup_logging, set_request_id, reset_request_id
from config import FINNHUB_KEY, RAG_ENABLED
from cache import close_alpha_client

# ── Sentry 초기화 (SENTRY_DSN 환경변수가 설정된 경우에만 활성화) ──
_sentry_dsn = os.getenv("SENTRY_DSN", "")
if _sentry_dsn:
    sentry_sdk.init(
        dsn=_sentry_dsn,
        environment=os.getenv("ENVIRONMENT", "development"),
        traces_sample_rate=float(os.getenv("SENTRY_TRACES_SAMPLE_RATE", "0.1")),
        send_default_pii=False,
    )
from data_sources.finnhub_ws import finnhub_ws
from routes import market_data, intelligence, backtest, realtime, screener, correlation, monte_carlo, chatbot, prices, dividend, rag, etf, portfolio_optimize, similarity, earnings, portfolio_risk

# ── 로깅 ──
setup_logging()
logger = logging.getLogger("app")


# ── Lifespan ──

@asynccontextmanager
async def lifespan(app: FastAPI):
    # startup
    if FINNHUB_KEY:
        asyncio.create_task(finnhub_ws.connect())
        logger.info("Finnhub WebSocket connection started")
    else:
        logger.info("FINNHUB_API_KEY not set, skipping real-time quotes")

    if RAG_ENABLED:
        logger.info("RAG system enabled")
    else:
        logger.info("RAG system disabled")
    yield
    # shutdown
    await finnhub_ws.disconnect()
    await close_alpha_client()
    logger.info("Finnhub WebSocket disconnected")


# ── FastAPI App ──
app = FastAPI(title="Stock AI", lifespan=lifespan)


# ── 미들웨어 ──

@app.middleware("http")
async def request_id_middleware(request: Request, call_next):
    rid = request.headers.get("X-Request-Id")
    if not rid or not rid.strip():
        rid = str(uuid.uuid4())

    token = set_request_id(rid)
    request.state.request_id = rid

    start = time.perf_counter()
    response = None
    try:
        response = await call_next(request)
        return response
    finally:
        dur_ms = (time.perf_counter() - start) * 1000.0
        status = getattr(response, "status_code", 500)

        if response is not None:
            response.headers["X-Request-Id"] = rid
            prev = response.headers.get("Access-Control-Expose-Headers", "")
            expose = "X-Request-Id"
            if expose not in prev:
                response.headers["Access-Control-Expose-Headers"] = (prev + ", " + expose).strip(", ").strip()

        logger.info("%s %s -> %s (%.1fms)", request.method, request.url.path, status, dur_ms)
        reset_request_id(token)


# ── 라우터 등록 ──

app.include_router(market_data.router)
app.include_router(intelligence.router)
app.include_router(backtest.router)
app.include_router(realtime.router)
app.include_router(screener.router)
app.include_router(correlation.router)
app.include_router(monte_carlo.router)
app.include_router(chatbot.router)
app.include_router(prices.router)
app.include_router(dividend.router)
app.include_router(rag.router)
app.include_router(etf.router)
app.include_router(portfolio_optimize.router)
app.include_router(similarity.router)
app.include_router(earnings.router)
app.include_router(portfolio_risk.router)


# ── Health Check ──

@app.get("/health")
def health():
    return {"status": "ok"}


