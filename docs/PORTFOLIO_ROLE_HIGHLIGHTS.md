# Stock-AI 포지션별 강조 포인트 재편집

기준일: 2026-02-25  
원본 기술문서: `docs/PORTFOLIO_TECHNICAL_DOSSIER.md`

가독성 요약 뷰(HTML): `docs/PORTFOLIO_READABLE.html`
원문 전체 뷰(HTML): `docs/PORTFOLIO_FULL.html`

## 1. 사용 목적

이 문서는 같은 프로젝트를 지원 포지션에 맞춰 다르게 설명하기 위한 가이드다.

1. 백엔드 엔지니어 지원용
2. 풀스택 엔지니어 지원용
3. AI/ML 엔지니어 지원용

---

## 2. 공통 코어 메시지(모든 포지션 공통)

면접 30초 기본 버전:

“Stock-AI는 React, Spring Boot, FastAPI를 분리한 투자 분석 플랫폼입니다.  
저는 인증 보안 강화(토큰 해시/회전), AI 호출 안정화(캐시+중복제거), 실시간 처리(WebSocket/SSE), 그리고 포트폴리오 실적 캘린더·리스크 대시보드 기능 확장을 주도했습니다.”

---

## 3. 백엔드 포지션용 재편집

### 3.1 무엇을 가장 앞에 두고 말할지

1. 보안: Refresh token hash 저장 + rotation + cleanup scheduler
2. 신뢰성: AI 호출 timeout/retry/cache/dedup
3. 실시간 게이트웨이: WS relay + 구독 동기화
4. 운영성: Request-ID, Prometheus, actuator 정책 분리

### 3.2 이력서 Bullet (국문)

1. Spring Boot 4 기반 API 서버에서 인증 보안을 강화하기 위해 refresh token 해시 저장(SHA-256+pepper), 토큰 로테이션, 만료 토큰 스케줄 정리를 구현했습니다.
2. AI 연동 구간에 Redis 캐시와 in-flight dedup을 적용해 중복 계산을 억제하고 지연/실패 상황에서의 안정성을 개선했습니다.
3. WebSocket 릴레이 아키텍처를 구현해 클라이언트 구독 상태를 중앙에서 관리하고 재연결 시 구독 자동 복구를 지원했습니다.
4. `X-Request-Id` 기반 분산 추적과 `ai_client_*` 메트릭을 도입해 운영 중 장애 분석 시간을 단축했습니다.

### 3.3 Resume Bullet (영문)

1. Hardened JWT refresh-token flow with SHA-256 token hashing, token rotation, and scheduled cleanup.
2. Implemented multi-layer resilience for AI integrations using Redis caching, in-flight deduplication, and timeout/retry controls.
3. Built a WebSocket relay path with subscription synchronization and reconnect recovery.
4. Added request correlation (`X-Request-Id`) and AI client metrics for production observability.

### 3.4 면접 핵심 질문/답변 포인트

1. Q: 토큰을 왜 해시로 저장했나요?  
   A: DB 유출 시 raw refresh token 재사용을 막기 위해서입니다. 조회는 deterministic hash로 수행하고, refresh 시 rotation을 적용했습니다.

2. Q: AI 서비스 장애 시 백엔드는 어떻게 보호하나요?  
   A: timeout/retry를 구분 설정하고, 캐시 + dedup을 결합해 외부 호출 폭증을 억제했습니다.

3. Q: 실시간 구독 유실은 어떻게 복구하나요?  
   A: relay 재연결 시 현재 구독 스냅샷을 재전송해 프론트 상태와 상류 소켓 상태를 동기화합니다.

### 3.5 코드 근거 파일

1. `backend/src/main/java/com/sw103302/backend/service/AuthService.java`
2. `backend/src/main/java/com/sw103302/backend/service/TokenHashService.java`
3. `backend/src/main/resources/db/migration/V20__harden_refresh_tokens.sql`
4. `backend/src/main/java/com/sw103302/backend/service/MarketCacheService.java`
5. `backend/src/main/java/com/sw103302/backend/component/QuoteWebSocketRelay.java`

---

## 4. 풀스택 포지션용 재편집

### 4.1 무엇을 가장 앞에 두고 말할지

1. 사용자 흐름 완성도: 로그인 -> 분석 -> 리포트 -> 포트폴리오 관리
2. FE/BE 계약 설계: React Query + REST + SSE + WS
3. 기능 확장: 실적 캘린더, 리스크 대시보드(차트 포함)
4. 장애 대응 UX: 토큰 자동 갱신, 재시도, 오프라인 표시

### 4.2 이력서 Bullet (국문)

1. React 19 + Spring Boot + FastAPI 구조에서 FE/BE/AI 인터페이스를 통합 설계해 분석·백테스트·포트폴리오 워크플로를 완성했습니다.
2. React Query 기반 서버 상태 관리로 도메인별 캐시 전략을 분리하고, 인증 만료 시 자동 refresh 재시도 플로우를 구현했습니다.
3. SSE 리포트 스트리밍과 WebSocket 실시간 시세를 UI에 통합해 실시간 분석 경험을 구현했습니다.
4. 포트폴리오 실적 캘린더/리스크 대시보드를 추가해 제품 기능을 “조회”에서 “의사결정 보조” 단계로 확장했습니다.

### 4.3 Resume Bullet (영문)

1. Delivered end-to-end product flows across React, Spring Boot, and FastAPI services.
2. Standardized server-state handling with TanStack Query and implemented token-refresh retry logic for authenticated APIs.
3. Integrated SSE report streaming and WebSocket quote updates into user-facing dashboards.
4. Extended portfolio UX with an earnings calendar and a risk dashboard (volatility/drawdown/VaR charts).

### 4.4 면접 핵심 질문/답변 포인트

1. Q: React Query를 왜 썼나요?  
   A: 서버 상태를 중앙 정책(staleTime, invalidate)으로 통제해 중복 요청과 동기화 복잡도를 줄였습니다.

2. Q: SSE와 WS를 나눈 기준은?  
   A: 보고서 생성은 서버 단방향 이벤트라 SSE가 적합하고, 실시간 시세 구독/해제는 양방향 제어가 필요해 WS가 적합합니다.

3. Q: Fullstack 관점에서 가장 어려웠던 통합 이슈는?  
   A: 인증 만료/재발급 시 동시 요청 레이스를 큐잉 방식으로 정리한 부분과 WS URL/프록시 경로 일관화였습니다.

### 4.5 코드 근거 파일

1. `frontend/src/App.js`
2. `frontend/src/api/http.js`
3. `frontend/src/hooks/queries/usePortfolio.js`
4. `frontend/src/pages/EarningsCalendar.js`
5. `frontend/src/pages/RiskDashboard.js`
6. `backend/src/main/java/com/sw103302/backend/controller/PortfolioController.java`

---

## 5. AI/ML 포지션용 재편집

### 5.1 무엇을 가장 앞에 두고 말할지

1. AI 서비스 설계: FastAPI 기반 도메인별 라우트 분리
2. 정량 분석: 리스크 지표 계산(변동성/MaxDD/VaR/ES/Beta)
3. 데이터 파이프라인: yfinance/FDR/Alpha/Finnhub 조합
4. LLM 통합: Ollama/OpenAI fallback + RAG 컨텍스트 주입

### 5.2 이력서 Bullet (국문)

1. FastAPI 기반 분석 엔진에서 기술적/기본적/뉴스 감성 지표를 결합한 투자 인사이트 API를 설계했습니다.
2. 포트폴리오 리스크 API에서 연환산 변동성, 최대낙폭, VaR95, Expected Shortfall, 베타, 분산점수를 계산해 대시보드 데이터로 제공했습니다.
3. 실적 캘린더 API를 구현해 보유 종목 이벤트를 기간 기반으로 집계/정렬하여 포트폴리오 의사결정 정보를 제공했습니다.
4. LLM 경로에 로컬(Ollama) 우선, OpenAI 폴백 전략과 RAG 검색 컨텍스트를 결합해 리포트 품질과 가용성을 보완했습니다.

### 5.3 Resume Bullet (영문)

1. Designed FastAPI-based analytics endpoints combining technical, fundamental, and sentiment signals.
2. Implemented portfolio risk metrics (annualized volatility, max drawdown, VaR/ES, beta, diversification) for dashboard consumption.
3. Built an earnings-calendar pipeline for portfolio holdings with date-window filtering and event normalization.
4. Integrated hybrid LLM routing (Ollama primary, OpenAI fallback) and RAG-based context enrichment.

### 5.4 면접 핵심 질문/답변 포인트

1. Q: 리스크 지표 계산에서 중요하게 본 점은?  
   A: 최소 데이터 길이 검증, NaN/Inf 안전 처리, 시장 벤치마크 정렬(alignment), 그리고 결과를 프론트 차트 소비 형태로 직렬화하는 부분입니다.

2. Q: LLM 품질/비용/가용성 균형은 어떻게 잡았나요?  
   A: 로컬 모델을 기본 경로로 두고 실패 시 OpenAI로 폴백해 가용성을 확보했습니다. 고비용 경로는 리포트/고부가 요청으로 제한했습니다.

3. Q: RAG를 왜 도입했나요?  
   A: LLM 단독 생성의 최신성/근거 약점을 보완하기 위해 ticker 기반 문서 검색 결과를 프롬프트에 결합했습니다.

### 5.5 코드 근거 파일

1. `ai/routes/intelligence.py`
2. `ai/routes/portfolio_risk.py`
3. `ai/routes/earnings.py`
4. `ai/routes/market_data.py`
5. `ai/routes/rag.py`
6. `ai/rag/store.py`

---

## 6. 포지션별 자기소개 템플릿 (복붙용)

### 6.1 백엔드 지원

“저는 Spring Boot 기반 서비스에서 인증/보안, 외부 연동 안정성, 운영 관측성 개선을 중심으로 일해왔습니다.  
Stock-AI에서는 refresh token 하드닝과 AI 연동 캐시/중복제거 구조를 구현해 보안성과 안정성을 동시에 개선했습니다.”

### 6.2 풀스택 지원

“저는 사용자 플로우를 끝까지 연결하는 풀스택 개발을 지향합니다.  
Stock-AI에서 React Query, 인증 재시도, SSE/WS 통합, 포트폴리오 실적/리스크 기능까지 FE-BE-AI를 일관된 제품 경험으로 연결했습니다.”

### 6.3 AI/ML 지원

“저는 모델 자체보다 ‘제품에 들어가는 분석 API’를 설계하는 데 강점이 있습니다.  
Stock-AI에서 리스크 계산 API, 실적 이벤트 API, LLM fallback과 RAG 결합 경로를 구현해 실제 사용자 의사결정에 연결했습니다.”

---

## 7. 제출 전략 (실무 팁)

1. 백엔드 공고: 섹션 3만 압축 제출(보안/운영 수치 강조).
2. 풀스택 공고: 섹션 4 + 공통 코어 메시지 중심 제출.
3. AI 공고: 섹션 5 중심으로 수치 계산/데이터 파이프라인 강조.
4. 어떤 포지션이든 “내가 바꾼 코드 파일 5개”를 마지막에 붙이면 신뢰도가 높아진다.
