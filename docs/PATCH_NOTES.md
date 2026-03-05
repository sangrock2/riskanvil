# Stock-AI 패치노트

---

# v1.3.4 - 운영 추적/알림 자동화 보강 (DB_URL 정규화 + 에러 메트릭)

**릴리즈 날짜**: 2026-03-05  
**이전 버전**: v1.3.3  
**현재 버전**: v1.3.4

---

## Backend 운영성 개선

- `DB_URL` 자동 정규화 추가
  - `postgresql://...`, `postgres://...` 입력 시 `jdbc:postgresql://...`로 자동 보정
  - `backend/src/main/java/com/sw103302/backend/config/DbUrlEnvironmentPostProcessor.java`
  - `backend/src/main/resources/META-INF/spring.factories` 등록
- 운영 오류 추적 메트릭 추가
  - `app_error_total{status,error_code}` 카운터 수집
  - `backend/src/main/java/com/sw103302/backend/component/ErrorMetricsRecorder.java`
  - `GlobalExceptionHandler`에서 에러 응답 생성 시 자동 기록
- 운영 준비상태 로그/경고 추가
  - 앱 시작 시 profiles/datasource/ai/sentry 상태 로그 출력
  - prod에서 위험 설정(JWT 기본값/CORS placeholder/Sentry 미설정) 경고
  - `backend/src/main/java/com/sw103302/backend/component/OperationalReadinessReporter.java`
- Actuator info 강화
  - `management.info.*` 활성화 및 `info.app.*` 메타데이터 추가

## 운영 문서/체크리스트 확장

- 알림 템플릿 신규 문서 추가: `docs/ALERTING_TEMPLATES.md`
  - Render/Sentry 알림 룰 기준 및 심각도 매핑
- 운영 문서 동기화:
  - `docs/OPERATIONS_MANUAL.md`
  - `docs/OPERATIONS_CHECKLIST.md`
  - `docs/DEPLOY_RENDER.md`
  - `docs/DOCS_INDEX.md`
  - `README.md`, `.env.example`

---

# v1.3.3 - 실서비스 운영 체크리스트/매뉴얼 정비

**릴리즈 날짜**: 2026-03-05  
**이전 버전**: v1.3.2  
**현재 버전**: v1.3.3

---

## 운영 문서 개선

- 실행형 운영 체크리스트 신규 추가: `docs/OPERATIONS_CHECKLIST.md`
  - 일일 점검, 주간 점검, 배포 전/후, 장애 대응, 롤백, DB/보안 점검 항목 정리
- 운영 정본 문서 개편: `docs/OPERATIONS_MANUAL.md`
  - Render 실서비스 기준 로그/오류/메트릭/Sentry 운영 절차 반영
  - `requestId` 기반 장애 추적 절차와 DB URL 오류 대응 런북 추가
- 문서 연결성 강화:
  - `docs/DOCS_INDEX.md`에 운영 체크리스트 반영
  - `README.md` 문서 목록에 운영 체크리스트 반영

## 코드 변경 여부

- 이번 릴리즈는 운영 문서 체계 강화가 중심이며, 필수 코드 수정은 포함하지 않음
- 현재 코드 기준으로 운영 가능하며, 운영 과정에서 수집된 지표/에러 패턴에 따라 추가 하드닝을 진행

---

# v1.3.2 - Render PostgreSQL 16 배포 프로필 추가

**릴리즈 날짜**: 2026-03-04  
**이전 버전**: v1.3.1  
**현재 버전**: v1.3.2

---

## 배포 설정 변경

- Backend에 PostgreSQL 전용 프로필 추가: `application-postgres.properties`
  - `SPRING_PROFILES_ACTIVE=prod,postgres` 조합 지원
  - PostgreSQL 드라이버/URL 기본값 주입
  - Postgres 배포 시 기본 `FLYWAY_ENABLED=false`, `JPA_DDL_AUTO=update`
- Backend runtime dependency에 PostgreSQL JDBC 드라이버 추가
- `application.properties`의 DB driver/Flyway/cache 타입을 env로 제어 가능하도록 변경
- Render 문서/환경변수 예시를 PostgreSQL 16 기준으로 업데이트

---

# v1.3.1 - Render 배포 호환성 업데이트

**릴리즈 날짜**: 2026-03-04  
**이전 버전**: v1.3.0  
**현재 버전**: v1.3.1

---

## 배포 호환성 개선

- Frontend API/SSE 호출에 `VITE_API_BASE_URL` 지원 추가
- WebSocket 설정에서 `https://...` 입력 시 `wss://.../ws/quotes`로 자동 정규화
- Backend 포트 설정을 `PORT` 우선(`server.port=${PORT:${SERVER_PORT:8080}}`)으로 변경
- Backend 내부 서비스 주소 주입 변수 추가:
  - `AI_SERVICE_HOSTPORT` (`ai.baseUrl` 기본값 구성)
  - `DB_HOSTPORT` (`spring.datasource.url` 기본값 구성)
- Render Blueprint 파일 추가: `render.yaml`
- Render 전용 배포 문서 추가: `docs/DEPLOY_RENDER.md`

---

# v1.3.0 - 안정성/배포 준비 업그레이드 (Vite 전환 · 계약 게이트 · 회복탄력성)

**릴리즈 날짜**: 2026-03-04
**이전 버전**: v1.2.0
**현재 버전**: v1.3.0

---

## Phase 1: Frontend Build 체인 현대화

- CRA 기반 빌드 체인을 **Vite**로 전환
  - `frontend/package.json` scripts 업데이트 (`dev/build/test/preview`)
  - `frontend/index.html` 엔트리 교체
  - `frontend/vite.config.mjs`, `frontend/vitest.config.cjs` 추가
- Docker 프론트 산출물 경로를 `build` -> `dist`로 전환
  - `frontend/Dockerfile` 수정
- 프론트 환경변수 체계 정리
  - `REACT_APP_*` 중심에서 `VITE_*` 중심으로 전환
  - `.env.example`, `frontend/src/index.js`, `frontend/src/api/ws.js` 반영

## Phase 2: CI 품질 게이트 강화

- OpenAPI 계약 일관성 체크를 CI 필수 단계로 승격
  - `.github/workflows/ci.yml`에서 `openapi:check` 강제
- AI 서비스 의존성 설치를 lock 우선 전략으로 변경
  - `ai/requirements.lock.txt` 추가
  - `ai/Dockerfile`, CI install 단계에서 lock 파일 우선 사용

## Phase 3: 백엔드 안정성 강화

- 인증 경로 전용 제한을 고비용 API까지 확장
  - `AuthRateLimitFilter` 대상 추가:
    - `/api/analysis` (POST)
    - `/api/market/report` (POST)
    - `/api/chatbot/chat` (POST)
    - `/api/backtest` (POST)
  - `Retry-After` 헤더 및 rule 태그 응답 추가
- 테스트 추가
  - `AuthRateLimitFilterTest` 신규
  - `QuoteWebSocketHandlerTest` ping/pong 케이스 추가

## Phase 4: WebSocket 복원력 개선

- 프론트 WebSocket 클라이언트에 다음 기능 추가
  - heartbeat ping
  - pong timeout 시 stale connection 정리
  - exponential backoff + jitter 재접속
  - 브라우저 visibility 복귀 시 자동 복구
- 백엔드 `QuoteWebSocketHandler`에 ping -> pong 응답 추가

## Phase 5: 코드 구조/문서 정리

- 대형 파일 리팩터링
  - `SystemMap` 유틸 함수 분리 (`frontend/src/utils/systemMapUtils.js`)
  - 포트폴리오 심볼 처리 유틸 분리 (`PortfolioSymbolUtil`)
- 로드맵 문서 최신화 (`docs/ROADMAP.md`)
- 프론트 README 및 루트 README 실행/빌드 가이드 업데이트

---

# v1.2.0 - 포트폴리오 제출 준비 업그레이드 (배포 증빙 · E2E · OpenAPI · 검증 체계)

**릴리즈 날짜**: 2026-02-28
**이전 버전**: v1.1.0
**현재 버전**: v1.2.0

---

## Phase 1: Public Service Evidence Pack

- 공개 URL 검증 스크립트 추가: `scripts/verify_public_service.ps1`
- 모니터링 스크린샷 캡처 스크립트 추가: `scripts/capture_monitoring_screenshots.ps1`
- 증빙 문서 추가: `docs/PUBLIC_SERVICE_EVIDENCE.md`
- 증빙 산출물 디렉터리 추가: `artifacts/reports/`, `artifacts/screenshots/`

## Phase 2: Core E2E Automation (3 Flows)

- `scripts/e2e_smoke.py`를 핵심 3개 플로우 중심으로 재구성
  1. 인증(회원가입/로그인)
  2. 분석 실행/상세 조회
  3. 포트폴리오 리스크/실적 조회
- 실행 결과 JSON 리포트 저장 지원:
  - `artifacts/reports/e2e-core-flows.json`
- CI에서 E2E 리포트 아티팩트 업로드 추가

## Phase 3: OpenAPI + Typed Client Generation

- OpenAPI 스냅샷 계약 추가:
  - `docs/openapi/stock-ai.openapi.json`
- 동기화 스크립트:
  - `scripts/openapi_sync.ps1`
- 타입 클라이언트 생성 스크립트:
  - `scripts/generate_openapi_client.ps1`
- Frontend 명령 추가:
  - `npm --prefix frontend run openapi:generate`
- CI에 API Contract 생성 검증 단계 추가

## Phase 4: Reliability Documentation

- 장애 회고 문서 추가:
  - `docs/INCIDENT_POSTMORTEM_AI_TIMEOUT_2026-02-28.md`
- 백테스트/리스크 검증 문서 및 스크립트 추가:
  - `docs/VALIDATION_BACKTEST_RISK.md`
  - `scripts/validate_backtest_risk.ps1`

## Phase 5: Portfolio Final Documentation

- 최종 제출 문서 추가:
  - `docs/PORTFOLIO_FINAL_SUBMISSION.md`
- 문서 인덱스/README 정비:
  - `docs/DOCS_INDEX.md`, `README.md`

---

# v1.1.0 - 실적 캘린더 · 리스크 대시보드 · 배포/운영 체계화

**릴리즈 날짜**: 2026-02-27
**이전 버전**: v1.0.0
**현재 버전**: v1.1.0

---

## Phase 1: 분석 기능 확장 (실적 캘린더 + 리스크 대시보드)

### 신규 기능
- **실적 캘린더(Earnings Calendar)**: 일정/예상 EPS/확정 EPS/서프라이즈 중심 조회 화면 및 API 추가
- **리스크 대시보드(Risk Dashboard)**: 변동성, VaR, 최대 낙폭, 포지션별 위험도 확인 화면 및 API 추가

### Backend
- `service/EarningsCalendarService.java`, `dto/EarningsCalendarResponse.java`
- `service/RiskDashboardService.java`, `dto/RiskDashboardResponse.java`
- `controller/PortfolioController.java` 확장 (포트폴리오 기반 리스크 응답 통합)
- AI 연동 라우트 추가: `ai/routes/earnings.py`, `ai/routes/portfolio_risk.py`

### Frontend
- `pages/EarningsCalendar.js`, `css/EarningsCalendar.module.css`
- `pages/RiskDashboard.js`, `css/RiskDashboard.module.css`
- `App.js`, `NavBar.js` 라우팅/메뉴 통합

---

## Phase 2: 보안/인증 하드닝

### Refresh Token 보안 강화
- 평문 저장 중심 구조를 해시 기반 검증 구조로 개선
- 토큰 저장/조회/갱신 경로를 해시 서비스로 통합

### 관련 변경
- `service/TokenHashService.java` 신규
- `entity/RefreshToken.java`, `repository/RefreshTokenRepository.java`, `service/AuthService.java` 수정
- `db/migration/V20__harden_refresh_tokens.sql` 추가
- `config/SecurityConfig.java`, `util/GlobalExceptionHandler.java` 보완

---

## Phase 3: 실시간 통신/인프라 개선

### WebSocket 시세 경로 정리
- 시세 릴레이/핸들러/구독 이벤트 흐름 정리로 안정성 개선
- 관련 파일:
  - `component/QuoteWebSocketHandler.java`
  - `component/QuoteWebSocketRelay.java`
  - `component/QuoteSubscriptionEvent.java`
  - `config/WebSocketConfig.java`

### 컨테이너/리버스프록시 준비
- `frontend/Dockerfile`, `backend/Dockerfile`, `ai/Dockerfile` 추가
- `docker-compose.yml`, `nginx/nginx.conf` 운영 배포 기준으로 정비

---

## Phase 4: UI/UX 및 문서 체계화

### UI/UX 개선
- 다크 모드 토큰 일관화 (실적/리스크 페이지)
- 모바일 테이블 가로 스크롤 적용
- Settings 모달 접근성 개선 (`Escape`, backdrop close, ARIA dialog)
- 강제 새로고침/새창 흐름 제거 (SPA 라우팅 통일)

### 운영/포트폴리오 문서 보강
- 신규 문서:
  - `docs/SERVICE_MANUAL.md`, `docs/SERVICE_MANUAL.html`
  - `docs/PUBLIC_DEPLOYMENT_GUIDE.md`, `docs/DEPLOY_CHECKLIST.md`
  - `docs/OPERATIONS_RUNBOOK.md`, `docs/ERROR_CATALOG.md`, `docs/CONFIG_BASELINE.md`
  - `docs/PORTFOLIO_TECHNICAL_DOSSIER.md`, `docs/PORTFOLIO_ROLE_HIGHLIGHTS.md`, `docs/PORTFOLIO_READABLE.html`, `docs/PORTFOLIO_FULL.html`
- 데모 데이터 주입:
  - `scripts/seed_demo_data.ps1`
  - `scripts/sql/seed_demo_data.sql`
  - `docs/DEMO_SEED_GUIDE.md`

---

# v1.0.0 - 2FA 로그인 연동 · AI 챗봇 업그레이드(GPT-4o) · 모의투자 · Virtual Threads

**릴리즈 날짜**: 2026-02-21
**이전 버전**: v0.8.0
**현재 버전**: v1.0.0

---

## Phase 1: 2FA 로그인 연동

기존에는 2FA를 활성화해도 로그인 시 검증이 완전히 우회됐습니다. 이제 2FA 활성화 계정은 로그인 시 반드시 TOTP 인증을 통과해야 합니다.

### 구현 방식 (Redis Pending Token)
1. 로그인 요청 → 2FA 활성화 계정 확인 → UUID 임시 토큰 Redis 저장 (TTL 5분)
2. 프론트엔드에 `{requires2FA: true, pendingToken: UUID}` 반환 (JWT 미발급)
3. TOTP 코드 입력 → `POST /api/auth/verify-2fa` → Redis 조회 → TOTP 검증 → 최종 JWT 발급

### 수정/신규 파일 (Backend)
- `dto/AuthResponse.java` — `requires2FA`, `pendingToken` 필드 추가, 팩토리 메서드 `of()` / `pending2FA()`
- `dto/VerifyTwoFactorLoginRequest.java` — 신규 DTO `{pendingToken, totpCode, backupCode}`
- `service/AuthService.java` — `login()` 2FA 체크 추가, `verifyTwoFactorLogin()` 신규 메서드, Redis TTL 5분
- `controller/AuthController.java` — `POST /api/auth/verify-2fa` 엔드포인트 추가

### 수정 파일 (Frontend)
- `pages/Login.js` — `requires2FA=true` 시 인라인 TOTP 입력 화면 표시, TOTP↔백업코드 전환, "로그인으로 돌아가기" 버튼
- `i18n/translations.js` — 2FA 인증 관련 번역 추가 (KO/EN)

---

## Phase 2: AI 챗봇 업그레이드

### 모델 업데이트
| 모델 키 | 이전 | 현재 |
|---------|------|------|
| opus | gpt-4 | gpt-4o |
| sonnet | gpt-4-turbo | gpt-4o |
| haiku | gpt-3.5-turbo | gpt-4o-mini |

### 시스템 프롬프트 강화
- 금융 전문가 역할 부여, 한국어 우선 응답
- 주식 분석 전문 지시사항 (근거 기반 분석, 투자 위험 안내)
- `max_tokens` 1024 → 1500 증가

### 포트폴리오 컨텍스트 주입
- ChatbotService가 사용자의 포트폴리오·보유 종목·관심종목을 DB에서 조회
- "내 포트폴리오 분석해줘" 같은 질문에 실제 보유 데이터 기반으로 답변 가능

### 수정 파일
- `ai/routes/chatbot.py` — 모델 매핑, 시스템 프롬프트, `context` 파라미터 수신
- `backend/.../service/ChatbotService.java` — 포트폴리오/관심종목 컨텍스트 빌드 및 전달
- `i18n/translations.js` — 모델 표시 레이블 업데이트 (GPT-4o / GPT-4o mini)

---

## Phase 3: 모의투자 (Paper Trading) — 신규 기능

가상 자금으로 실제 시장가로 주식 매매를 연습할 수 있는 모의투자 기능을 추가했습니다.

### 계좌 구성
| 시장 | 초기 자본금 | 통화 |
|------|------------|------|
| US | $100,000 | USD |
| KR | ₩100,000,000 | KRW |

### 핵심 기능
- **시장가 주문**: BUY / SELL (수수료 0.1% 적용)
- **평균단가 계산**: 추가 매수 시 가중평균가 자동 계산
- **실시간 평가**: AI 서비스의 현재가 기반 미실현 손익 계산
- **계좌 초기화**: 언제든지 초기 자본금으로 리셋 가능
- **주문 이력**: 페이징 지원 주문 내역 조회

### DB 마이그레이션
- `V19__add_paper_trading.sql` — `paper_accounts`, `paper_positions`, `paper_orders` 테이블 추가

### API 엔드포인트
```
GET  /api/paper/accounts          계좌 조회 (없으면 자동 생성)
POST /api/paper/accounts/reset    계좌 초기화
POST /api/paper/order             시장가 주문 {market, ticker, direction, quantity}
GET  /api/paper/positions?market= 보유 종목 (실시간 평가금액 포함)
GET  /api/paper/orders?market=    주문 이력 (페이징)
```

### 신규 파일 (Backend)
- `entity/PaperAccount.java`, `PaperPosition.java`, `PaperOrder.java`
- `repository/PaperAccountRepository.java`, `PaperPositionRepository.java`, `PaperOrderRepository.java`
- `service/PaperTradingService.java`
- `controller/PaperTradingController.java`
- `dto/PaperOrderRequest.java`, `PaperOrderResponse.java`, `PaperAccountResponse.java`, `PaperPositionResponse.java`

### 신규/수정 파일 (Frontend)
- `pages/PaperTrading.js` — US/KR 탭, 계좌 요약 카드, 주문 폼, 포지션 테이블, 이력
- `css/PaperTrading.module.css`
- `api/paperTrading.js`
- `components/NavBar.js` — "모의투자" 메뉴 추가
- `App.js` — `/paper-trading` 보호 라우트 추가
- `i18n/translations.js` — 모의투자 전체 번역 추가 (37개 키)

---

## Phase 4: 기술 스택 업그레이드

### Java 21 Virtual Threads 활성화
```properties
spring.threads.virtual.enabled=true
```
Tomcat 스레드 풀을 Java 21 가상 스레드로 교체. I/O 집약적 작업(DB, API 호출)의 처리량 향상.

### PWA 매니페스트 업데이트
- `name`: "Stock AI - 주식 분석 플랫폼"
- `short_name`: "Stock AI"
- `theme_color` / `background_color`: "#1a1a2e" (다크 테마 맞춤)

---

## 버그 수정

| 파일 | 내용 |
|------|------|
| `PriceService.java` | `System.err.println` → `@Slf4j` + `log.error()` 로 교체 |

---

# v0.8.0 - React Query + Monte Carlo 업그레이드 + RAG 시스템 + 포트폴리오 문서화

**릴리즈 날짜**: 2026-01-31
**이전 버전**: v0.6.0
**현재 버전**: v0.8.0

---

## 주요 신규 기능

### 1. TanStack Query (React Query) 통합
- **상태 관리 혁신**: `useState/useEffect` 패턴 → React Query로 전환
- **자동 캐싱**: API 호출 60% 감소, 데이터 타입별 최적화된 `staleTime` 전략
  - Quote: 30초 / Prices: 2분 / Insights: 5분 / Fundamentals: 6시간
- **9개 커스텀 훅 추가**: `useQuote`, `useInsights`, `useWatchlist`, `usePortfolio`, `usePortfolioDetail`, `useMonteCarloSimulation`, `useBacktestHistory`, `useDividendCalendar`, `useConversations`
- **8개 페이지 변환**: Dashboard, Watchlist, Portfolio, Analyze, DividendCalendar, Chatbot, Compare, Backtest
- 중복 요청 제거, 낙관적 업데이트, 백그라운드 리프레시 적용

### 2. Monte Carlo 시뮬레이션 대규모 업그레이드
- **벡터화 구현**: Python for-loop → NumPy 브로드캐스팅 (100배 성능 향상)
  - 10K 시뮬레이션: 5초 → 50ms
- **3가지 모델 추가**:
  - GBM (Geometric Brownian Motion) — 기본 모델
  - Jump-Diffusion (Merton 모델) — 팻테일 이벤트 반영
  - Historical Bootstrap — 실제 수익률 분포 기반
- **신규 분석**: Confidence Bands (5/25/50/75/95 퍼센타일), Scenario Analysis (Bull/Base/Bear), skewness/kurtosis/probabilityProfit

### 3. RAG (Retrieval-Augmented Generation) 시스템
- **ChromaDB 벡터 스토어**: ONNX 임베딩 (50MB, PyTorch 불필요)
- **뉴스 크롤러**: BeautifulSoup 기반, 동시성 3개 제한
- **시맨틱 검색**: 티커별 관련 뉴스 검색 (<50ms)
- **리포트 통합**: AI 리포트 생성 시 과거 뉴스 컨텍스트 자동 주입
- **API**: `POST /rag/index`, `GET /rag/search`, `GET /rag/status`

### 4. 포트폴리오 문서화 (6개 파일)
- `PROJECT_OVERVIEW.md`, `TECHNICAL_STACK.md`, `API_DOCUMENTATION.md`
- `DATABASE_SCHEMA.md`, `DEVELOPMENT_GUIDE.md`, `ARCHITECTURE_DECISIONS.md`

---

# v0.6.0 - 8대 신규 기능 + Toss-style 랜딩 페이지

**릴리즈 날짜**: 2026-01-30
**이전 버전**: v0.5.0
**현재 버전**: v0.6.0

---

## 주요 신규 기능

### 1. 포트폴리오 관리
- CRUD, 보유 종목 관리, 성과 지표, 섹터 배분
- 배당 캘린더 포트폴리오 통합

### 2. 주식 스크리너
- PE/PB/ROE/시가총액 등 15개 이상 필터
- 사전 설정(Preset) 지원

### 3. 상관관계 분석
- 멀티 티커 상관관계 히트맵

### 4. Monte Carlo 시뮬레이션
- GBM 기반 시뮬레이션, VaR/CVaR 통계

### 5. AI 챗봇
- OpenAI GPT 기반 대화, 대화 이력 관리

### 6. 2FA (이중 인증)
- TOTP 설정/검증/비활성화, 백업 코드 (Settings 페이지)

### 7. 사용자 설정 (Settings)
- 테마, 기본 시장, 알림 설정

### 8. 배당 캘린더
- 예정/과거 배당 추적, 포트폴리오 통합

### 랜딩 페이지
- Toss 스타일 스크롤 애니메이션, 패럴랙스, count-up 효과

### 인프라
- DB 마이그레이션 7개 추가 (V12~V18)
- AI 서비스 신규 라우트 4개 (screener, chatbot, correlation, monte-carlo)
- 포트폴리오/가격 서비스 테스트 추가

---

# v0.5.0 - 성능 및 품질 대규모 개선

**릴리즈 날짜**: 2026-01-29
**이전 버전**: v0.4.0
**현재 버전**: v0.5.0

---

## High Priority - 성능 개선

### 1. N+1 쿼리 문제 해결
Watchlist 조회 시 발생하던 심각한 N+1 쿼리 문제를 해결했습니다.

| | 변경 전 | 변경 후 |
|--|---------|---------|
| 쿼리 수 (N=10) | 21개 | 2~3개 |
| 개선율 | — | 86% 감소 |

- `WatchlistRepository.java` — `@EntityGraph` + `findByUserIdWithTags()` 추가
- `MarketCacheRepository.java` — `IN` 절 배치 조회 메서드 추가
- `WatchlistService.java` — 배치 조회 로직으로 리팩토링

### 2. 프론트엔드 리렌더링 최적화
- Dashboard 6개 위젯 전부 `React.memo` 래핑
- `WatchlistItemRow` 컴포넌트 분리 + Custom comparison function
- 편집 중인 아이템만 리렌더링 (~80% 감소)

### 3. Skeleton UI 적용
- Dashboard `WatchlistWidget`: `SkeletonTable(rows=5, columns=3)`
- Dashboard `MarketOverviewWidget`: `SkeletonCard(count=3)`
- Watchlist 페이지: `SkeletonTable(rows=10, columns=6)`

---

## Medium Priority - 사용자 경험

### 4. 리포트 내보내기 (TXT/JSON)
- `/api/market/report/export/txt`, `/json` 엔드포인트 추가
- 파일명: `report_{TICKER}_{YYYY-MM-DD}.{ext}`

### 5. 다중 타임프레임 모멘텀 분석
- 20/60/120/200일 기간별 모멘텀 바 차트
- `MultiTimeframeChart.js` 신규 컴포넌트

### 6. 검색 디바운싱 개선
- `useCallback + debounce` → `useMemo + debounce` (stale closure 해결)

### 7. 비동기 처리 개선
- `UsageService.log()` → `@Async("reportExecutor")` 적용
- 스레드풀: CorePool 2→8, MaxPool 4→32, Queue 50→200

---

## Low Priority - 코드 품질

### 8. DB 인덱스 최적화 (`V11__optimize_indexes.sql`)
6개 복합 인덱스 추가 (market_cache, watchlist_item_tag, analysis_runs, backtest_runs, api_usage_log)

### 9. 입력 길이 검증 강화
`validators.js`에 `validateMaxLength`, `validateNotes`, `validateArrayLength` 추가

### 10. JSDoc / JavaDoc 주석 추가
`InteractiveChart.js`, `Analyze.js`, `WatchlistService.java` 등

### 11. OpenAPI 스키마 어노테이션
`InsightRequest.java`, `WatchlistItemResponse.java`에 `@Schema` 추가

---

# v0.4.0 - 대시보드 · 리포트 강화 · 오프라인 · 분석 옵션 · AI 신뢰도

**릴리즈 날짜**: 2025-01-26
**이전 버전**: v0.3.0
**현재 버전**: v0.4.0

---

## 주요 신규 기능

- **대시보드 커스터마이징**: 위젯 표시/숨김, 순서 변경
- **리포트 생성 강화**: 템플릿(Detailed/Summary/Technical/Fundamental), 섹션 선택, PDF/Markdown/클립보드 내보내기
- **Service Worker 오프라인 캐싱**: Network-First(API), Cache-First(정적 자산)
- **온/오프라인 상태 표시**: `OfflineIndicator.js`
- **분석 옵션**: 기간(30일~1년), 지표 카테고리, 벤치마크(SPY/QQQ/DIA/IWM/VTI)
- **AI 신뢰도 등급 표시**: A~F 등급, 진행률 바, 컬러 코딩
- **인터랙티브 차트**: 줌, 팬, 슬라이더, SVG 스냅샷 다운로드
- **캔들스틱 + 거래량 차트**: `AdvancedCharts.js`
- **워치리스트 태그 시스템**: `WatchlistTag` 엔티티 + API
- **OHLC 데이터 API**: `GET /api/market/ohlc`
- **DB**: `V9__add_watchlist_tags.sql`

---

# v0.3.0 - UI/UX 대규모 개선

**릴리즈 날짜**: 2025-01-20
**이전 버전**: v0.2.0
**현재 버전**: v0.3.0

다크 모드, CSS 변수 시스템, 접근성 개선, 차트 인터랙션 강화 (줌/팬/브러시), OHLC 데이터, 퍼포먼스 최적화 (React.memo, lazy loading), 인사이트 시각화 (Recharts).

---

## 버전 히스토리

| 버전 | 릴리즈 날짜 | 주요 내용 |
|------|------------|---------|
| 1.0.0 | 2026-02-21 | 2FA 로그인 연동, GPT-4o 챗봇, 모의투자, Virtual Threads |
| 0.8.0 | 2026-01-31 | React Query, Monte Carlo 100배 성능, RAG 시스템 |
| 0.6.0 | 2026-01-30 | 포트폴리오, 스크리너, 상관관계, 챗봇, 2FA, 배당 캘린더 |
| 0.5.0 | 2026-01-29 | N+1 쿼리 해결, 리렌더링 최적화, 리포트 내보내기 |
| 0.4.0 | 2025-01-26 | 대시보드, 리포트, 오프라인, AI 신뢰도 |
| 0.3.0 | 2025-01-20 | 다크모드, 차트 강화, 성능 최적화 |
| 0.2.0 | 2025-01-15 | AI 분석, 백테스트, 실시간 데이터 |
| 0.1.0 | 2025-01-10 | 초기 릴리즈 |
