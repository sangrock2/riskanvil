# Performance and Load Baseline

이 문서는 Stock-AI의 성능/부하 관리 기준을 정의한다. 목표는 "느리다"를 감으로 말하지 않고, 어떤 경로를 어떤 지표로 측정하고 어떤 수준을 합격선으로 볼지 명확하게 만드는 것이다.

## 1. What Exists Today

현재 저장소에는 이미 다음 관측성 자산이 들어 있다.

| 범주 | 현재 자산 | 위치 |
|---|---|---|
| 서버 헬스 | `/actuator/health`, `/health` | Backend, AI |
| Prometheus 계측 | `/actuator/prometheus` | Backend |
| 표준 웹 메트릭 | `http.server.requests` 등 Spring Boot 기본 메트릭 | Backend Actuator |
| 인증 플로우 메트릭 | `auth_flow_total`, `auth_flow_latency`, `auth_flow_stage_latency` | `AuthMetricsRecorder` |
| 캐시 이벤트 메트릭 | `app_cache_events_total` | `CacheMetricsRecorder` |
| AI 호출 메트릭 | `ai_client_latency`, `ai_client_requests_total` | `AiClient` |
| AI 호출 보조 메트릭 | `ai_call_latency_seconds`, `ai_call_total` | `AiCallMetrics` |
| API 호출 집계 | `api.calls`, `api.latency.ms` | `UsageService` |
| 오류 추적 | Frontend/Backend/AI Sentry | 각 서비스 SDK |
| 상관관계 추적 | `X-Request-Id`, 구조화 에러 응답 | Frontend/Backend/AI |
| 브라우저 UX 계측 | 나쁜 Core Web Vitals만 Sentry 전송 | Frontend `reportWebVitals.js` |
| 합성 모니터링 | `scripts/synthetic_monitor.py` | 운영 체크 |
| 단기 부하 테스트 | `scripts/load_test_short.py` | release gate |
| 강한 부하 테스트 | `scripts/load_test_heavy.py` | staging / monthly rehearsal |

즉 현재 문제는 "아무 계측도 없다"가 아니라 "계측은 일부 있는데, 기준선과 정기 보고 체계가 약하다"에 가깝다.

## 2. Gaps To Close

현재 남아 있는 빈 부분은 아래와 같다.

1. 운영 기준선이 저장소에 남아 있지 않다.
2. `api.latency.ms`는 호출부가 `durationMs`를 넘길 때만 기록되므로 커버리지가 부분적이다.
3. Web Vitals는 Sentry 연동까지 들어갔지만 릴리즈별 기준선 저장이 아직 없다.
4. CI가 부하 리포트 파일을 남기더라도 "이 수치를 합격선으로 본다"는 정식 문서와 보관 절차가 더 필요하다.

## 3. Primary User Journeys And SLO Targets

아래 목표치는 현재 아키텍처와 Render 운영 환경을 고려한 1차 목표치다. 세 번 이상의 릴리즈 데이터를 쌓은 뒤 재조정한다.

| 사용자 흐름 | 관측 지표 | 목표 |
|---|---|---|
| 로그인 | `http.server.requests` for `/api/auth/login` | warm p95 `< 1500ms`, cold-start first request `< 8000ms`, error rate `< 1%` |
| 토큰 refresh | `/api/auth/refresh` | p95 `< 800ms`, error rate `< 1%` |
| 시세 조회 | `/api/market/quote` | p95 `< 1200ms`, error rate `< 1%` |
| 인사이트 조회(test) | `/api/market/insights?test=true` | p95 `< 6000ms`, error rate `< 2%` |
| 포트폴리오 목록 | `/api/portfolio` | p95 `< 1000ms`, error rate `< 1%` |
| 워치리스트 추가/삭제 | `/api/watchlist` mutation | p95 `< 1000ms`, error rate `< 1%` |
| 포지션/포트폴리오 삭제 | `/api/portfolio/...` mutation | p95 `< 1200ms`, error rate `< 1%` |
| AI upstream | `ai_client_latency`, `ai_client_requests_total` | timeout + 5xx 합산 `< 2%` |
| 합성 모니터링 | `synthetic_monitor.py` success ratio | 24h 기준 `>= 99%` |

## 4. Release Gates

릴리즈 전 최소 기준은 아래와 같다.

### 4.1 Smoke Gate

- `scripts/e2e_smoke.py` 통과
- 수동 스모크 체크리스트 통과

### 4.2 Short Load Gate

기본 설정:

- `VUS=6`
- `ITERATIONS=8`
- `INSIGHTS_EVERY=4`
- 기준: `errorRate <= 5%`, `p95 <= 5000ms`

실행:

```bash
python scripts/load_test_short.py
```

산출물:

- `artifacts/reports/load-test-short-report.json`

### 4.3 Heavy Load Gate

기본 설정:

- stages: `12:60,24:120,36:180`
- 기준: `errorRate <= 8%`, `p95 <= 9000ms`
- 필요 시 `MIN_RPS` 추가

실행:

```bash
python scripts/load_test_heavy.py
```

산출물:

- `artifacts/reports/load-test-heavy-report.json`

## 5. Measurement Sources By Layer

### 5.1 Frontend

- 브라우저 Network 탭
- 사용자 체감 지표
- Sentry 프론트 이슈
- Sentry로 전송되는 degraded Web Vitals(LCP, INP, CLS)

### 5.2 Backend

- `/actuator/metrics`
- `/actuator/prometheus`
- `http.server.requests`
- `auth_flow_total`
- `auth_flow_latency`
- `auth_flow_stage_latency`
- `app_cache_events_total`
- `ai_client_latency`
- `ai_client_requests_total`
- `api.calls`
- `api.latency.ms`
- `X-Request-Id` 기반 로그 추적

### 5.3 AI

- `/health`
- Sentry 이슈 추이
- Backend에서 기록하는 AI upstream latency/counter

## 6. Reporting Cadence

| 주기 | 작업 | 출력물 |
|---|---|---|
| 매 배포 | `e2e_smoke.py`, short load | smoke 결과 + short report |
| 주 1회 | synthetic monitor 24h 결과 검토 | synthetic report |
| 월 1회 | heavy load + DR rehearsal | heavy report + rehearsal log |
| 장애 발생 시 | requestId, Sentry, Prometheus 추적 | incident note / postmortem |

## 7. Baseline Report Template

실제 수치를 쌓기 위한 최소 템플릿은 아래와 같다.

| Date | Env | Commit | Scenario | Total Requests | Error Rate | p50 | p95 | p99 | Notes |
|---|---|---|---|---|---|---|---|---|---|
| Pending | production | - | short load | - | - | - | - | - | 첫 기준선 수집 필요 |

추천 원칙:

1. 배포 후 첫 기준선은 빈 시간대가 아니라 실제 사용자 접속이 있는 시간대에 한 번 더 측정한다.
2. cold start와 warm state를 분리해서 기록한다.
3. 로그인 지연처럼 체감 이슈가 있으면 별도 항목으로 남긴다.

## 8. Current Interpretation Guide

### 로그인 10~15초는 정상인가

아니다. 현재 코드 기준 로그인은 아래 단계라서 정상적으로는 10~15초까지 오래 걸릴 이유가 적다.

- 사용자 조회
- BCrypt 비밀번호 검증
- 기존 refresh token 정리
- 새 refresh token 저장

따라서 이 수준의 지연은 보통 아래를 먼저 의심한다.

1. Render cold start
2. Postgres 연결 획득 지연
3. 2FA 계정의 Redis 경로 지연
4. 마지막으로 BCrypt cost

이 플로우는 정식 기준선 수집 시 별도 표로 관리해야 한다.

## 9. Immediate Instrumentation Improvements

다음 4가지는 문서화만으로 끝내지 말고 코드/대시보드 작업으로 이어져야 한다.

1. `auth_flow_stage_latency`를 기준으로 로그인 병목 구간을 실제 배포 수치로 기록
2. `app_cache_events_total`를 hit/miss/write_error 비율 대시보드로 연결
3. 프런트 Web Vitals를 릴리즈/환경 태그와 함께 Sentry에서 주간 리뷰
4. CI에서 short/heavy report artifact를 보존하고 릴리즈 노트에 링크

## 10. How To Use This Document In Interviews

이 문서는 "성능을 신경 썼다"가 아니라 아래를 보여주기 위한 문서다.

- 어떤 경로를 중요한 사용자 흐름으로 봤는지
- 어떤 메트릭으로 측정 가능한 상태를 만들었는지
- 목표치를 어디에 뒀는지
- 아직 없는 지표를 어떻게 채울 계획인지

즉 구현보다 운영 기준을 설계한 흔적을 보여주는 문서다.
