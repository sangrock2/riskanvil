# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Stock-AI is a full-stack web application for AI-powered stock market analysis and backtesting. It consists of three services:
- **Frontend**: React 19 SPA with React Router v7
- **Backend**: Spring Boot 4.0.0 with Java 21
- **AI Service**: FastAPI (Python) for technical analysis and AI sentiment

## Build & Run Commands

### Frontend (frontend/)
```bash
npm start           # Dev server on port 3000
npm test            # Jest + React Testing Library
npm run build       # Production build
```

### Backend (backend/)
```bash
./gradlew clean build     # Full build with tests
./gradlew bootRun         # Dev server on port 8080
./gradlew test            # Run JUnit 5 tests
./gradlew build -x test   # Build without tests
```

### AI Service (ai/)
```bash
python -m uvicorn main:app --reload   # Dev server on port 8000
```

### Docker (Full Stack)
```bash
docker-compose up -d      # Start all services
docker-compose down       # Stop all services
```

### Smoke & Integration Tests
```bash
./scripts/smoke.ps1               # PowerShell smoke tests (requires running services)
./scripts/integration_tests.ps1   # Full integration test suite
./scripts/run_all_tests.ps1       # Build + unit + integration tests
```

## Architecture

### Service Communication
- Frontend → Backend: REST API at `/api/*`, JWT Bearer token auth
- Backend → AI Service: HTTP via WebClient with Resilience4j circuit breaker
- Request tracking: `X-Request-Id` header propagated across all services

### Backend Structure (backend/src/main/java/com/sw103302/backend/)
- `controller/` - REST endpoints (Analysis, Backtest, Auth, Market, Watchlist, etc.)
- `service/` - Business logic with Spring Cache and async support
- `entity/` - JPA entities mapped to MySQL tables
- `component/` - Filters (JWT, RequestId), AiClient, SseEmitterRegistry
- `config/` - Security, WebClient, Redis, Resilience4j configurations
- `dto/` - Request/Response objects

### Frontend Structure (frontend/src/)
- `pages/` - Route components (Analyze, Backtest, Watchlist, Compare, etc.)
- `components/` - Reusable UI (NavBar, TickerSearch, Chart components)
- `api/` - HTTP client with JWT auth (`http.js`), SSE support (`sseFetch.js`)
- `auth/` - Token management in localStorage

### Key Patterns
- **Authentication**: JWT-based, stateless, stored in localStorage
- **Caching**: Redis for market data and usage aggregations
- **Streaming**: Server-Sent Events via SseEmitterRegistry for real-time updates
- **Resilience**: Circuit breaker and retry on AI service calls
- **Deduplication**: InFlightDeduplicator prevents duplicate API processing

## Database

MySQL 8.4 with Flyway migrations at `backend/src/main/resources/db/migration/`. Migrations run automatically on startup.

**Test database**: H2 in-memory (configured in `application-test.properties`)

## Configuration

Main config: `backend/src/main/resources/application.properties`
- `ai.baseUrl` - AI service URL (default: http://localhost:8000)
- `security.jwt.secret` - JWT signing key
- `spring.data.redis.*` - Redis connection
- `valuation.*` - Stock valuation parameters (PE, PS, PB ratios, DCF settings)

Environment template: `.env.example`

## Testing Notes

- Backend tests use TestContainers and WireMock for integration testing
- Frontend has `?test=true` query param that routes to test data in AI service
- Test data files located in `ai/testdata/`

## Additional Documentation

All documentation is in the `docs/` directory:

- **docs/FEATURES.md** - Comprehensive list of currently implemented features
- **docs/ROADMAP.md** - Future feature roadmap with priorities and implementation guides
- **docs/NGINX_SETUP.md** - Nginx + Docker deployment guide
- **docs/API_DOCUMENTATION.md** - REST API reference
- **docs/ARCHITECTURE_DECISIONS.md** - Key architectural decisions
- **docs/DATABASE_SCHEMA.md** - Database schema reference
- **docs/DEVELOPMENT_GUIDE.md** - Local development setup guide
