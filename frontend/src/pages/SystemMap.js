import { useEffect, useMemo, useState } from "react";
import styles from "../css/SystemMap.module.css";
import rawMapData from "../data/systemMapData.json";
import {
  safeId,
  buildFileTree,
  filterTree,
  pathStartsWith,
  replaceTemplate,
  truncateText,
  cloneJson,
  diffTopLevel,
  deriveAction,
} from "../utils/systemMapUtils";

const LAYER_ORDER = [
  "frontend",
  "backend-api",
  "backend-service",
  "backend-data",
  "ai-route",
  "external",
];

const LAYER_LABEL = {
  frontend: "Frontend",
  "backend-api": "Backend Controller",
  "backend-service": "Backend Service",
  "backend-data": "Data / DB / Cache",
  "ai-route": "AI Routes",
  external: "External",
};

const NODE_W = 150;
const NODE_H = 36;
const LAYER_START_X = 66;
const LAYER_GAP_X = 212;
const NODE_START_Y = 82;
const NODE_GAP_Y = 70;
const LAYER_BADGE_Y = 20;
const LAYER_BADGE_W = Math.round(NODE_W * 1.25);
const LAYER_BADGE_H = Math.round(NODE_H * 0.95);
const LAYER_BADGE_OFFSET_X = Math.round((LAYER_BADGE_W - NODE_W) / 2);
const EDGE_NODE_GAP = 6;
const EDGE_LABEL_GAP_FROM_FROM_NODE = 7;
const EDGE_LABEL_VSTEP = 11;

function assignPositions(nodes) {
  const grouped = LAYER_ORDER.reduce((acc, layer) => {
    acc[layer] = [];
    return acc;
  }, {});

  for (const n of nodes) {
    if (!grouped[n.layer]) grouped[n.layer] = [];
    grouped[n.layer].push(n);
  }

  const positioned = [];
  let maxInLayer = 0;
  LAYER_ORDER.forEach((layer, layerIdx) => {
    const list = grouped[layer] || [];
    maxInLayer = Math.max(maxInLayer, list.length);
    list.forEach((n, idx) => {
      positioned.push({
        ...n,
        x: LAYER_START_X + layerIdx * LAYER_GAP_X,
        y: NODE_START_Y + idx * NODE_GAP_Y,
      });
    });
  });

  return {
    nodes: positioned,
    width: LAYER_START_X + (LAYER_ORDER.length - 1) * LAYER_GAP_X + NODE_W + 90,
    height: NODE_START_Y + Math.max(1, maxInLayer) * NODE_GAP_Y + 130,
  };
}

function buildScenarioFrames(scenarioId, rawInput, stepCount) {
  const baseInput = cloneJson(rawInput);
  const totalSteps = Math.max(0, Number(stepCount) || 0);
  if (totalSteps === 0) return [];

  const frames = [];
  let state = cloneJson(baseInput);

  function push(nextState, note) {
    const input = cloneJson(state);
    const output = cloneJson(nextState);
    frames.push({
      input,
      output,
      note,
      delta: diffTopLevel(input, output),
    });
    state = output;
  }

  switch (scenarioId) {
    case "insights_flow": {
      const ticker = String(baseInput.ticker || "AAPL").toUpperCase();
      const market = String(baseInput.market || "US").toUpperCase();
      const days = Math.max(5, Number(baseInput.days) || 90);
      const newsLimit = Math.max(5, Number(baseInput.newsLimit) || 20);
      const refresh = Boolean(baseInput.refresh);
      const score = Math.min(95, Math.max(35, 74 + (refresh ? 1 : 0) - (days > 120 ? 2 : 0)));
      const confidence = Number((0.62 + Math.min(days, 180) / 900).toFixed(2));
      const latestPrice = Number((108 + ticker.length * 13.2 + days * 0.08).toFixed(2));

      push(
        {
          stage: "frontend.input",
          request: { ticker, market, days, newsLimit, refresh },
          trace: ["ui:analyze_button_click"],
        },
        "사용자 입력을 표준 요청 포맷으로 정규화합니다."
      );
      push(
        {
          ...state,
          stage: "frontend.query",
          query: {
            key: ["insights", ticker, market, days, newsLimit, refresh],
            cacheHit: !refresh,
            staleTimeMs: 30000,
          },
        },
        "React Query가 캐시 키를 만들고 API 호출 여부를 결정합니다."
      );
      push(
        {
          ...state,
          stage: "backend.controller",
          backend: {
            endpoint: "POST /api/market/insights",
            controller: "MarketController",
            validation: "passed",
          },
        },
        "MarketController가 요청을 검증하고 서비스 계층으로 전달합니다."
      );
      push(
        {
          ...state,
          stage: "backend.service",
          orchestration: {
            services: ["AnalysisService", "PriceService", "MarketCacheService"],
            cachePolicy: refresh ? "bypass" : "prefer-cache",
            aiCallRequired: true,
          },
        },
        "서비스 계층에서 캐시 정책과 AI 호출 계획을 오케스트레이션합니다."
      );
      push(
        {
          ...state,
          stage: "backend.ai-client",
          aiRequest: {
            gateway: "FastAPI",
            route: "/intelligence/insights",
            payloadBytes: 900 + days * 3 + newsLimit * 12,
          },
        },
        "AiClient가 FastAPI 라우트로 분석 요청을 전달합니다."
      );
      push(
        {
          ...state,
          stage: "ai.intelligence",
          features: {
            momentum: 0.68,
            valuation: 0.59,
            risk: 0.41,
            newsSentiment: 0.64,
          },
          model: { name: "intel-score-v3", version: "3.2.1" },
        },
        "intelligence 라우트에서 특징량을 계산하고 점수 모델을 실행합니다."
      );
      push(
        {
          ...state,
          stage: "ai.market-data",
          marketData: {
            providers: { yfinance: "ok", alphaVantage: "ok" },
            candles: days,
            mergedRows: days,
            latestPrice,
          },
        },
        "외부 데이터 공급자 결과를 병합해 분석용 데이터셋을 구성합니다."
      );
      push(
        {
          ...state,
          stage: "ai.llm-enrich",
          llm: {
            provider: "openai -> ollama fallback",
            summaryReady: true,
            bulletCount: 3,
          },
        },
        "LLM이 근거 기반 자연어 요약을 생성해 결과를 보강합니다."
      );
      push(
        {
          ...state,
          stage: "frontend.render",
          response: {
            ticker,
            market,
            score,
            action: deriveAction(score),
            confidence,
            reasons: ["trend_strength", "valuation_gap", "news_sentiment"],
          },
          ui: {
            cardUpdated: true,
            chartUpdated: true,
            highlight: `${ticker} recommendation`,
          },
        },
        "최종 점수/추천/근거가 프론트 UI로 복귀해 시각화됩니다."
      );
      break;
    }
    case "portfolio_detail": {
      const portfolioId = Number(baseInput.portfolioId) || 1;
      const positions = ["AAPL", "MSFT", "NVDA"];
      push(
        {
          stage: "frontend.request",
          request: { portfolioId, endpoint: `GET /api/portfolio/${portfolioId}` },
        },
        "포트폴리오 상세 조회 요청을 생성합니다."
      );
      push(
        {
          ...state,
          stage: "backend.authz",
          backend: {
            controller: "PortfolioController",
            service: "PortfolioService",
            ownershipCheck: "passed",
          },
        },
        "사용자 권한과 포트폴리오 소유권을 확인합니다."
      );
      push(
        {
          ...state,
          stage: "db.read",
          db: {
            source: "MySQL",
            repositories: ["PortfolioRepository", "PortfolioPositionRepository"],
            positions,
          },
        },
        "DB에서 포트폴리오와 포지션 데이터를 조회합니다."
      );
      push(
        {
          ...state,
          stage: "price.batch",
          pricing: {
            service: "PriceService",
            aiRoute: "/prices/batch",
            markets: ["US"],
            tickerCount: positions.length,
          },
        },
        "시장별 티커를 묶어 가격 배치 API를 호출합니다."
      );
      push(
        {
          ...state,
          stage: "analytics.merge",
          analytics: {
            dayReturnPct: 1.42,
            monthReturnPct: 6.11,
            allocation: { tech: 0.82, cash: 0.18 },
          },
        },
        "가격/히스토리/섹터 정보를 결합해 성과 지표를 계산합니다."
      );
      push(
        {
          ...state,
          stage: "frontend.response",
          response: {
            portfolioId,
            positionCount: positions.length,
            totalValue: 142340.11,
            totalPnL: 12340.11,
          },
        },
        "집계 결과를 프론트로 반환하고 카드/차트에 반영합니다."
      );
      break;
    }
    case "watchlist_refresh": {
      const ticker = String(baseInput.ticker || "TSLA").toUpperCase();
      const market = String(baseInput.market || "US").toUpperCase();
      push(
        {
          stage: "frontend.refresh-request",
          request: { ticker, market, refresh: true },
        },
        "관심종목 강제 갱신 요청을 생성합니다."
      );
      push(
        {
          ...state,
          stage: "backend.cache-bypass",
          backend: {
            controller: "MarketController",
            cacheBypass: true,
            services: ["AnalysisService", "MarketCacheService"],
          },
        },
        "캐시 우회 모드로 분석 재계산 경로를 선택합니다."
      );
      push(
        {
          ...state,
          stage: "ai.recompute",
          ai: {
            intelligenceRoute: "/intelligence/insights",
            sources: ["yfinance", "alpha-vantage"],
            refreshed: true,
            score: 78,
          },
        },
        "최신 시세/뉴스 기반으로 인사이트를 재계산합니다."
      );
      push(
        {
          ...state,
          stage: "cache.persist",
          persistence: {
            mysqlUpdated: true,
            redisUpdated: true,
            cacheTtlSec: 180,
          },
        },
        "재계산 결과를 DB와 캐시에 반영합니다."
      );
      push(
        {
          ...state,
          stage: "frontend.re-render",
          response: {
            ticker,
            score: 78,
            action: "BUY",
            price: 248.31,
            refreshedAt: "now",
          },
        },
        "갱신된 점수와 가격 요약이 Watchlist UI에 표시됩니다."
      );
      break;
    }
    case "report_stream": {
      const ticker = String(baseInput.ticker || "NVDA").toUpperCase();
      const market = String(baseInput.market || "US").toUpperCase();
      const template = String(baseInput.template || "detailed");
      push(
        {
          stage: "frontend.sse-open",
          request: {
            endpoint: "/api/market/report/stream",
            ticker,
            market,
            template,
          },
        },
        "SSE 채널을 열고 리포트 스트림 수신을 준비합니다."
      );
      push(
        {
          ...state,
          stage: "backend.stream-registry",
          stream: {
            controller: "ReportStreamController",
            emitterRegistered: true,
            timeoutMs: 120000,
          },
        },
        "백엔드에서 장시간 스트리밍 emitter를 등록합니다."
      );
      push(
        {
          ...state,
          stage: "ai.report-generation",
          ai: {
            route: "/intelligence/report",
            provider: "openai -> ollama fallback",
            chunks: 8,
          },
        },
        "AI 리포트 토큰을 청크 단위로 생성해 스트리밍합니다."
      );
      push(
        {
          ...state,
          stage: "backend.persist",
          persistence: {
            reportHistorySaved: true,
            usageLogged: true,
          },
        },
        "완성된 리포트와 사용량 로그를 저장합니다."
      );
      push(
        {
          ...state,
          stage: "export-ready",
          export: {
            formats: ["txt", "json", "pdf"],
            downloadReady: true,
          },
        },
        "동일 리포트를 TXT/JSON/PDF로 내보낼 수 있게 준비합니다."
      );
      break;
    }
    case "realtime_ws": {
      const ticker = String(baseInput.ticker || "MSFT").toUpperCase();
      push(
        {
          stage: "frontend.ws-connect",
          ws: {
            endpoint: "/ws/quotes",
            subscribedTicker: ticker,
            status: "connected",
          },
        },
        "브라우저가 WS 연결 후 구독 메시지를 보냅니다."
      );
      push(
        {
          ...state,
          stage: "backend.ws-relay",
          relay: {
            handler: "QuoteWebSocketHandler",
            relay: "QuoteWebSocketRelay",
            upstream: "ai/realtime",
          },
        },
        "구독 이벤트를 Relay로 넘겨 상위 WS 채널을 구독합니다."
      );
      push(
        {
          ...state,
          stage: "ai.finnhub-ingest",
          tick: {
            provider: "Finnhub",
            ticker,
            price: 420.57,
            volume: 12400,
          },
        },
        "AI realtime 라우트가 체결 데이터를 수신합니다."
      );
      push(
        {
          ...state,
          stage: "backend.broadcast",
          broadcast: {
            targetSessions: 6,
            indexType: "ticker -> sessionIds",
            latencyMs: 39,
          },
        },
        "ticker 역인덱스로 구독 세션에 저지연 브로드캐스트합니다."
      );
      push(
        {
          ...state,
          stage: "frontend.patch",
          ui: {
            cardUpdated: true,
            chartUpdated: true,
            lastTickPrice: 420.57,
          },
        },
        "실시간 가격/거래량이 카드와 차트에 즉시 반영됩니다."
      );
      break;
    }
    case "backtest_execute": {
      const ticker = String(baseInput.ticker || "AAPL").toUpperCase();
      const market = String(baseInput.market || "US").toUpperCase();
      const strategy = String(baseInput.strategy || "sma_cross");
      const initialCapital = Number(baseInput.initialCapital) || 100000;
      push(
        {
          stage: "frontend.backtest-request",
          request: {
            ticker,
            market,
            strategy,
            period: { start: baseInput.start || "2024-01-01", end: baseInput.end || "2025-01-01" },
            initialCapital,
          },
        },
        "백테스트 실행 파라미터를 수집해 API 요청을 생성합니다."
      );
      push(
        {
          ...state,
          stage: "backend.backtest-controller",
          backend: {
            controller: "BacktestController",
            service: "BacktestService",
            validation: "passed",
          },
        },
        "컨트롤러가 요청을 검증하고 전략 실행 서비스로 전달합니다."
      );
      push(
        {
          ...state,
          stage: "ai.backtest-route",
          ai: {
            route: "/backtest/run",
            barsLoaded: 252,
            source: "yfinance",
          },
        },
        "AI backtest 라우트가 과거 가격 데이터를 로딩합니다."
      );
      push(
        {
          ...state,
          stage: "strategy-simulation",
          simulation: {
            trades: 21,
            winRate: 0.57,
            maxDrawdown: -0.122,
          },
        },
        "전략 시그널로 매수/매도 체결을 시뮬레이션합니다."
      );
      push(
        {
          ...state,
          stage: "metrics-calc",
          metrics: {
            totalReturnPct: 18.7,
            sharpe: 1.26,
            cagr: 0.187,
          },
        },
        "수익률/샤프/낙폭 등 핵심 성과 지표를 계산합니다."
      );
      push(
        {
          ...state,
          stage: "frontend.backtest-render",
          response: {
            ticker,
            strategy,
            equityCurvePoints: 252,
            totalReturnPct: 18.7,
          },
        },
        "결과 곡선과 성과 요약을 프론트 차트에 렌더링합니다."
      );
      break;
    }
    case "auth_refresh": {
      const email = String(baseInput.email || "demo@stock.ai");
      const rememberMe = Boolean(baseInput.rememberMe);
      push(
        {
          stage: "frontend.login-submit",
          request: { email, rememberMe, hasPassword: true },
        },
        "로그인 폼 입력값을 인증 요청으로 변환합니다."
      );
      push(
        {
          ...state,
          stage: "backend.auth-verify",
          backend: {
            controller: "AuthController",
            service: "AuthService",
            passwordVerified: true,
          },
        },
        "사용자 조회 및 비밀번호 해시 검증을 수행합니다."
      );
      push(
        {
          ...state,
          stage: "token-issued",
          tokens: {
            accessTokenTtlSec: 900,
            refreshTokenTtlSec: rememberMe ? 604800 : 86400,
            refreshTokenStored: true,
          },
        },
        "접근/갱신 토큰을 발급하고 refresh 토큰을 저장합니다."
      );
      push(
        {
          ...state,
          stage: "access-expired",
          authEvent: {
            reason: "401 from protected api",
            trigger: "frontend interceptor",
            refreshAttempted: true,
          },
        },
        "액세스 토큰 만료 시 인터셉터가 refresh 경로를 자동 호출합니다."
      );
      push(
        {
          ...state,
          stage: "token-rotated",
          tokens: {
            accessTokenTtlSec: 900,
            refreshTokenTtlSec: rememberMe ? 604800 : 86400,
            refreshTokenStored: true,
            rotated: true,
          },
          response: { retryOriginalRequest: true, authState: "authenticated" },
        },
        "토큰 재발급 후 원래 요청을 재시도하고 인증 상태를 복구합니다."
      );
      break;
    }
    case "paper_trade_order": {
      const ticker = String(baseInput.ticker || "NVDA").toUpperCase();
      const side = String(baseInput.side || "BUY").toUpperCase();
      const qty = Math.max(1, Number(baseInput.qty) || 3);
      const price = Number(baseInput.price) || 840.5;
      const accountId = Number(baseInput.accountId) || 1;
      push(
        {
          stage: "frontend.order-submit",
          order: { accountId, ticker, side, qty, price, orderType: "LIMIT" },
        },
        "주문 입력값을 Paper Trading 주문 포맷으로 정규화합니다."
      );
      push(
        {
          ...state,
          stage: "backend.validation",
          backend: {
            controller: "PaperTradingController",
            service: "PaperTradingService",
            checks: ["account", "quantity", "price", "market hours"],
            passed: true,
          },
        },
        "계좌 상태와 주문 파라미터를 검증합니다."
      );
      push(
        {
          ...state,
          stage: "risk-cash-check",
          risk: {
            buyingPowerBefore: 250000,
            requiredCash: Number((qty * price).toFixed(2)),
            allowed: true,
          },
        },
        "가용 현금/리스크 한도 검사 후 체결 가능 여부를 확정합니다."
      );
      push(
        {
          ...state,
          stage: "fill-simulated",
          execution: {
            status: "FILLED",
            fillPrice: Number((price * 0.9994).toFixed(2)),
            filledQty: qty,
            slippagePct: -0.06,
          },
        },
        "모의 체결 엔진이 체결가와 슬리피지를 계산합니다."
      );
      push(
        {
          ...state,
          stage: "db-persist",
          persistence: {
            orderSaved: true,
            positionUpdated: true,
            accountBalanceUpdated: true,
          },
        },
        "주문/포지션/계좌 잔고 변경사항을 저장합니다."
      );
      push(
        {
          ...state,
          stage: "frontend.order-result",
          response: {
            accountId,
            ticker,
            status: "FILLED",
            avgPrice: Number((price * 0.9994).toFixed(2)),
            quantity: qty,
          },
        },
        "체결 결과를 프론트에 반환해 계좌/포지션 UI를 갱신합니다."
      );
      break;
    }
    default: {
      push(
        {
          stage: "input",
          payload: baseInput,
        },
        "입력 데이터를 처리할 준비를 완료했습니다."
      );
      break;
    }
  }

  while (frames.length < totalSteps) {
    push(
      { ...state, stage: `step_${frames.length + 1}` },
      "후속 단계에서 상태를 유지합니다."
    );
  }

  return frames.slice(0, totalSteps);
}

function buildArchitecture(raw) {
  const nodes = [];
  const edges = [];
  const edgeSet = new Set();

  function addNode(node) {
    if (!nodes.some((n) => n.id === node.id)) nodes.push(node);
  }

  function addEdge(from, to, label = "") {
    const id = `${from}->${to}`;
    if (edgeSet.has(id)) return id;
    edgeSet.add(id);
    edges.push({ id, from, to, label });
    return id;
  }

  const fixedNodes = [
    { id: "fe_ui", layer: "frontend", label: "Pages & Components", tech: "React Router" },
    { id: "fe_query", layer: "frontend", label: "hooks/queries", tech: "TanStack Query" },
    { id: "fe_api", layer: "frontend", label: "api/http.js", tech: "fetch + refresh token" },
    { id: "fe_ws", layer: "frontend", label: "api/ws.js", tech: "WebSocket client" },
    { id: "be_ai_client", layer: "backend-service", label: "AiClient", tech: "WebClient + Cache + Retry" },
    { id: "be_ws_handler", layer: "backend-service", label: "QuoteWebSocketHandler", tech: "Spring WebSocket" },
    { id: "be_ws_relay", layer: "backend-service", label: "QuoteWebSocketRelay", tech: "AI relay + reconnect" },
    { id: "db_mysql", layer: "backend-data", label: "MySQL", tech: "JPA + Flyway" },
    { id: "db_redis", layer: "backend-data", label: "Redis Cache", tech: "Spring Cache" },
    { id: "ai_gateway", layer: "ai-route", label: "FastAPI main.py", tech: "Router aggregation" },
    { id: "ext_yfinance", layer: "external", label: "Yahoo Finance", tech: "yfinance" },
    { id: "ext_alpha", layer: "external", label: "Alpha Vantage", tech: "REST API" },
    { id: "ext_finnhub", layer: "external", label: "Finnhub", tech: "WebSocket stream" },
    { id: "ext_openai", layer: "external", label: "OpenAI", tech: "LLM API" },
    { id: "ext_ollama", layer: "external", label: "Ollama", tech: "Local LLM" },
  ];

  fixedNodes.forEach(addNode);

  const controllerId = new Map();
  const serviceId = new Map();
  const repoId = new Map();
  const aiRouteId = new Map();

  for (const c of raw.backend.controllers || []) {
    const id = `ctrl_${safeId(c.name)}`;
    controllerId.set(c.name, id);
    addNode({
      id,
      layer: "backend-api",
      label: c.name,
      tech: `base: ${c.basePath || "/"}`,
      file: c.file,
    });
  }

  for (const s of raw.backend.services || []) {
    const id = `svc_${safeId(s.name)}`;
    serviceId.set(s.name, id);
    addNode({
      id,
      layer: "backend-service",
      label: s.name,
      tech: "Spring @Service",
      file: s.file,
    });
  }

  for (const r of raw.backend.repositories || []) {
    const id = `repo_${safeId(r.name)}`;
    repoId.set(r.name, id);
    addNode({
      id,
      layer: "backend-data",
      label: r.name,
      tech: "JPA Repository",
      file: r.file,
    });
  }

  for (const ar of raw.ai.routes || []) {
    const id = `ai_route_${safeId(ar.module)}`;
    aiRouteId.set(ar.module, id);
    addNode({
      id,
      layer: "ai-route",
      label: `routes/${ar.module}.py`,
      tech: `${ar.endpoints.length} endpoints`,
      file: ar.file,
    });
  }

  addEdge("fe_ui", "fe_query", "UI trigger");
  addEdge("fe_query", "fe_api", "REST fetch");
  addEdge("fe_ws", "be_ws_handler", "WS subscribe");
  addEdge("be_ws_handler", "be_ws_relay", "subscription event");
  addEdge("be_ws_relay", "ai_route_realtime", "relay ws");
  addEdge("be_ai_client", "ai_gateway", "HTTP request");
  addEdge("ai_gateway", "ai_route_market_data", "route include");
  addEdge("ai_gateway", "ai_route_intelligence", "route include");
  addEdge("ai_gateway", "ai_route_prices", "route include");
  addEdge("ai_route_market_data", "ext_yfinance", "price/fundamental");
  addEdge("ai_route_market_data", "ext_alpha", "news/symbol");
  addEdge("ai_route_realtime", "ext_finnhub", "live quote");
  addEdge("ai_route_intelligence", "ext_openai", "report/summary");
  addEdge("ai_route_intelligence", "ext_ollama", "fallback/provider");
  addEdge("svc_MarketCacheService", "db_redis", "cache read/write");
  addEdge("svc_PriceService", "db_redis", "prices cache");
  addEdge("svc_UsageService", "db_mysql", "usage_log");
  addEdge("svc_PortfolioService", "db_mysql", "portfolio tables");
  addEdge("svc_WatchlistService", "db_mysql", "watchlist tables");

  const allFrontendApiPaths = [
    ...(raw.frontend.apiModules || []).flatMap((m) => m.paths || []),
    ...(raw.frontend.hooks || []).flatMap((h) => h.paths || []),
  ];

  for (const c of raw.backend.controllers || []) {
    const ctrlNodeId = controllerId.get(c.name);
    if (!ctrlNodeId) continue;
    const usedByFrontend = allFrontendApiPaths.some((p) => pathStartsWith(c.basePath || "", p));
    if (usedByFrontend) addEdge("fe_api", ctrlNodeId, c.basePath || "/api");
    for (const dep of c.dependencies || []) {
      if (serviceId.has(dep)) addEdge(ctrlNodeId, serviceId.get(dep), "delegate");
      if (dep === "QuoteWebSocketHandler") addEdge(ctrlNodeId, "be_ws_handler", "ws");
      if (dep === "QuoteWebSocketRelay") addEdge(ctrlNodeId, "be_ws_relay", "ws relay");
    }
  }

  for (const s of raw.backend.services || []) {
    const sid = serviceId.get(s.name);
    if (!sid) continue;
    for (const dep of s.dependencies || []) {
      if (serviceId.has(dep)) addEdge(sid, serviceId.get(dep), "service call");
      if (repoId.has(dep)) addEdge(sid, repoId.get(dep), "repository");
      if (dep === "AiClient") addEdge(sid, "be_ai_client", "AI call");
      if (dep === "CacheManager") addEdge(sid, "db_redis", "cache");
    }
  }

  for (const ar of raw.ai.routes || []) {
    const rid = aiRouteId.get(ar.module);
    if (!rid) continue;
    addEdge("ai_gateway", rid, "include_router");
    const deps = ar.dependencies || [];
    if (deps.some((d) => d.includes("yfinance_client"))) addEdge(rid, "ext_yfinance", "source");
    if (deps.some((d) => d.includes("alpha_client"))) addEdge(rid, "ext_alpha", "source");
    if (deps.some((d) => d.includes("finnhub_ws"))) addEdge(rid, "ext_finnhub", "source");
    if (deps.some((d) => d.includes("openai_client"))) addEdge(rid, "ext_openai", "llm");
    if (deps.some((d) => d.includes("ollama_client"))) addEdge(rid, "ext_ollama", "llm");
  }

  for (const r of raw.backend.repositories || []) {
    addEdge(`repo_${safeId(r.name)}`, "db_mysql", "JPA");
  }

  const positioned = assignPositions(nodes);
  return {
    ...positioned,
    edges,
    index: { controllerId, serviceId, repoId, aiRouteId },
  };
}

function edgePath(from, to) {
  const sx = from.x + NODE_W + EDGE_NODE_GAP;
  const sy = from.y + NODE_H / 2;
  const tx = to.x - EDGE_NODE_GAP;
  const ty = to.y + NODE_H / 2;
  const dx = Math.max(40, Math.abs(tx - sx) * 0.35);
  const c1x = sx + dx;
  const c2x = tx - dx;
  return `M ${sx} ${sy} C ${c1x} ${sy}, ${c2x} ${ty}, ${tx} ${ty}`;
}

function buildEdgeLabelPositions(nodes, edges) {
  const nodeById = new Map(nodes.map((n) => [n.id, n]));
  const layerNodes = new Map();
  for (const n of nodes) {
    if (!layerNodes.has(n.layer)) layerNodes.set(n.layer, []);
    layerNodes.get(n.layer).push(n);
  }
  for (const arr of layerNodes.values()) {
    arr.sort((a, b) => a.y - b.y);
  }
  const groups = new Map();

  for (const edge of edges) {
    if (!edge.label) continue;
    const from = nodeById.get(edge.from);
    const to = nodeById.get(edge.to);
    if (!from || !to) continue;
    if (!groups.has(edge.from)) groups.set(edge.from, []);
    groups.get(edge.from).push({ edge, from, to });
  }

  const posMap = new Map();

  for (const list of groups.values()) {
    const from = list[0]?.from;
    if (!from) continue;
    const siblings = layerNodes.get(from.layer) || [];
    const nextSibling = siblings.find((n) => n.y > from.y + 1);

    const bandTop = from.y + NODE_H + EDGE_LABEL_GAP_FROM_FROM_NODE;
    const bandBottom = nextSibling ? nextSibling.y - 6 : bandTop + EDGE_LABEL_VSTEP * 4;
    const maxRowsBySpace = Math.max(
      1,
      Math.floor((bandBottom - bandTop) / EDGE_LABEL_VSTEP) + 1
    );
    const byLabel = new Map();
    for (const item of list) {
      const key = item.edge.label;
      const existing = byLabel.get(key);
      if (existing) {
        existing.count += 1;
        existing.toX = Math.min(existing.toX, item.to.x);
      } else {
        byLabel.set(key, {
          edgeId: item.edge.id,
          label: key,
          count: 1,
          toX: item.to.x,
        });
      }
    }

    const labelItems = Array.from(byLabel.values()).sort((a, b) => a.toX - b.toX);

    const maxRows = Math.max(1, Math.min(3, maxRowsBySpace));
    const cols = Math.max(1, Math.min(3, Math.ceil(labelItems.length / maxRows)));
    const slots = maxRows * cols;
    const visibleCount = Math.min(slots, labelItems.length);
    const hiddenCount = Math.max(0, labelItems.length - visibleCount);
    const xStep = cols >= 3 ? 36 : 44;

    for (let idx = 0; idx < visibleCount; idx += 1) {
      const item = labelItems[idx];
      const col = idx % cols;
      const row = Math.floor(idx / cols);
      const center = (cols - 1) / 2;
      const x = from.x + NODE_W / 2 + (col - center) * xStep;
      const y = bandTop + row * EDGE_LABEL_VSTEP;
      let text = item.count > 1 ? `${item.label} x${item.count}` : item.label;
      if (hiddenCount > 0 && idx === visibleCount - 1) {
        text = `${text} +${hiddenCount}`;
      }
      posMap.set(item.edgeId, { x, y, text });
    }
  }

  return posMap;
}

function useScenario(graph) {
  return useMemo(() => {
    const has = (id) => graph.nodes.some((n) => n.id === id);
    const pick = (id) => (has(id) ? id : null);
    const svc = (name) => pick(`svc_${safeId(name)}`);
    const ctrl = (name) => pick(`ctrl_${safeId(name)}`);
    const ai = (name) => pick(`ai_route_${safeId(name)}`);
    const repo = (name) => pick(`repo_${safeId(name)}`);

    const scenarios = [
      {
        id: "insights_flow",
        name: "인사이트 분석 (Analyze)",
        description: "사용자 입력이 인사이트 점수/추천/근거로 변환되는 전 과정을 보여줍니다.",
        defaultInput: { ticker: "AAPL", market: "US", days: 90, newsLimit: 20, refresh: false },
        steps: [
          { title: "1) 사용자 입력", detail: "사용자가 {{ticker}} ({{market}}) 분석을 실행합니다.", nodeIds: ["fe_ui"] },
          {
            title: "2) Query Hook 실행",
            detail: "React Query 훅이 캐시 상태를 확인하고 API 요청을 준비합니다.",
            nodeIds: ["fe_query", "fe_api"],
            edgeIds: ["fe_ui->fe_query", "fe_query->fe_api"],
          },
          {
            title: "3) Backend 엔드포인트 진입",
            detail: "/api/market/insights 요청이 MarketController로 들어옵니다.",
            nodeIds: [ctrl("MarketController"), "fe_api"],
            edgeIds: ["fe_api->ctrl_MarketController"],
          },
          {
            title: "4) Service 계층 처리",
            detail: "AnalysisService/PriceService가 입력 검증, 캐시, AI 호출을 오케스트레이션합니다.",
            nodeIds: [svc("AnalysisService"), svc("PriceService"), ctrl("MarketController"), "be_ai_client"].filter(Boolean),
            edgeIds: ["ctrl_MarketController->svc_AnalysisService", "svc_AnalysisService->be_ai_client"].filter(Boolean),
          },
          {
            title: "5) AI Gateway 호출",
            detail: "Backend AiClient가 FastAPI로 요청을 전달합니다.",
            nodeIds: ["be_ai_client", "ai_gateway"],
            edgeIds: ["be_ai_client->ai_gateway"],
          },
          {
            title: "6) AI Intelligence 라우트",
            detail: "routes/intelligence.py에서 점수 계산/추천 생성 파이프라인을 실행합니다.",
            nodeIds: [ai("intelligence"), "ai_gateway"].filter(Boolean),
            edgeIds: ["ai_gateway->ai_route_intelligence"],
          },
          {
            title: "7) 시세/뉴스/펀더멘털 수집",
            detail: "market_data, prices 라우트를 거쳐 yfinance/Alpha Vantage 데이터를 결합합니다.",
            nodeIds: [ai("market_data"), ai("prices"), "ext_yfinance", "ext_alpha"].filter(Boolean),
            edgeIds: ["ai_route_market_data->ext_yfinance", "ai_route_market_data->ext_alpha"],
          },
          {
            title: "8) LLM 요약/리포트 보강",
            detail: "설정에 따라 OpenAI/Ollama를 통해 자연어 요약을 생성합니다.",
            nodeIds: [ai("intelligence"), "ext_openai", "ext_ollama"].filter(Boolean),
            edgeIds: ["ai_route_intelligence->ext_openai", "ai_route_intelligence->ext_ollama"],
          },
          {
            title: "9) 응답 복귀",
            detail: "추천(action/score/breakdown)가 프론트로 돌아와 시각화됩니다.",
            nodeIds: ["fe_ui", "fe_query", ctrl("MarketController"), svc("AnalysisService")].filter(Boolean),
          },
        ],
      },
      {
        id: "portfolio_detail",
        name: "포트폴리오 상세 조회",
        description: "포지션, 가격, 히스토리, 섹터 정보를 결합해 성과/배분을 계산합니다.",
        defaultInput: { portfolioId: 1 },
        steps: [
          {
            title: "1) 포트폴리오 상세 요청",
            detail: "프론트가 /api/portfolio/{{portfolioId}}를 호출합니다.",
            nodeIds: ["fe_ui", "fe_api", "fe_query"],
            edgeIds: ["fe_ui->fe_query", "fe_query->fe_api"],
          },
          {
            title: "2) PortfolioController -> PortfolioService",
            detail: "컨트롤러가 서비스로 위임하고 사용자 권한/포트폴리오 존재를 검증합니다.",
            nodeIds: [ctrl("PortfolioController"), svc("PortfolioService")].filter(Boolean),
            edgeIds: ["fe_api->ctrl_PortfolioController", "ctrl_PortfolioController->svc_PortfolioService"].filter(Boolean),
          },
          {
            title: "3) DB 포지션 조회",
            detail: "Repository 계층에서 포트폴리오/포지션 데이터를 조회합니다.",
            nodeIds: [svc("PortfolioService"), "repo_PortfolioRepository", "repo_PortfolioPositionRepository", "db_mysql"].filter(Boolean),
          },
          {
            title: "4) 시장별 가격 배치 호출",
            detail: "PriceService가 tickers를 시장별로 모아 AI /prices/batch를 호출합니다.",
            nodeIds: [svc("PortfolioService"), svc("PriceService"), "be_ai_client", ai("prices")].filter(Boolean),
            edgeIds: ["svc_PortfolioService->svc_PriceService", "svc_PriceService->be_ai_client", "be_ai_client->ai_gateway", "ai_gateway->ai_route_prices"].filter(Boolean),
          },
          {
            title: "5) 상세 보조 데이터 결합",
            detail: "/prices/info, /prices/historical 결과로 day/week/month 변화율을 계산합니다.",
            nodeIds: [svc("PortfolioService"), ai("prices"), "ext_yfinance"].filter(Boolean),
            edgeIds: ["ai_route_prices->ext_yfinance"],
          },
          {
            title: "6) 응답 구성",
            detail: "성능지표/배분/포지션 요약을 직렬화해 프론트로 전달합니다.",
            nodeIds: [svc("PortfolioService"), ctrl("PortfolioController"), "fe_ui"].filter(Boolean),
          },
        ],
      },
      {
        id: "watchlist_refresh",
        name: "관심종목 인사이트 갱신",
        description: "watchlist 항목별 최신 인사이트 갱신 및 캐시 반영 경로를 보여줍니다.",
        defaultInput: { ticker: "TSLA", market: "US", test: false },
        steps: [
          { title: "1) 사용자가 갱신 버튼 클릭", detail: "{{ticker}} 인사이트 갱신 요청이 생성됩니다.", nodeIds: ["fe_ui", "fe_api"] },
          {
            title: "2) Backend MarketController /insights refresh",
            detail: "refresh=true로 들어온 요청이 캐시 무시 경로로 진행됩니다.",
            nodeIds: [ctrl("MarketController"), svc("MarketCacheService"), svc("AnalysisService")].filter(Boolean),
            edgeIds: ["fe_api->ctrl_MarketController"],
          },
          {
            title: "3) AI 인사이트 재계산",
            detail: "AiClient -> FastAPI intelligence 라우트가 실시간 데이터를 재수집합니다.",
            nodeIds: ["be_ai_client", "ai_gateway", ai("intelligence"), ai("market_data"), "ext_yfinance", "ext_alpha"].filter(Boolean),
            edgeIds: ["be_ai_client->ai_gateway", "ai_gateway->ai_route_intelligence", "ai_route_market_data->ext_yfinance", "ai_route_market_data->ext_alpha"].filter(Boolean),
          },
          {
            title: "4) MarketCache 업데이트",
            detail: "업데이트된 insights_json이 DB/캐시에 저장됩니다.",
            nodeIds: [svc("MarketCacheService"), "db_mysql", "db_redis"].filter(Boolean),
          },
          { title: "5) UI 재렌더", detail: "score/action/price 요약이 watchlist 카드에 반영됩니다.", nodeIds: ["fe_query", "fe_ui"] },
        ],
      },
      {
        id: "report_stream",
        name: "리포트 스트리밍 + 내보내기",
        description: "SSE 실시간 리포트 생성과 TXT/JSON/PDF export를 포함합니다.",
        defaultInput: { ticker: "NVDA", market: "US", web: true, template: "detailed" },
        steps: [
          { title: "1) 스트림 시작", detail: "프론트가 /api/market/report/stream (SSE)을 열어 토큰 단위 수신을 준비합니다.", nodeIds: ["fe_ui", "fe_api"] },
          { title: "2) ReportStreamController", detail: "요청이 SseEmitterRegistry를 통해 장시간 스트림으로 관리됩니다.", nodeIds: [ctrl("ReportStreamController"), "svc_MarketCacheService", "svc_UsageService"].filter(Boolean) },
          {
            title: "3) AI /report 호출",
            detail: "AiClient가 FastAPI intelligence.report 파이프라인을 호출합니다.",
            nodeIds: ["be_ai_client", "ai_gateway", ai("intelligence"), "ext_openai", "ext_ollama"].filter(Boolean),
            edgeIds: ["be_ai_client->ai_gateway", "ai_gateway->ai_route_intelligence", "ai_route_intelligence->ext_openai", "ai_route_intelligence->ext_ollama"].filter(Boolean),
          },
          { title: "4) 스트림 완료 + 히스토리 저장", detail: "완성된 보고서가 report-history와 usage log에 기록됩니다.", nodeIds: ["db_mysql", "svc_UsageService", "svc_MarketCacheService"].filter(Boolean) },
          { title: "5) Export API", detail: "같은 리포트를 TXT/JSON/PDF로 ExportController에서 직렬화하여 다운로드합니다.", nodeIds: [ctrl("ReportExportController"), "fe_ui"].filter(Boolean) },
        ],
      },
      {
        id: "realtime_ws",
        name: "실시간 시세 WebSocket",
        description: "구독 이벤트가 Finnhub까지 전달되고, 체결 데이터가 다시 UI로 브로드캐스트됩니다.",
        defaultInput: { ticker: "MSFT" },
        steps: [
          { title: "1) 브라우저 WS 연결", detail: "클라이언트가 /ws/quotes 연결 후 {{ticker}} 구독 메시지를 전송합니다.", nodeIds: ["fe_ws", "be_ws_handler"], edgeIds: ["fe_ws->be_ws_handler"] },
          { title: "2) 구독 이벤트 전파", detail: "핸들러가 이벤트를 Relay로 전달해 AI WS 구독 명령을 보냅니다.", nodeIds: ["be_ws_handler", "be_ws_relay", ai("realtime")].filter(Boolean), edgeIds: ["be_ws_handler->be_ws_relay", "be_ws_relay->ai_route_realtime"].filter(Boolean) },
          { title: "3) Finnhub 실시간 수신", detail: "AI realtime 라우트가 Finnhub WebSocket 체결 데이터를 수신합니다.", nodeIds: [ai("realtime"), "ext_finnhub"].filter(Boolean), edgeIds: ["ai_route_realtime->ext_finnhub"] },
          { title: "4) Backend 브로드캐스트", detail: "Relay -> Handler -> 구독 세션들에게 ticker별 역인덱스로 고효율 브로드캐스트합니다.", nodeIds: ["be_ws_relay", "be_ws_handler", "fe_ws"] },
          { title: "5) UI 실시간 반영", detail: "현재가/거래량/타임스탬프가 카드/차트에 즉시 반영됩니다.", nodeIds: ["fe_ui"] },
        ],
      },
      {
        id: "backtest_execute",
        name: "백테스트 실행 파이프라인",
        description: "전략 파라미터 입력부터 AI 백테스트 계산, 성과 지표 반환까지의 흐름입니다.",
        defaultInput: {
          ticker: "AAPL",
          market: "US",
          strategy: "sma_cross",
          start: "2024-01-01",
          end: "2025-01-01",
          initialCapital: 100000,
        },
        steps: [
          { title: "1) 전략/기간/자본 입력", detail: "사용자가 {{ticker}} {{strategy}} 백테스트를 시작합니다.", nodeIds: ["fe_ui", "fe_query", "fe_api"], edgeIds: ["fe_ui->fe_query", "fe_query->fe_api"] },
          { title: "2) BacktestController 진입", detail: "요청이 /api/backtest로 들어와 유효성을 검사합니다.", nodeIds: [ctrl("BacktestController"), svc("BacktestService")].filter(Boolean), edgeIds: ["fe_api->ctrl_BacktestController", "ctrl_BacktestController->svc_BacktestService"].filter(Boolean) },
          { title: "3) AI backtest 라우트 호출", detail: "BacktestService가 AiClient를 통해 FastAPI backtest 라우트를 호출합니다.", nodeIds: [svc("BacktestService"), "be_ai_client", "ai_gateway", ai("backtest")].filter(Boolean), edgeIds: ["svc_BacktestService->be_ai_client", "be_ai_client->ai_gateway", "ai_gateway->ai_route_backtest"].filter(Boolean) },
          { title: "4) 과거데이터 로드 + 전략 시뮬레이션", detail: "AI가 yfinance 가격으로 매매 시뮬레이션을 실행합니다.", nodeIds: [ai("backtest"), "ext_yfinance"].filter(Boolean), edgeIds: ["ai_route_backtest->ext_yfinance"] },
          { title: "5) 성과 지표 계산", detail: "수익률, 샤프, MDD, 거래내역을 계산/직렬화합니다.", nodeIds: [ai("backtest"), svc("BacktestService")].filter(Boolean), edgeIds: ["be_ai_client->ai_gateway"] },
          { title: "6) 프론트 차트 렌더", detail: "equity curve와 성능 요약이 결과 화면에 렌더링됩니다.", nodeIds: ["fe_ui", "fe_query"] },
        ],
      },
      {
        id: "auth_refresh",
        name: "로그인 + 토큰 재발급",
        description: "초기 로그인, 토큰 발급, 만료 후 refresh 재발급 흐름을 보여줍니다.",
        defaultInput: { email: "demo@stock.ai", rememberMe: true },
        steps: [
          { title: "1) 로그인 요청 생성", detail: "이메일/비밀번호가 Auth API 요청으로 전송됩니다.", nodeIds: ["fe_ui", "fe_api"] },
          { title: "2) AuthController / AuthService 검증", detail: "유저 조회 및 패스워드 해시 검증을 수행합니다.", nodeIds: [ctrl("AuthController"), svc("AuthService"), svc("TokenHashService")].filter(Boolean), edgeIds: ["fe_api->ctrl_AuthController", "ctrl_AuthController->svc_AuthService", "svc_AuthService->svc_TokenHashService"].filter(Boolean) },
          { title: "3) JWT/Refresh 발급", detail: "JwtService가 access/refresh 토큰을 발급하고 refresh 저장소를 갱신합니다.", nodeIds: [svc("AuthService"), svc("JwtService"), repo("RefreshTokenRepository"), "db_mysql"].filter(Boolean), edgeIds: ["svc_AuthService->svc_JwtService", "svc_AuthService->repo_RefreshTokenRepository", "repo_RefreshTokenRepository->db_mysql"].filter(Boolean) },
          { title: "4) Access 만료 후 Refresh 호출", detail: "프론트 인터셉터가 refresh 엔드포인트를 호출해 세션을 유지합니다.", nodeIds: ["fe_api", ctrl("AuthController"), svc("AuthService")].filter(Boolean), edgeIds: ["fe_api->ctrl_AuthController", "ctrl_AuthController->svc_AuthService"].filter(Boolean) },
          { title: "5) 재발급 토큰으로 요청 재시도", detail: "새 access token으로 원래 API 요청을 자동 재시도합니다.", nodeIds: ["fe_query", "fe_api", "fe_ui"] },
        ],
      },
      {
        id: "paper_trade_order",
        name: "모의투자 주문 체결",
        description: "주문 입력, 리스크/잔고 검사, 체결, 포지션 업데이트까지의 전체 흐름입니다.",
        defaultInput: { accountId: 1, ticker: "NVDA", side: "BUY", qty: 3, price: 840.5 },
        steps: [
          { title: "1) 주문 입력", detail: "{{ticker}} {{side}} {{qty}}주 주문을 생성합니다.", nodeIds: ["fe_ui", "fe_api"] },
          { title: "2) PaperTradingController 진입", detail: "요청 검증 후 PaperTradingService로 위임합니다.", nodeIds: [ctrl("PaperTradingController"), svc("PaperTradingService")].filter(Boolean), edgeIds: ["fe_api->ctrl_PaperTradingController", "ctrl_PaperTradingController->svc_PaperTradingService"].filter(Boolean) },
          { title: "3) 잔고/리스크 체크", detail: "계좌 가용현금과 주문 리스크 한도를 확인합니다.", nodeIds: [svc("PaperTradingService"), repo("PaperAccountRepository"), "db_mysql"].filter(Boolean), edgeIds: ["svc_PaperTradingService->repo_PaperAccountRepository", "repo_PaperAccountRepository->db_mysql"].filter(Boolean) },
          { title: "4) 체결 시뮬레이션", detail: "모의 체결 엔진이 fill price/slippage를 계산합니다.", nodeIds: [svc("PaperTradingService"), svc("PriceService"), "be_ai_client", ai("prices")].filter(Boolean), edgeIds: ["svc_PaperTradingService->svc_PriceService", "svc_PriceService->be_ai_client", "be_ai_client->ai_gateway", "ai_gateway->ai_route_prices"].filter(Boolean) },
          { title: "5) 주문/포지션/잔고 반영", detail: "주문과 포지션, 계좌 잔고를 저장소에 반영합니다.", nodeIds: [repo("PaperOrderRepository"), repo("PaperPositionRepository"), repo("PaperAccountRepository"), "db_mysql"].filter(Boolean), edgeIds: ["repo_PaperOrderRepository->db_mysql", "repo_PaperPositionRepository->db_mysql", "repo_PaperAccountRepository->db_mysql"].filter(Boolean) },
          { title: "6) UI 업데이트", detail: "체결 결과와 계좌 평가금액이 즉시 반영됩니다.", nodeIds: ["fe_query", "fe_ui"] },
        ],
      },
    ];

    return scenarios.map((s) => ({
      ...s,
      steps: s.steps.map((st) => ({
        ...st,
        nodeIds: (st.nodeIds || []).filter(Boolean),
        edgeIds: (st.edgeIds || []).filter(Boolean),
      })),
    }));
  }, [graph]);
}

function FileTreeNode({ node, depth, expanded, setExpanded, activeQuery }) {
  if (!node) return null;
  const isDir = node.type === "dir";
  const isOpen = expanded.has(node.path);
  const hasChildren = isDir && (node.children || []).length > 0;

  function toggle() {
    if (!isDir) return;
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(node.path)) next.delete(node.path);
      else next.add(node.path);
      return next;
    });
  }

  const match = activeQuery && node.path.toLowerCase().includes(activeQuery.toLowerCase());

  return (
    <div className={styles.treeNode}>
      <div
        className={`${styles.treeRow} ${match ? styles.treeMatch : ""}`}
        style={{ paddingLeft: 10 + depth * 14 }}
        onClick={toggle}
        role={isDir ? "button" : undefined}
        tabIndex={isDir ? 0 : -1}
        onKeyDown={(e) => {
          if (!isDir) return;
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            toggle();
          }
        }}
      >
        {isDir ? <span className={styles.treeCaret}>{isOpen ? "▼" : "▶"}</span> : <span className={styles.treeCaret}>•</span>}
        <span className={styles.treeName}>{node.name}</span>
        {isDir ? <span className={styles.treeCount}>{node.children.length}</span> : null}
      </div>

      {isDir && isOpen && hasChildren ? (
        <div>
          {node.children.map((ch) => (
            <FileTreeNode
              key={ch.path}
              node={ch}
              depth={depth + 1}
              expanded={expanded}
              setExpanded={setExpanded}
              activeQuery={activeQuery}
            />
          ))}
        </div>
      ) : null}
    </div>
  );
}

export default function SystemMap() {
  const [tab, setTab] = useState("architecture");
  const [nodeSearch, setNodeSearch] = useState("");
  const [fileSearch, setFileSearch] = useState("");
  const [scenarioId, setScenarioId] = useState("insights_flow");
  const [inputText, setInputText] = useState("");
  const [currentStep, setCurrentStep] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speedMs, setSpeedMs] = useState(1600);
  const [mapScale, setMapScale] = useState(72);

  const graph = useMemo(() => buildArchitecture(rawMapData), []);
  const scenarios = useScenario(graph);
  const scenario = useMemo(
    () => scenarios.find((s) => s.id === scenarioId) || scenarios[0],
    [scenarioId, scenarios]
  );

  useEffect(() => {
    if (!scenario) return;
    setInputText(JSON.stringify(scenario.defaultInput || {}, null, 2));
    setCurrentStep(0);
    setPlaying(false);
  }, [scenarioId, scenario]);

  const parsedInput = useMemo(() => {
    try {
      return { value: JSON.parse(inputText || "{}"), error: "" };
    } catch (e) {
      return { value: scenario?.defaultInput || {}, error: `JSON 파싱 오류: ${e.message}` };
    }
  }, [inputText, scenario]);

  const resolvedSteps = useMemo(() => {
    const ctx = parsedInput.value || {};
    return (scenario?.steps || []).map((st) => ({
      ...st,
      title: replaceTemplate(st.title, ctx),
      detail: replaceTemplate(st.detail, ctx),
    }));
  }, [scenario, parsedInput.value]);

  const flowFrames = useMemo(
    () => buildScenarioFrames(scenario?.id, parsedInput.value, resolvedSteps.length),
    [scenario?.id, parsedInput.value, resolvedSteps.length]
  );

  useEffect(() => {
    if (resolvedSteps.length === 0) return;
    setCurrentStep((prev) => Math.min(prev, resolvedSteps.length - 1));
  }, [resolvedSteps.length]);

  useEffect(() => {
    if (!playing || resolvedSteps.length === 0) return undefined;
    const timer = window.setInterval(() => {
      setCurrentStep((prev) => {
        if (prev >= resolvedSteps.length - 1) {
          setPlaying(false);
          return prev;
        }
        return prev + 1;
      });
    }, speedMs);
    return () => window.clearInterval(timer);
  }, [playing, speedMs, resolvedSteps.length]);

  const activeStep = resolvedSteps[currentStep] || resolvedSteps[0] || null;
  const activeFrame = flowFrames[currentStep] || flowFrames[0] || null;
  const activeNodeSet = useMemo(() => new Set(activeStep?.nodeIds || []), [activeStep]);
  const activeEdgeSet = useMemo(() => new Set(activeStep?.edgeIds || []), [activeStep]);
  const flowFocusMode = playing && activeNodeSet.size > 0;

  const nodeById = useMemo(
    () => new Map(graph.nodes.map((n) => [n.id, n])),
    [graph.nodes]
  );
  const edgeById = useMemo(
    () => new Map(graph.edges.map((e) => [e.id, e])),
    [graph.edges]
  );
  const edgeLabelPosMap = useMemo(
    () => buildEdgeLabelPositions(graph.nodes, graph.edges),
    [graph.nodes, graph.edges]
  );
  const expertDetail = useMemo(() => {
    if (!activeStep) return null;

    const nodes = (activeStep.nodeIds || [])
      .map((nid) => nodeById.get(nid))
      .filter(Boolean);
    const edges = (activeStep.edgeIds || [])
      .map((eid) => edgeById.get(eid))
      .filter(Boolean);

    const interactions = edges.map((e) => {
      const from = nodeById.get(e.from);
      const to = nodeById.get(e.to);
      return {
        id: e.id,
        fromLabel: from?.label || e.from,
        toLabel: to?.label || e.to,
        fromLayer: from?.layer || "",
        toLayer: to?.layer || "",
        protocol: e.label || "internal link",
      };
    });

    const delta = activeFrame?.delta || { added: [], updated: [], removed: [] };
    const inputObj =
      activeFrame?.input && typeof activeFrame.input === "object" ? activeFrame.input : {};
    const outputObj =
      activeFrame?.output && typeof activeFrame.output === "object" ? activeFrame.output : {};
    const inputKeys = Object.keys(inputObj);
    const outputKeys = Object.keys(outputObj);
    const changedCount = delta.added.length + delta.updated.length + delta.removed.length;
    const layerPath = Array.from(new Set(nodes.map((n) => n.layer)))
      .map((layer) => LAYER_LABEL[layer] || layer)
      .join(" -> ");
    const protocolSummary = Array.from(new Set(interactions.map((it) => it.protocol))).slice(0, 8);
    const files = Array.from(new Set(nodes.map((n) => n.file).filter(Boolean)));
    const complexityScore = nodes.length * 2 + interactions.length * 3 + changedCount;
    const complexityLevel =
      complexityScore >= 20 ? "high" : complexityScore >= 10 ? "medium" : "low";

    return {
      stepTitle: activeStep.title,
      stepDetail: activeStep.detail,
      nodes,
      interactions,
      delta,
      note: activeFrame?.note || "",
      context: {
        nodeCount: nodes.length,
        edgeCount: edges.length,
        inputKeys,
        outputKeys,
        changedCount,
        layerPath: layerPath || "N/A",
        protocolSummary,
        files,
        complexityScore,
        complexityLevel,
      },
    };
  }, [activeStep, activeFrame, nodeById, edgeById]);

  const scaledMapWidth = useMemo(
    () => Math.max(680, Math.round((graph.width * mapScale) / 100)),
    [graph.width, mapScale]
  );
  const scaledMapHeight = useMemo(
    () => Math.round((graph.height * mapScale) / 100),
    [graph.height, mapScale]
  );

  const visibleNodeSet = useMemo(() => {
    if (!nodeSearch.trim()) return null;
    const q = nodeSearch.trim().toLowerCase();
    const matched = graph.nodes
      .filter((n) => {
        const t = `${n.label} ${n.tech || ""} ${n.file || ""}`.toLowerCase();
        return t.includes(q);
      })
      .map((n) => n.id);
    return new Set(matched);
  }, [graph.nodes, nodeSearch]);

  const fileTree = useMemo(() => buildFileTree(rawMapData.files || []), []);
  const filteredFileTree = useMemo(
    () => filterTree(fileTree, fileSearch),
    [fileTree, fileSearch]
  );

  const [expanded, setExpanded] = useState(() => new Set(["", "frontend", "backend", "ai", "docs", "scripts"]));

  useEffect(() => {
    if (!fileSearch) return;
    if (!filteredFileTree) return;
    const open = new Set([""]);
    (function openAll(node) {
      if (node.type === "dir") {
        open.add(node.path);
        (node.children || []).forEach(openAll);
      }
    })(filteredFileTree);
    setExpanded(open);
  }, [fileSearch, filteredFileTree]);

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div className={styles.titleBlock}>
          <h1 className={styles.title}>System Circuit Map</h1>
          <p className={styles.subtitle}>
            구조도 + 순서도 + 파일지도 + 단계별 하이라이트 재생으로 데이터 흐름을 회로처럼 추적합니다.
          </p>
          <div className={styles.statRow}>
            <span className={styles.statPill}>Files: {rawMapData.stats?.totalFiles}</span>
            <span className={styles.statPill}>Controllers: {rawMapData.backend?.controllers?.length}</span>
            <span className={styles.statPill}>Services: {rawMapData.backend?.services?.length}</span>
            <span className={styles.statPill}>AI Routes: {rawMapData.ai?.routes?.length}</span>
          </div>
        </div>
      </div>

      <div className={styles.layout}>
        <aside className={styles.sidebar}>
          <div className={styles.panel}>
            <h3 className={styles.panelTitle}>Flow Simulator</h3>
            <label className={styles.label}>시나리오</label>
            <select
              className={styles.select}
              value={scenarioId}
              onChange={(e) => setScenarioId(e.target.value)}
            >
              {scenarios.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>

            <div className={styles.scenarioDesc}>{scenario?.description}</div>

            <label className={styles.label}>입력 데이터 (JSON)</label>
            <textarea
              className={styles.textarea}
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              spellCheck={false}
            />
            {parsedInput.error ? <div className={styles.error}>{parsedInput.error}</div> : null}

            <div className={styles.controls}>
              <button
                className={styles.btnPrimary}
                onClick={() => setPlaying((p) => !p)}
                disabled={parsedInput.error || resolvedSteps.length === 0}
              >
                {playing ? "일시정지" : "재생"}
              </button>
              <button
                className={styles.btn}
                onClick={() => setCurrentStep((v) => Math.max(0, v - 1))}
                disabled={currentStep <= 0}
              >
                이전
              </button>
              <button
                className={styles.btn}
                onClick={() => setCurrentStep((v) => Math.min(resolvedSteps.length - 1, v + 1))}
                disabled={currentStep >= resolvedSteps.length - 1}
              >
                다음
              </button>
              <button
                className={styles.btn}
                onClick={() => {
                  setPlaying(false);
                  setCurrentStep(0);
                }}
              >
                초기화
              </button>
            </div>

            <div className={styles.speedRow}>
              <label className={styles.labelInline}>속도: {speedMs}ms</label>
              <input
                type="range"
                min="400"
                max="3500"
                step="100"
                value={speedMs}
                onChange={(e) => setSpeedMs(Number(e.target.value))}
              />
            </div>

            <div className={styles.stepProgress}>
              <input
                type="range"
                min="0"
                max={Math.max(0, resolvedSteps.length - 1)}
                value={Math.min(currentStep, Math.max(0, resolvedSteps.length - 1))}
                onChange={(e) => setCurrentStep(Number(e.target.value))}
              />
              <div className={styles.progressLabel}>
                Step {resolvedSteps.length === 0 ? 0 : currentStep + 1} / {resolvedSteps.length}
              </div>
            </div>

            {activeStep ? (
              <div className={styles.currentStep}>
                <div className={styles.currentTitle}>현재 흐름 위치</div>
                <div className={styles.currentStepName}>{activeStep.title}</div>
                <div className={styles.currentStepDetail}>{activeStep.detail}</div>
                {activeFrame ? (
                  <div className={styles.dataFlow}>
                    <div className={styles.dataGrid}>
                      <div className={styles.dataCard}>
                        <div className={styles.dataTitle}>입력 데이터</div>
                        <pre className={styles.dataJson}>
                          {JSON.stringify(activeFrame.input, null, 2)}
                        </pre>
                      </div>
                      <div className={styles.dataCard}>
                        <div className={styles.dataTitle}>결과 데이터</div>
                        <pre className={styles.dataJson}>
                          {JSON.stringify(activeFrame.output, null, 2)}
                        </pre>
                      </div>
                    </div>
                    <div className={styles.transformNote}>
                      {activeFrame.note}
                    </div>
                    <div className={styles.deltaRow}>
                      <span className={`${styles.deltaChip} ${styles.deltaAdded}`}>
                        + 추가: {activeFrame.delta.added.join(", ") || "없음"}
                      </span>
                      <span className={`${styles.deltaChip} ${styles.deltaUpdated}`}>
                        ~ 변경: {activeFrame.delta.updated.join(", ") || "없음"}
                      </span>
                      <span className={`${styles.deltaChip} ${styles.deltaRemoved}`}>
                        - 제거: {activeFrame.delta.removed.join(", ") || "없음"}
                      </span>
                    </div>
                  </div>
                ) : null}
              </div>
            ) : null}
          </div>

          <div className={styles.panel}>
            <h3 className={styles.panelTitle}>시나리오 단계</h3>
            <div className={styles.stepList}>
              {resolvedSteps.map((st, idx) => (
                <button
                  key={`${st.title}-${idx}`}
                  className={`${styles.stepItem} ${idx === currentStep ? styles.stepItemActive : ""}`}
                  onClick={() => {
                    setCurrentStep(idx);
                    setPlaying(false);
                  }}
                >
                  <span className={styles.stepIndex}>{idx + 1}</span>
                  <span>
                    <span className={styles.stepText}>{st.title}</span>
                    {flowFrames[idx] ? (
                      <span className={styles.stepDelta}>
                        +{flowFrames[idx].delta.added.length} / ~{flowFrames[idx].delta.updated.length} / -{flowFrames[idx].delta.removed.length}
                      </span>
                    ) : null}
                  </span>
                </button>
              ))}
            </div>
          </div>
        </aside>

        <main className={styles.main}>
          <div className={styles.tabs}>
            <button
              className={`${styles.tab} ${tab === "architecture" ? styles.tabActive : ""}`}
              onClick={() => setTab("architecture")}
            >
              구조도 그래프
            </button>
            <button
              className={`${styles.tab} ${tab === "sequence" ? styles.tabActive : ""}`}
              onClick={() => setTab("sequence")}
            >
              순서도
            </button>
            <button
              className={`${styles.tab} ${tab === "files" ? styles.tabActive : ""}`}
              onClick={() => setTab("files")}
            >
              파일 지도
            </button>
          </div>

          {tab === "architecture" ? (
            <div className={styles.archStack}>
              <section className={styles.canvasPanel}>
                <div className={styles.canvasHeader}>
                  <input
                    className={styles.search}
                    placeholder="노드 검색 (클래스/기술/파일)"
                    value={nodeSearch}
                    onChange={(e) => setNodeSearch(e.target.value)}
                  />
                  <div className={styles.zoomControl}>
                    <label className={styles.zoomLabel}>맵 축척: {mapScale}%</label>
                    <input
                      type="range"
                      min="50"
                      max="200"
                      step="5"
                      value={mapScale}
                      onChange={(e) => setMapScale(Number(e.target.value))}
                    />
                  </div>
                </div>

                <div className={styles.mapViewport}>
                  <svg
                    className={styles.svg}
                    viewBox={`0 0 ${graph.width} ${graph.height}`}
                    width={scaledMapWidth}
                    height={scaledMapHeight}
                  >
                  <defs>
                    <marker id="arrow" markerWidth="9" markerHeight="6" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
                      <path d="M0,0 L9,3 L0,6 z" fill="currentColor" />
                    </marker>
                  </defs>

                  {LAYER_ORDER.map((layer, idx) => (
                    <g key={layer}>
                      <rect
                        x={LAYER_START_X - LAYER_BADGE_OFFSET_X + idx * LAYER_GAP_X}
                        y={LAYER_BADGE_Y}
                        width={LAYER_BADGE_W}
                        height={LAYER_BADGE_H}
                        rx={9}
                        className={styles.layerBadge}
                      />
                      <text
                        x={LAYER_START_X - LAYER_BADGE_OFFSET_X + idx * LAYER_GAP_X + LAYER_BADGE_W / 2}
                        y={LAYER_BADGE_Y + Math.round(LAYER_BADGE_H * 0.68)}
                        className={styles.layerLabel}
                        textAnchor="middle"
                      >
                        {LAYER_LABEL[layer]}
                      </text>
                    </g>
                  ))}

                  {graph.edges.map((edge) => {
                    const from = nodeById.get(edge.from);
                    const to = nodeById.get(edge.to);
                    if (!from || !to) return null;
                    const active = activeEdgeSet.has(edge.id);
                    if (active) return null;
                    const path = edgePath(from, to);
                    const filteredOut =
                      visibleNodeSet &&
                      !(visibleNodeSet.has(edge.from) && visibleNodeSet.has(edge.to));
                    return (
                      <g
                        key={edge.id}
                        className={`${styles.edgeGroup} ${filteredOut ? styles.edgeDim : ""}`}
                      >
                        <path d={path} className={styles.edge} markerEnd="url(#arrow)" />
                      </g>
                    );
                  })}

                  {graph.nodes.map((n) => {
                    const active = activeNodeSet.has(n.id);
                    const filteredOut = visibleNodeSet && !visibleNodeSet.has(n.id);
                    const flowMuted = flowFocusMode && !active;
                    return (
                      <g
                        key={n.id}
                        transform={`translate(${n.x}, ${n.y})`}
                        className={`${styles.nodeGroup} ${active ? styles.nodeActive : ""} ${flowFocusMode && active ? styles.nodeActiveFlow : ""} ${flowMuted ? styles.nodeFlowMuted : ""} ${filteredOut ? styles.nodeDim : ""}`}
                      >
                        <rect width={NODE_W} height={NODE_H} rx={8} className={styles.node} />
                        <text className={styles.nodeTitle} x="8" y="14">
                          {truncateText(n.label, 24)}
                        </text>
                        <text className={styles.nodeSub} x="8" y="27">
                          {truncateText(n.tech || "", 26)}
                        </text>
                      </g>
                    );
                  })}

                  {graph.edges.map((edge) => {
                    const from = nodeById.get(edge.from);
                    const to = nodeById.get(edge.to);
                    if (!from || !to) return null;
                    const active = activeEdgeSet.has(edge.id);
                    if (!active) return null;
                    const path = edgePath(from, to);
                    const filteredOut =
                      visibleNodeSet &&
                      !(visibleNodeSet.has(edge.from) && visibleNodeSet.has(edge.to));
                    return (
                      <g
                        key={`active-${edge.id}`}
                        className={`${styles.edgeGroup} ${styles.edgeActive} ${filteredOut ? styles.edgeDim : ""}`}
                      >
                        <path d={path} className={styles.edge} markerEnd="url(#arrow)" />
                      </g>
                    );
                  })}

                  {graph.edges.map((edge) => {
                    if (!edge.label) return null;
                    const pos = edgeLabelPosMap.get(edge.id);
                    if (!pos) return null;
                    const active = activeEdgeSet.has(edge.id);
                    const filteredOut =
                      visibleNodeSet &&
                      !(visibleNodeSet.has(edge.from) && visibleNodeSet.has(edge.to));
                    return (
                      <text
                        key={`label-${edge.id}`}
                        className={`${styles.edgeLabel} ${active ? styles.edgeLabelActive : ""} ${filteredOut ? styles.edgeDim : ""}`}
                        x={pos.x}
                        y={pos.y}
                        textAnchor="middle"
                        dominantBaseline="hanging"
                      >
                        {truncateText(pos.text || edge.label, 20)}
                      </text>
                    );
                  })}
                  </svg>
                </div>
              </section>

              <section className={styles.expertPanel}>
                <div className={styles.expertHeader}>
                  <div className={styles.expertTitle}>Flow Deep Dive</div>
                  <div className={styles.expertSub}>
                    현재 Step의 노드 상호작용, 기술 계층 이동, 상태 변경 요약
                  </div>
                </div>

                {expertDetail ? (
                  <div className={styles.expertGrid}>
                    <div className={styles.expertBlock}>
                      <h4 className={styles.expertBlockTitle}>Step Context</h4>
                      <p className={styles.expertTextStrong}>{expertDetail.stepTitle}</p>
                      <p className={styles.expertText}>{expertDetail.stepDetail}</p>
                      <p className={styles.expertMeta}>
                        Scenario: <b>{scenario?.name}</b> | Step {resolvedSteps.length === 0 ? 0 : currentStep + 1}/{resolvedSteps.length}
                      </p>
                      <div className={styles.expertContextGrid}>
                        <div className={styles.expertKv}>
                          <span className={styles.expertKvLabel}>Complexity</span>
                          <span className={styles.expertCode}>
                            {expertDetail.context.complexityLevel} ({expertDetail.context.complexityScore})
                          </span>
                        </div>
                        <div className={styles.expertKv}>
                          <span className={styles.expertKvLabel}>Active Nodes/Edges</span>
                          <span className={styles.expertCode}>
                            {expertDetail.context.nodeCount} / {expertDetail.context.edgeCount}
                          </span>
                        </div>
                        <div className={styles.expertKv}>
                          <span className={styles.expertKvLabel}>Layer Path</span>
                          <span className={styles.expertText}>{expertDetail.context.layerPath}</span>
                        </div>
                        <div className={styles.expertKv}>
                          <span className={styles.expertKvLabel}>Input Keys</span>
                          <span className={styles.expertCode}>
                            {expertDetail.context.inputKeys.slice(0, 10).join(", ") || "none"}
                          </span>
                        </div>
                        <div className={styles.expertKv}>
                          <span className={styles.expertKvLabel}>Output Keys</span>
                          <span className={styles.expertCode}>
                            {expertDetail.context.outputKeys.slice(0, 10).join(", ") || "none"}
                          </span>
                        </div>
                        <div className={styles.expertKv}>
                          <span className={styles.expertKvLabel}>Observed Protocols</span>
                          <span className={styles.expertCode}>
                            {expertDetail.context.protocolSummary.join(", ") || "none"}
                          </span>
                        </div>
                        <div className={styles.expertKv}>
                          <span className={styles.expertKvLabel}>Changed Fields</span>
                          <span className={styles.expertCode}>
                            {expertDetail.context.changedCount}
                          </span>
                        </div>
                        <div className={styles.expertKv}>
                          <span className={styles.expertKvLabel}>Related Files</span>
                          <span className={styles.expertCode}>
                            {expertDetail.context.files.slice(0, 4).join(", ") || "none"}
                          </span>
                        </div>
                      </div>
                    </div>

                    <div className={styles.expertBlock}>
                      <h4 className={styles.expertBlockTitle}>Interaction Chain</h4>
                      {expertDetail.interactions.length > 0 ? (
                        <ul className={styles.expertList}>
                          {expertDetail.interactions.map((it) => (
                            <li key={it.id}>
                              <span className={styles.expertCode}>{it.fromLabel}</span>
                              {" -> "}
                              <span className={styles.expertCode}>{it.toLabel}</span>
                              {" | "}
                              <span className={styles.expertDim}>{LAYER_LABEL[it.fromLayer] || it.fromLayer}</span>
                              {" -> "}
                              <span className={styles.expertDim}>{LAYER_LABEL[it.toLayer] || it.toLayer}</span>
                              {" | "}
                              protocol=<span className={styles.expertCode}>{it.protocol}</span>
                            </li>
                          ))}
                        </ul>
                      ) : (
                        <p className={styles.expertText}>현재 Step에는 명시된 edge 추적 정보가 없습니다.</p>
                      )}
                    </div>

                    <div className={styles.expertBlock}>
                      <h4 className={styles.expertBlockTitle}>Active Node Details</h4>
                      {expertDetail.nodes.length > 0 ? (
                        <ul className={styles.expertList}>
                          {expertDetail.nodes.map((n) => (
                            <li key={n.id}>
                              <span className={styles.expertCode}>{n.label}</span>
                              {" | "}
                              layer=<span className={styles.expertDim}>{LAYER_LABEL[n.layer] || n.layer}</span>
                              {" | "}
                              tech=<span className={styles.expertCode}>{n.tech || "-"}</span>
                              {n.file ? (
                                <>
                                  {" | file="}
                                  <span className={styles.expertCode}>{n.file}</span>
                                </>
                              ) : null}
                            </li>
                          ))}
                        </ul>
                      ) : (
                        <p className={styles.expertText}>현재 Step 활성 노드 정보가 없습니다.</p>
                      )}
                    </div>

                    <div className={styles.expertBlock}>
                      <h4 className={styles.expertBlockTitle}>State Transition</h4>
                      <p className={styles.expertText}>{expertDetail.note || "상태 전이 메모가 없습니다."}</p>
                      <div className={styles.expertDeltaRow}>
                        <span className={`${styles.deltaChip} ${styles.deltaAdded}`}>+ {expertDetail.delta.added.join(", ") || "none"}</span>
                        <span className={`${styles.deltaChip} ${styles.deltaUpdated}`}>~ {expertDetail.delta.updated.join(", ") || "none"}</span>
                        <span className={`${styles.deltaChip} ${styles.deltaRemoved}`}>- {expertDetail.delta.removed.join(", ") || "none"}</span>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className={styles.expertEmpty}>Flow 상세 정보를 생성할 수 없습니다.</div>
                )}
              </section>
            </div>
          ) : null}

          {tab === "sequence" ? (
            <section className={styles.sequencePanel}>
              <div className={styles.sequenceMeta}>
                <div className={styles.sequenceTitle}>{scenario?.name}</div>
                <div className={styles.sequenceDesc}>{scenario?.description}</div>
              </div>
              <div className={styles.sequenceSteps}>
                {resolvedSteps.map((st, idx) => (
                  <div
                    key={`${st.title}-${idx}`}
                    className={`${styles.sequenceCard} ${idx === currentStep ? styles.sequenceCardActive : ""}`}
                    onClick={() => {
                      setCurrentStep(idx);
                      setPlaying(false);
                    }}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        setCurrentStep(idx);
                        setPlaying(false);
                      }
                    }}
                  >
                    <div className={styles.sequenceNum}>{idx + 1}</div>
                    <div className={styles.sequenceCardTitle}>{st.title}</div>
                    <div className={styles.sequenceCardDetail}>{st.detail}</div>
                    <div className={styles.sequenceNodeList}>
                      {(st.nodeIds || []).map((nid) => {
                        const node = nodeById.get(nid);
                        return (
                          <span key={nid} className={styles.nodeChip}>
                            {node?.label || nid}
                          </span>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>
            </section>
          ) : null}

          {tab === "files" ? (
            <section className={styles.filePanel}>
              <div className={styles.fileHeader}>
                <input
                  className={styles.search}
                  placeholder="파일/경로 검색"
                  value={fileSearch}
                  onChange={(e) => setFileSearch(e.target.value)}
                />
                <div className={styles.fileMeta}>
                  generated: {rawMapData.generatedAt}
                </div>
              </div>
              <div className={styles.fileTree}>
                {filteredFileTree ? (
                  (filteredFileTree.children || []).map((rootNode) => (
                    <FileTreeNode
                      key={rootNode.path}
                      node={rootNode}
                      depth={0}
                      expanded={expanded}
                      setExpanded={setExpanded}
                      activeQuery={fileSearch}
                    />
                  ))
                ) : (
                  <div className={styles.empty}>검색 결과가 없습니다.</div>
                )}
              </div>
            </section>
          ) : null}
        </main>
      </div>
    </div>
  );
}
