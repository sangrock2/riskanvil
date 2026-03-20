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
from fastapi.responses import JSONResponse, Response
from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest

from request_id import setup_logging, set_request_id, reset_request_id
from config import (
    FINNHUB_KEY,
    INTERNAL_SERVICE_TOKEN,
    INTERNAL_SERVICE_TOKEN_HEADER,
    RAG_ENABLED,
    is_default_internal_service_token,
    is_internal_service_request_authorized,
)
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

AI_HTTP_REQUESTS = Counter(
    "ai_http_requests_total",
    "AI service HTTP requests",
    ("method", "path", "status"),
)
AI_HTTP_REQUEST_LATENCY = Histogram(
    "ai_http_request_latency_seconds",
    "AI service HTTP request latency",
    ("method", "path"),
)


# ── Lifespan ──

@asynccontextmanager
async def lifespan(app: FastAPI):
    # startup
    environment = os.getenv("ENVIRONMENT", "development").lower()
    configured_token = INTERNAL_SERVICE_TOKEN.strip()
    if not configured_token:
        raise RuntimeError("AI_INTERNAL_SERVICE_TOKEN must be set before startup.")
    if environment in {"prod", "production", "staging"} and is_default_internal_service_token():
        raise RuntimeError("AI_INTERNAL_SERVICE_TOKEN must be a strong non-default value in production-like environments.")

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
        if request.url.path not in {"/health", "/metrics"}:
            presented_token = request.headers.get(INTERNAL_SERVICE_TOKEN_HEADER, "")
            if not is_internal_service_request_authorized(presented_token):
                logger.warning("Rejected unauthorized AI HTTP request: %s", request.url.path)
                response = JSONResponse(status_code=403, content={"detail": "forbidden"})
                return response

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

        route = request.scope.get("route")
        path_label = getattr(route, "path", request.url.path)
        AI_HTTP_REQUESTS.labels(request.method, path_label, str(status)).inc()
        AI_HTTP_REQUEST_LATENCY.labels(request.method, path_label).observe(dur_ms / 1000.0)

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


@app.get("/metrics")
def metrics():
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


