# Stock-AI 포트폴리오/면접용 기술 문서

기준일: 2026-02-25  
기준 코드 스냅샷: `C:\Users\Sw103\Desktop\stock-ai` 워크스페이스

가독성 요약 뷰(HTML): `docs/PORTFOLIO_READABLE.html`
원문 전체 뷰(HTML): `docs/PORTFOLIO_FULL.html`

## 1. 문서 목적과 사용 방법

이 문서는 Stock-AI 프로젝트를 포트폴리오 제출, 이력서 기술, 면접 설명에 바로 활용할 수 있도록 정리한 기술 도큐먼트다.

이 문서의 목표는 다음 3가지다.

1. 프로젝트의 실제 구현 구조를 한 번에 설명한다.
2. 기술 선택 이유(왜 이 스택을 썼는지)를 근거와 함께 제시한다.
3. 면접에서 예상 질문에 대해 설계 의도와 트레이드오프를 설명할 수 있게 준비한다.

주의 사항:

1. 본 문서는 기존 `docs/*` 문서 중 일부의 과거/계획성 서술이 아닌, 실제 코드/설정 파일 기준으로 재정리했다.
2. 성능 수치 중 코드에서 직접 확인 가능한 값(예: TTL, 타임아웃, 레이트리밋)과 실측이 필요한 값(예: p95 latency)은 구분했다.

---

## 2. 프로젝트 한 줄 요약

Stock-AI는 **개인 투자자의 분석 의사결정을 지원하는 AI 기반 풀스택 투자 분석 플랫폼**으로,  
`React SPA + Spring Boot API + FastAPI AI 엔진 + MySQL/Redis + Nginx/Prometheus/Grafana`로 구성된다.

핵심 가치:

1. 분석 자동화: 기술적/기본적/뉴스/리스크를 통합 분석.
2. 실행 보조: 백테스트, 모의투자, 포트폴리오 리밸런싱/리스크 대시보드 제공.
3. 운영 안정성: 캐시, 토큰 하드닝, 레이트리밋, 헬스체크, 관측성(메트릭/Request ID) 포함.

---

## 3. 시스템 아키텍처

### 3.1 전체 구조

```text
[React Frontend]
  - UI/UX, React Query 캐시, 인증 토큰 보유
  - WS(/ws/quotes), REST(/api/*)
          |
          v
[Nginx Reverse Proxy]
  - /api/* -> Backend
  - /ws/quotes -> Backend WS
  - rate limit, SSE/WS proxy, actuator 제한
          |
          v
[Spring Boot Backend]
  - 인증/인가, 도메인 로직, DB 저장, 캐시, 오케스트레이션
  - AI 서비스 호출(WebClient), SSE 스트리밍, WS 릴레이
          |
          v
[FastAPI AI Service]
  - 시장 데이터 수집(yfinance/FDR/Alpha/Finnhub)
  - 인사이트/리포트/백테스트/몬테카를로/리스크 계산
  - LLM(Ollama/OpenAI) + RAG
```

보조 인프라:

1. MySQL: 영속 데이터 저장(Flyway 마이그레이션).
2. Redis: 캐시 및 일부 인증 플로우 상태.
3. Prometheus/Grafana: 메트릭 수집/시각화.
4. Sentry(옵션): FE/BE/AI 오류 수집.

### 3.2 서비스 책임 분리

| 계층 | 책임 | 대표 구현 파일 |
|---|---|---|
| Frontend | 화면, 사용자 상호작용, 클라이언트 캐싱, 토큰 동기화 | `frontend/src/App.js`, `frontend/src/api/http.js`, `frontend/src/hooks/queries/*` |
| Backend | 보안, 도메인 규칙, 저장/조회, 외부(AI) 오케스트레이션 | `backend/src/main/java/com/sw103302/backend/controller/*`, `service/*` |
| AI Service | 데이터 수집, 수치 계산, LLM 리포트, 실시간 시세 소스 | `ai/routes/*`, `ai/data_sources/*`, `ai/analysis/*` |

### 3.3 코드 규모/구성 지표(정적 집계)

집계 기준: 현재 워크스페이스 파일 카운트 스크립트

| 항목 | 수치 |
|---|---|
| Backend Controller 파일 수 | 21 |
| Backend Service 파일 수 | 24 |
| Backend Repository 파일 수 | 22 |
| AI Route 파일 수 | 17 |
| Frontend Page 파일 수 | 21 |
| DB 마이그레이션 수 | 20 (`V1`~`V20`) |
| Backend 엔드포인트 매핑 수 | 74 (`@*Mapping`) |
| AI 엔드포인트/WS 매핑 수 | 34 (`@router.*`) |
| Frontend Route 수 | 22 |
| 테스트 파일 수 | Backend 16, Frontend 2, AI 2 |

대략적인 코드 라인 수(주요 영역):

1. Backend main 코드: 11,038 lines
2. Frontend src 코드: 25,331 lines
3. AI Python 코드: 5,110 lines

---

## 4. 기술 스택과 채택 이유

### 4.1 Frontend

| 기술 | 버전 | 채택 이유 | 트레이드오프 |
|---|---|---|---|
| React | 19.2.3 | 컴포넌트 기반 UI, 생태계 안정성, 장기 유지보수 용이 | CRA 기반 번들 최적화 한계 |
| React Router | 7.11.0 | 페이지 라우팅/보호 라우트 단순화 | 고급 데이터 라우팅 미사용 |
| TanStack Query | 5.90.20 | 서버 상태 캐싱/재시도/무효화 표준화 | 캐시 정책 학습 필요 |
| Recharts | 3.7.0 | 리스크 대시보드/사용량 시각화 빠른 구현 | 대용량 데이터 렌더 최적화 과제 |
| i18next/react-i18next | 25.x / 16.x | 한국어/영어 전환 대응 | 번역 키 관리 부담 |
| Sentry FE SDK | 8.x | 운영 중 프론트 오류 추적 | 샘플링/PII 정책 운영 필요 |

핵심 패턴:

1. `QueryClient`에서 도메인별 `staleTime`을 분리(`QUOTE` 30초, `FUNDAMENTALS` 6시간 등).
2. API 공통 모듈(`api/http.js`)에서 401 시 refresh 큐잉 재시도 로직 제공.
3. `App.js`에서 페이지 lazy loading + `ProtectedRoute`로 인증 분기.

### 4.2 Backend

| 기술 | 버전 | 채택 이유 | 트레이드오프 |
|---|---|---|---|
| Spring Boot | 4.0.0 | 엔터프라이즈급 안정성, 보안/데이터/운영 생태계 | 설정/구조가 비교적 무겁다 |
| Java | 21 | LTS, 생산성/성능 균형, 타입 안정성 | 러닝커브가 Python/JS 대비 높음 |
| Spring Security + JWT | - | 무상태 인증, API 서버 수평 확장 친화적 | 토큰 폐기/회전 정책 설계 필요 |
| JPA + MySQL | MySQL 8.4 | 관계형 모델 강점, 트랜잭션 안정성 | 복잡 쿼리 시 튜닝 필요 |
| Flyway | - | 스키마 변경 이력화/재현성 확보 | 마이그레이션 품질관리 필요 |
| Redis Cache | 7.x | AI 호출 비용/지연 완화 | 캐시 일관성 정책 필요 |
| Spring WebFlux WebClient | - | AI 서비스 호출 비동기/타임아웃 제어 | 장애 처리 시 정책 복잡도 증가 |
| Resilience4j | 2.2.0 BOM | 재시도/서킷브레이커 패턴 도입 | 과도 재시도시 부하 확대 가능 |
| Micrometer + Prometheus | - | 운영 지표 표준화 | 지표 해석 체계 수립 필요 |
| SpringDoc OpenAPI | 2.8.0 | API 협업 문서 자동화 | 설명 품질은 코드 리뷰 필요 |

### 4.3 AI Service

| 기술 | 버전 | 채택 이유 | 트레이드오프 |
|---|---|---|---|
| FastAPI | >=0.115 | 라우팅/검증/비동기 처리 간결 | 대규모 구조화 시 레이어링 필요 |
| yfinance + FDR + Alpha | - | US/KR 시장 데이터 소스 다변화 | 데이터 소스별 스키마 편차 |
| numpy/pandas/scipy/pandas_ta | - | 수치 계산/시계열 분석 생산성 | 계산식 검증 체계 필요 |
| websockets(Finnhub) | >=13 | 실시간 시세 수신 | 외부 WS 장애 대응 필요 |
| Ollama/OpenAI | - | 로컬/클라우드 LLM 선택지 확보 | 비용/지연/품질 편차 |
| ChromaDB + BeautifulSoup | - | RAG 인덱싱/검색 구현 단순화 | 데이터 정합성/품질 관리 필요 |

---

## 5. 핵심 도메인 플로우

### 5.1 인증/보안 플로우

구성 요소:

1. 로그인/회원가입: `AuthController`, `AuthService`
2. JWT 검증 필터: `JwtAuthenticationFilter`
3. 인증 레이트 리밋: `AuthRateLimitFilter`
4. 2FA: `TwoFactorService`(TOTP + 백업코드)
5. 리프레시 토큰 하드닝: `TokenHashService`, `RefreshTokenRepository`, `V20__harden_refresh_tokens.sql`

핵심 설계:

1. Access Token(기본 15분), Refresh Token(7일) 분리.
2. Refresh Token은 원문이 아닌 `token_hash(SHA-256 + optional pepper)` 기반 조회.
3. Refresh 시 토큰 로테이션(새 토큰 발급 + 기존 레코드 갱신).
4. 만료 토큰 정리 스케줄러(기본 30분 주기).
5. 2FA 활성 사용자는 로그인 시 즉시 JWT 대신 `pendingToken` 발급 후 검증 완료 시 토큰 발급.

면접 포인트:

1. “왜 해시 저장인가?”: DB 유출 시 토큰 원문 재사용 리스크 감소.
2. “왜 로테이션인가?”: 탈취된 refresh token 재사용 창구를 줄임.

### 5.2 분석/백테스트 플로우

분석:

1. FE가 `/api/analysis` 호출.
2. Backend `AnalysisService`가 사용자 검증 후 AI `/analyze` 호출.
3. 응답 JSON을 `analysis_runs`에 저장, 요약(action/confidence) 추출 저장.
4. 히스토리는 페이징+정렬 화이트리스트 기반 조회.

백테스트:

1. FE가 `/api/backtest` 호출.
2. Backend `BacktestService`가 AI `/backtest` 호출.
3. summary 메트릭(totalReturn, sharpe 등) 파싱 후 `backtest_runs` 저장.
4. 히스토리/상세 조회 제공.

설계 이유:

1. Backend가 이력 저장의 단일 진입점 역할을 수행해 감사 추적성을 유지.
2. AI 서비스에서 계산하고 Backend에서 결과 정규화/저장함으로써 책임 분리.

### 5.3 인사이트/리포트 캐싱 + SSE 스트리밍

구성:

1. `MarketCacheService`에서 `market_cache`(DB) + Redis(`ai_insights`, `ai_report`) 이중 캐시.
2. `InMemoryInFlightDeduplicator`로 동일 키 동시 요청 중복 실행 방지.
3. `/api/market/report/stream`에서 `open -> delta -> done` SSE 이벤트 전송.
4. 보고서는 600자 청크 전송, 최대 10분 타임아웃.

설계 이유:

1. AI 리포트 생성은 느리고 비용이 큰 연산이라 캐시+중복제거가 필수.
2. SSE는 “생성 중 체감” UX를 개선하고, 긴 요청 타임아웃 체감 부담을 낮춤.

### 5.4 실시간 시세 WebSocket 릴레이

구성:

1. Frontend `quoteWS`가 `/ws/quotes` 연결.
2. Backend `QuoteWebSocketHandler`가 세션별 구독 티커 관리.
3. Backend `QuoteWebSocketRelay`가 AI `/ws/quotes`와 단일 연결 유지.
4. 구독/해제 이벤트를 `QuoteSubscriptionEvent`로 relay에 전달.
5. relay 재연결 시 현재 구독 스냅샷 재전송.

설계 이유:

1. 외부 Finnhub 연결/키 노출을 프론트에서 제거.
2. Backend를 실시간 게이트웨이로 두어 인증/정책/모니터링 일원화.
3. 재연결 구독 동기화로 운영 중 연결 유실 복구.

### 5.5 포트폴리오 확장 기능: 실적 캘린더 + 리스크 대시보드

실적 캘린더:

1. Backend가 포트폴리오 보유 종목을 시장별로 그룹화.
2. AI `/earnings/calendar` 호출 후 이벤트 병합/정렬.
3. FE `/earnings` 페이지에서 기간 선택(30/60/90/180일) 기반 조회.

리스크 대시보드:

1. Backend가 현재 가격 배치 조회 후 보유 비중 계산.
2. AI `/portfolio/risk`에서 변동성/Max DD/VaR/ES/베타/분산/집중도 계산.
3. FE `/risk-dashboard`에서 KPI 카드 + 시계열(drawdown, rolling vol) 시각화.

설계 이유:

1. 단순 포트폴리오 조회를 “일정 기반 이벤트 관리 + 리스크 관리”로 확장해 제품 가치 상승.
2. 계산 집약 로직은 AI 서비스로 분리해 확장성과 실험 속도 확보.

### 5.6 모의투자(Paper Trading)

핵심 동작:

1. US/KR 계좌 자동 생성(초기잔고 상이).
2. 시장가 BUY/SELL 주문, 수수료 0.1% 반영.
3. 포지션 평균단가/평가손익/주문이력 계산 및 조회.

의미:

1. 분석 결과를 실제 자금 리스크 없이 “행동 시뮬레이션”으로 연결하는 브리지 기능.

### 5.7 챗봇 컨텍스트 주입

핵심 동작:

1. 대화/메시지를 DB에 저장(`chat_conversations`, `chat_messages`).
2. 최근 이력(최대 30 메시지)을 AI에 전달.
3. 사용자 포트폴리오/관심종목 컨텍스트를 프롬프트에 주입.

설계 이유:

1. 일반 챗봇이 아닌 “사용자 맞춤형 투자 어시스턴트”로 전환.
2. 대화 히스토리와 포트폴리오 문맥을 결합해 답변 관련성 향상.

---

## 6. 데이터 모델과 마이그레이션 전략

### 6.1 핵심 엔티티(22개)

주요 도메인 묶음:

1. 인증/보안: `User`, `RefreshToken`, `UserSettings`, `TotpSecret`, `BackupCode`
2. 분석/기록: `AnalysisRun`, `BacktestRun`, `ApiUsageLog`, `MarketCache`, `MarketReportHistory`
3. 투자/운용: `Portfolio`, `PortfolioPosition`, `Dividend`, `PriceAlert`
4. 상호작용: `WatchlistItem`, `WatchlistTag`, `ChatConversation`, `ChatMessage`
5. 모의투자: `PaperAccount`, `PaperPosition`, `PaperOrder`
6. 기타: `ScreenerPreset`

### 6.2 마이그레이션(`V1`~`V20`)

진화 포인트:

1. 초기 사용자/분석/백테스트 테이블에서 시작.
2. 캐시/사용량/워치리스트/포트폴리오/2FA/챗봇/모의투자 등으로 도메인 확장.
3. `V20`에서 refresh token 하드닝(`token_hash` 추가, unique index).

설계 이유:

1. Flyway 버전 고정으로 배포 간 스키마 재현성 확보.
2. 기능 추가를 마이그레이션 이력으로 남겨 도메인 성장 경로를 증명 가능.

면접 포인트:

1. “DB 스키마 변경을 어떻게 안전하게 운영했는가?”에 대해 Flyway 기반 답변 가능.

---

## 7. 성능, 안정성, 관측성

### 7.1 성능 최적화

확인 가능한 설정값:

1. Redis 캐시 TTL:
   - `ai_quote` 30초
   - `ai_prices` 5분
   - `ai_fundamentals` 6시간
   - `ai_news` 10분
   - `ai_insights` 10분
   - `ai_report` 30분
2. In-flight dedup로 동일 키 동시 실행 1회로 수렴.
3. AI WebClient connection pool:
   - maxConnections 100
   - pendingAcquireMaxCount 500
4. FE Query staleTime 분리로 도메인별 재요청 빈도 제어.

### 7.2 안정성/복원력

확인 가능한 설정값:

1. AI 호출 타임아웃:
   - 일반 30초
   - 리포트 180초
2. AI 호출 재시도:
   - 일반 기본 2회(백오프)
   - 리포트 기본 0회
3. WS 재연결:
   - Backend relay: 5초 후 재시도
   - Frontend client: 1초 시작, 최대 30초 지수 백오프
4. Graceful shutdown:
   - 서버 종료 시 SSE emitter 일괄 종료

### 7.3 관측성

구현 요소:

1. `X-Request-Id` 전파:
   - Frontend/Backend/AI 응답 헤더 노출
   - Backend WebClient가 AI 호출 시 request-id 전달
2. 메트릭:
   - `ai_client_latency`, `ai_client_requests_total`
   - `/actuator/prometheus` 제공
3. 사용량 로그:
   - endpoint, cached 여부, 상태코드, duration, error_text 저장
4. 운영 스택:
   - Prometheus scrape + Grafana datasource provisioning
5. Sentry:
   - FE/AI/BE 선택적 활성화

---

## 8. 보안 설계

핵심 정책:

1. 보안 필터체인 분리:
   - Actuator 체인과 App 체인 분리, actuator 허용 엔드포인트 최소화
2. 인증 엔드포인트 레이트리밋:
   - 애플리케이션 필터: IP당 60초 10회 제한
   - Nginx: `/api/auth/` 5 req/min
3. JWT 비밀키 길이 강제:
   - 32바이트 미만 시 앱 기동 실패
4. Refresh token 하드닝:
   - 해시 저장 + rotation + cleanup scheduler
5. 전역 예외 포맷 통일:
   - `ApiErrorResponse`, `X-Error-Code` 헤더
6. 정렬 필드 화이트리스트:
   - 분석/백테스트 히스토리 정렬 파라미터 검증

면접 포인트:

1. 인증 강화를 “기능 완성”이 아니라 “운영 보안”으로 연결해 설명 가능.
2. 레이트리밋을 Nginx+애플리케이션 이중 방어로 설계한 이유를 설명 가능.

---

## 9. DevOps/운영 구조

### 9.1 Docker Compose 서비스 구성

1. `mysql`
2. `redis`
3. `ai`
4. `backend`
5. `frontend`
6. `nginx`
7. `prometheus`
8. `grafana`

운영 의도:

1. 로컬/스테이징에서 전체 스택 재현이 쉬움.
2. 헬스체크를 서비스별로 선언해 의존 순서 명확화.

### 9.2 Nginx 운영 포인트

1. `/api/auth/`와 `/api/`를 분리해 rate-limit 정책 차등 적용.
2. `/ws/quotes` 업그레이드 헤더 처리로 WS 안정 연결.
3. `/api/`에서 SSE 대응(proxy_buffering off).
4. `/actuator/`는 도커 네트워크 대역만 허용.

---

## 10. 테스트/품질 보증

### 10.1 자동화 테스트 구성

Backend:

1. 서비스/플로우/컴포넌트/유효성/예외계약 테스트 포함(16개 클래스).
2. 예: `AuthServiceTest`, `TokenHashServiceTest`, `GlobalExceptionHandlerContractTest`.

Frontend:

1. 기본 앱 테스트 + WS URL 해석 테스트(2개).
2. 실시간 연결 URL 결정 로직 회귀 방지.

AI:

1. `test_earnings.py`, `test_portfolio_risk.py`로 신규 기능 최소 단위 검증.

### 10.2 CI 파이프라인(`.github/workflows/ci.yml`)

1. Frontend: `npm ci` -> test -> build
2. Backend: Gradle build/test(Java 21)
3. AI: syntax check -> ruff -> pytest

의미:

1. 멀티서비스 스택을 단일 CI로 검증해 회귀 리스크를 줄임.

---

## 11. 포트폴리오용 성과 정리(이력서/자기소개서 문장)

아래 문구는 코드 근거가 있는 내용만 선별해 작성했다.

### 11.1 이력서 Bullet 샘플 (국문)

1. React 19 + Spring Boot 4 + FastAPI 기반 3계층 투자 분석 플랫폼을 설계/구현하고, 20개 Flyway 마이그레이션으로 도메인 확장을 관리했습니다.
2. AI 분석/리포트 경로에 Redis TTL 캐시와 in-flight dedup을 적용해 외부 API 호출 중복을 구조적으로 억제했습니다.
3. Refresh Token 해시 저장(SHA-256+pepper), 토큰 로테이션, 만료 토큰 스케줄 정리를 구현해 인증 보안을 강화했습니다.
4. WebSocket 릴레이(Frontend-Backend-AI) 구조로 실시간 시세 구독을 중앙화하고 재연결 시 구독 자동 복구를 구현했습니다.
5. 실적 캘린더/리스크 대시보드를 추가해 포트폴리오 기능을 이벤트 관리와 리스크 관리 영역으로 확장했습니다.
6. Prometheus/Grafana 및 Request-ID 기반 추적을 도입해 장애 분석과 운영 관측성을 개선했습니다.

### 11.2 Resume Bullet 샘플 (영문)

1. Built a production-oriented 3-service stock analytics platform using React 19, Spring Boot 4, and FastAPI, with 20 Flyway migrations for schema evolution.
2. Implemented multi-layer caching and in-flight request deduplication to reduce redundant AI calls and stabilize latency under repeated requests.
3. Hardened auth with hashed refresh tokens, token rotation, and scheduled cleanup, improving credential security posture.
4. Designed a WebSocket relay architecture (Frontend -> Backend -> AI) with subscription synchronization on reconnect.
5. Delivered new portfolio features including an earnings calendar and a risk dashboard with volatility/drawdown/VaR metrics and time-series charts.

### 11.3 정량 근거(코드에서 확인 가능한 값)

| 항목 | 값 |
|---|---|
| Access Token 기본 만료 | 15분 |
| Refresh Token 유효기간 | 7일 |
| Refresh token cleanup cron | 기본 30분 주기 |
| 인증 레이트리밋(앱) | 60초당 10회/IP |
| 인증 레이트리밋(Nginx) | 5 req/min |
| AI 일반 타임아웃 | 30초 |
| AI 리포트 타임아웃 | 180초 |
| SSE 타임아웃 | 10분 |
| WS 프론트 재연결 | 1초~30초 지수 백오프 |
| 캐시 TTL(quote/prices/fundamentals) | 30초/5분/6시간 |

---

## 12. 면접 대응 가이드

### 12.1 “왜 이 구조를 선택했나?” 답변 템플릿

1. Backend와 AI를 분리해 도메인 책임(보안/저장 vs 계산/ML)을 분명히 했고, 각 레이어의 변경 속도를 분리했다.
2. AI 호출은 지연/실패 가능성이 높아 timeout/retry/cache/dedup를 조합해 시스템 전체 안정성을 우선시했다.
3. 인증은 단순 JWT 발급에서 끝내지 않고 refresh token 저장 방식까지 하드닝해 운영 보안을 강화했다.

### 12.2 예상 질문과 핵심 답변

1. Q: SSE와 WebSocket을 왜 둘 다 썼나요?  
   A: 보고서 생성은 서버 단방향 스트리밍이므로 SSE가 단순/효율적이고, 실시간 시세는 다중 구독/해제 제어가 필요해 WebSocket이 적합합니다.

2. Q: 캐시 일관성은 어떻게 보장하나요?  
   A: `refresh=true` 요청으로 강제 재계산 경로를 제공하고, Redis TTL + DB cache timestamp를 조합해 자동 갱신/강제 갱신을 분리했습니다.

3. Q: AI 서비스 장애 시 대응은?  
   A: 호출 타임아웃/재시도 정책과 캐시 fallback을 사용하고, 메트릭(`ai_client_requests_total`, latency)으로 장애를 관측합니다.

4. Q: 보안에서 가장 중요하게 본 지점은?  
   A: refresh token 원문 저장을 해시 저장으로 전환한 부분입니다. DB 유출 시 즉시 재사용 가능한 토큰 노출 위험을 줄였습니다.

5. Q: 왜 React Query를 도입했나요?  
   A: 서버 상태를 컴포넌트 로컬 상태로 관리하면 중복/재요청 제어가 어렵습니다. staleTime/invalidations로 정책을 중앙화했습니다.

6. Q: 실시간 구독 유실은 어떻게 처리하나요?  
   A: relay 재연결 시 현재 구독 스냅샷을 AI 소켓으로 재구독하도록 구현했습니다.

7. Q: 리스크 대시보드 계산을 AI 서비스에 둔 이유는?  
   A: 시계열 통계 계산은 Python 수치 생태계가 강점이고, Backend는 권한/보유종목 집계 및 API 계약 안정화에 집중했습니다.

8. Q: 기술 부채는 무엇인가요?  
   A: 일부 과거 문서와 실제 구현 간 불일치가 있으며, 엔드투엔드 테스트 범위 확장이 필요합니다.

9. Q: 확장 계획은?  
   A: 백엔드-AI 계약 테스트 강화, 시나리오 기반 E2E 도입, 모니터링 대시보드 표준화, 비동기 작업 큐 도입 순으로 계획합니다.

---

## 13. 프로젝트 개선 과제(정직한 진단)

1. 문서 동기화: 일부 기존 문서의 기술/버전/기능 설명이 실제 코드와 불일치.
2. 테스트 심화: FE E2E, AI 통합 테스트, 성능/부하 테스트 부재.
3. 계약 안정성: Backend-AI 응답 스키마 contract test 추가 필요.
4. 오류 처리 표준화: 일부 서비스는 예외를 RuntimeException으로 래핑해 세밀한 에러 코드 매핑이 어려움.
5. 운영 지표 확장: p95/p99 SLA 관점 대시보드/알림 룰 정교화 필요.

이 섹션을 면접에서 선제적으로 말하면 “구현자 시각 + 운영 시각”을 동시에 보여줄 수 있다.

---

## 14. 참고 근거 파일(핵심)

아키텍처/엔트리:

1. `frontend/src/App.js`
2. `backend/src/main/java/com/sw103302/backend/BackendApplication.java`
3. `ai/main.py`
4. `docker-compose.yml`
5. `nginx/nginx.conf`

보안/인증:

1. `backend/src/main/java/com/sw103302/backend/config/SecurityConfig.java`
2. `backend/src/main/java/com/sw103302/backend/service/AuthService.java`
3. `backend/src/main/java/com/sw103302/backend/service/TokenHashService.java`
4. `backend/src/main/resources/db/migration/V20__harden_refresh_tokens.sql`
5. `backend/src/main/java/com/sw103302/backend/component/AuthRateLimitFilter.java`

실시간/SSE:

1. `frontend/src/api/ws.js`
2. `backend/src/main/java/com/sw103302/backend/component/QuoteWebSocketHandler.java`
3. `backend/src/main/java/com/sw103302/backend/component/QuoteWebSocketRelay.java`
4. `ai/routes/realtime.py`
5. `backend/src/main/java/com/sw103302/backend/controller/ReportStreamController.java`

신규 기능:

1. `frontend/src/pages/EarningsCalendar.js`
2. `frontend/src/pages/RiskDashboard.js`
3. `backend/src/main/java/com/sw103302/backend/service/EarningsCalendarService.java`
4. `backend/src/main/java/com/sw103302/backend/service/RiskDashboardService.java`
5. `ai/routes/earnings.py`
6. `ai/routes/portfolio_risk.py`

테스트/CI:

1. `.github/workflows/ci.yml`
2. `backend/src/test/java/...`
3. `frontend/src/api/ws.test.js`
4. `ai/tests/test_earnings.py`
5. `ai/tests/test_portfolio_risk.py`

---

## 15. 최종 요약(면접 30초 버전)

“Stock-AI는 React, Spring Boot, FastAPI를 분리한 3계층 투자 분석 플랫폼입니다.  
저는 보안(토큰 해시/회전), 안정성(캐시+중복제거+재시도), 실시간성(WS 릴레이/SSE), 확장 기능(실적 캘린더/리스크 대시보드)을 중심으로 제품을 고도화했습니다.  
핵심은 기능 구현을 넘어서 운영 가능한 구조로 만든 점이며, 현재는 테스트/계약/문서 동기화를 다음 단계로 보고 있습니다.”

---

## 16. 포지션별 강조 문서

백엔드/풀스택/AI 포지션별 강조 포인트는 아래 문서에 분리 정리:

`docs/PORTFOLIO_ROLE_HIGHLIGHTS.md`
