# Engineering Handbook

이 문서는 Stock-AI의 기술 문서 정본입니다. (아키텍처/스택/개발/테스트/품질 기준 통합)

## 1. System Overview

Stock-AI는 다음 3개 애플리케이션 계층으로 구성됩니다.

- Frontend: React SPA (`frontend`)
- Backend: Spring Boot API (`backend`)
- AI Service: FastAPI 기반 정량/LLM 분석 (`ai`)

### 1.1 Runtime Topology

- 사용자 브라우저 -> Nginx -> Frontend
- Frontend -> Backend REST/WebSocket
- Backend -> AI Service (HTTP)
- Backend -> MySQL / Redis
- Monitoring: Prometheus + Grafana

## 2. Technology Stack (Code-Verified)

### 2.1 Frontend

- React `^19.2.3`
- React Router DOM `^7.11.0`
- TanStack React Query `^5.90.20`
- Recharts `^3.7.0`
- i18next + react-i18next
- Sentry React `^8.0.0`

### 2.2 Backend

- Spring Boot `4.0.0`
- Java Toolchain `21`
- Spring Security + JWT (`jjwt 0.12.6`)
- Spring Data JPA + Flyway + MySQL
- Redis Cache
- Resilience4j (CircuitBreaker/Retry)
- Spring WebSocket + SSE
- OpenAPI UI (`springdoc 2.8.0`)
- Sentry Spring Boot Starter

### 2.3 AI Service

- FastAPI + Uvicorn
- pandas / numpy / scipy / pandas_ta
- yfinance / FinanceDataReader
- OpenAI + Anthropic SDK
- ChromaDB + BeautifulSoup (RAG)
- sentry-sdk[fastapi]

### 2.4 Infrastructure

- Docker Compose
- MySQL 8.4 / Redis 7
- Nginx 1.27
- Prometheus / Grafana

## 3. Repository Structure

```text
stock-ai/
├─ frontend/
│  ├─ src/pages            # 라우트 페이지
│  ├─ src/components       # 공통 UI
│  ├─ src/api              # 백엔드 API 클라이언트
│  └─ src/i18n             # 번역 리소스
├─ backend/
│  └─ src/main/java/...    # controller/service/repository/entity
├─ ai/
│  ├─ main.py              # FastAPI 앱 엔트리
│  └─ routes/              # AI 라우트 모듈
├─ docs/
└─ docker-compose.yml
```

## 4. Backend API Surface (Controller 기준)

### 4.1 인증/보안

- `/api/auth/register`
- `/api/auth/login`
- `/api/auth/verify-2fa`
- `/api/auth/refresh`
- `/api/auth/logout`
- `/api/2fa/setup`
- `/api/2fa/verify`
- `/api/2fa/disable`

### 4.2 분석/시장

- `/api/analysis` (실행/이력/상세)
- `/api/market/search`
- `/api/market/insights`
- `/api/market/quote`
- `/api/market/prices`
- `/api/market/ohlc`
- `/api/market/report`
- `/api/market/report/stream` (SSE)
- `/api/market/report/export/{txt|json|pdf}`
- `/api/market/valuation`

### 4.3 투자 기능

- `/api/watchlist` + `/api/watchlist/tags`
- `/api/portfolio` (CRUD + position + rebalance)
- `/api/portfolio/{id}/earnings-calendar`
- `/api/portfolio/{id}/risk-dashboard`
- `/api/dividend/...`
- `/api/paper/...`

### 4.4 고급 분석

- `/api/screener`
- `/api/correlation`
- `/api/monte-carlo`
- `/api/chatbot/...`
- `/api/usage/...`

상세 계약은 [API.md](./API.md)를 기준으로 관리합니다.

## 5. Frontend Route Map

`frontend/src/App.js` 기준 주요 경로:

- Public: `/`, `/login`, `/register`, `/glossary`, `/learn`
- Auth Required:
  - `/dashboard`
  - `/analyze`
  - `/backtest`
  - `/insight-detail`
  - `/watchlist`
  - `/compare`
  - `/usage`
  - `/portfolio`
  - `/settings`
  - `/screener`
  - `/correlation`
  - `/chatbot`
  - `/dividends`
  - `/earnings`
  - `/risk-dashboard`
  - `/paper-trading`

## 6. AI Service Route Map

`ai/routes` 기준 핵심 라우트:

- Market Data: `/symbol_search`, `/quote`, `/prices`, `/ohlc`, `/fundamentals`, `/news`
- Analysis: `/insights`, `/report`, `/backtest`, `/correlation`, `/screener`, `/monte-carlo`
- Portfolio AI: `/efficient-frontier`, `/portfolio/risk`, `/find`
- Dividends/Earnings: `/dividend/history`, `/dividend/upcoming`, `/earnings/calendar`
- RAG: `/index`, `/search`, `/status`
- Health: `/health`

## 7. Data Model and Migration Strategy

Flyway 마이그레이션 파일(`backend/src/main/resources/db/migration`)이 DB 스키마의 단일 진실 소스입니다.

주요 테이블 그룹:

- 사용자/인증: `users`, `refresh_tokens`, `user_settings`, `two_factor_*`
- 분석: `analysis_runs`, `backtest_runs`, `market_cache`, `market_report_history`
- 포트폴리오: `portfolios`, `portfolio_positions`, `dividends`
- 관심종목: `watchlist`, `watchlist_tag`, `watchlist_item_tag`
- 부가 기능: `screener_presets`, `chat_*`, `price_alerts`, `api_usage_log`
- 모의투자: `paper_accounts`, `paper_positions`, `paper_orders`

## 8. Security Architecture

- JWT Access Token + Refresh Token
- Refresh Token 해시 저장(`V20__harden_refresh_tokens.sql`)
- 2FA(TOTP + backup code)
- Spring Security 기반 인가 정책
- CORS는 `APP_CORS_ALLOWED_ORIGIN_PATTERNS`로 제어
- Sentry/Request ID로 추적성 확보

## 9. Reliability and Performance

- WebClient timeout/retry 분리(일반 요청 vs report 요청)
- Resilience4j CircuitBreaker + Retry
- Redis 캐싱 + DB 캐시 + 중복요청 완화
- Frontend Query 캐시(staleTime)로 API 부하 절감
- SSE/WebSocket으로 실시간성 보강

## 10. Local Development Standard

### 10.1 Quick Start

```bash
docker compose up -d mysql redis
cd backend && ./gradlew bootRun
cd ai && uvicorn main:app --reload
cd frontend && npm install && npm start
```

### 10.2 Required Environment

`.env.example`을 복사해 `.env` 생성 후 최소 다음 값을 채웁니다.

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET`
- `AI_BASE_URL`
- `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`

## 11. Testing Strategy

- Backend: JUnit5 + Spring Boot Test + Testcontainers
- AI: pytest
- Frontend: Jest + React Testing Library
- Smoke/E2E 보조 스크립트: `scripts/`

기본 실행 예시:

```bash
cd backend && ./gradlew test
cd ai && pytest
cd frontend && npm test -- --watchAll=false
pwsh -File scripts/smoke.ps1
```

## 12. Engineering Conventions

- API 계약 변경 시 `API.md` 동시 수정
- 새 페이지 추가 시 `PRODUCT_MANUAL.md`의 화면/플로우 갱신
- 마이그레이션은 롤백/재실행 안정성 고려 (idempotent 우선)
- 장애 대응에 영향 있는 변경은 `OPERATIONS_MANUAL.md` 반영

## 13. OpenAPI and Typed Client Workflow

1. 백엔드 실행 후 OpenAPI 동기화:

```powershell
pwsh -File scripts/openapi_sync.ps1 -BackendBaseUrl http://localhost:8080
```

2. 타입 클라이언트 생성:

```powershell
pwsh -File scripts/generate_openapi_client.ps1
```

3. 생성 결과 확인:

```powershell
npm --prefix frontend run openapi:check
```

기준 파일:

- `docs/openapi/stock-ai.openapi.json`
- `frontend/src/api/generated/`
