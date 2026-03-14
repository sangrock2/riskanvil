# Operations Checklist (초보 운영자 상세판)

이 문서는 "처음 서비스를 운영하는 사람"을 기준으로 작성된 실행형 체크리스트입니다.  
한 줄씩 그대로 따라 하면 배포/점검/장애 대응까지 수행할 수 있게 구성했습니다.

관련 문서:
- 운영 정책/배경 설명: [OPERATIONS_MANUAL.md](./OPERATIONS_MANUAL.md)
- 배포값/환경변수 기준: [DEPLOY_RENDER.md](./DEPLOY_RENDER.md)
- 알림 템플릿: [ALERTING_TEMPLATES.md](./ALERTING_TEMPLATES.md)

---

## 0. 오늘 점검 기록 (항상 먼저 작성)

- [ ] 환경: `prod` / `staging`
- [ ] 점검자:
- [ ] 점검 시작 시각 (KST):
- [ ] 대상 커밋:
- [ ] Render 배포 ID(있으면):
- [ ] 점검 결과: `PASS / FAIL`
- [ ] FAIL 항목 이슈 링크:

---

## 1. 처음 1회 세팅 체크리스트 (서비스 시작 시 1번만)

### 1.1 Render 서비스 구성 확인

- [ ] Backend 서비스 존재 (`riskanvil-backend`, Web Service)
- [ ] AI 서비스 존재 (`riskanvil-ai`, Private Service)
- [ ] Frontend 서비스 존재 (Static Site)
- [ ] PostgreSQL 서비스 존재 (Managed)

확인 위치:
1. Render Dashboard 접속
2. 프로젝트(또는 Workspace)에서 서비스 카드 4개 확인

### 1.2 Backend 환경변수 확인

확인 위치:
1. Render -> `riskanvil-backend`
2. `Environment` 탭

필수값:
- [ ] `SPRING_PROFILES_ACTIVE=prod,postgres`
- [ ] `JWT_SECRET` 설정 (32바이트 이상)
- [ ] `REFRESH_TOKEN_PEPPER` 설정
- [ ] `DB_URL` 설정 (반드시 `jdbc:postgresql://...`)
- [ ] `DB_USERNAME` 설정
- [ ] `DB_PASSWORD` 설정
- [ ] `FLYWAY_ENABLED=true`
- [ ] `FLYWAY_BASELINE_VERSION=2`
- [ ] `JPA_DDL_AUTO=validate`
- [ ] `APP_CORS_ALLOWED_ORIGIN_PATTERNS`에 실제 프론트 URL 입력
- [ ] `AI_BASE_URL` 설정 (예: `https://riskanvil-ai.onrender.com`)

운영 안정값(권장):
- [ ] `SPRING_CACHE_TYPE=simple`
- [ ] `MANAGEMENT_HEALTH_REDIS_ENABLED=false`

주의:
- `DB_URL`에 `postgresql://...` 형식(=jdbc 없음)을 넣지 않습니다.
- DB 이름은 Render Postgres의 실제 DB 이름과 정확히 일치해야 합니다.

### 1.3 Frontend 환경변수 확인

확인 위치:
1. Render -> Frontend Static Site
2. `Environment` 탭

- [ ] `VITE_API_BASE_URL=https://riskanvil-backend.onrender.com`
- [ ] (선택) `VITE_WS_URL=https://riskanvil-backend.onrender.com`

### 1.4 AI 환경변수 확인

확인 위치:
1. Render -> `riskanvil-ai`
2. `Environment` 탭

- [ ] `DATA_PROVIDER` 확인 (`yfinance` 또는 `alpha_vantage`)
- [ ] `ALPHAVANTAGE_API_KEY` 입력 (alpha_vantage 쓸 때)
- [ ] `OPENAI_API_KEY` 입력 (OpenAI 기능 사용할 때)

### 1.5 Sentry 연결 (Frontend/Backend/AI)

- [ ] Frontend Sentry 프로젝트 생성 및 DSN 입력 (`VITE_SENTRY_DSN` 사용 시)
- [ ] Backend Sentry 프로젝트 생성 및 DSN 입력 (`SENTRY_DSN`)
- [ ] AI Sentry 프로젝트 생성 및 DSN 입력 (`SENTRY_DSN`)

검증:
1. 각 서비스 재배포
2. Sentry 프로젝트의 Issues 탭에 이벤트 수신되는지 확인

---

## 2. 배포 전 체크리스트 (릴리즈마다)

- [ ] GitHub `main` 브랜치에 배포 커밋 푸시 완료
- [ ] Render Backend/AI/Frontend 대상 브랜치가 `main`인지 확인
- [ ] 환경변수 오타/누락 재확인
- [ ] `DB_URL`, `AI_BASE_URL`, `CORS` 재확인
- [ ] 롤백 기준 확인 (직전 정상 커밋/배포 ID 기록)
- [ ] `docs/PATCH_NOTES.md` 업데이트
- [ ] 부하 테스트 계획값 확정 (`scripts/load_test_heavy.py` stage/목표치)
- [ ] 고부하 인증 제어값 확인 (`AUTH_RELOGIN_EVERY=0`, 로그인 쿨다운/백오프/스태거)

---

## 3. 배포 실행 순서 (권장)

1. AI 배포
2. Backend 배포
3. Frontend 배포
4. Backend CORS 최종 재확인 후 필요 시 재배포

Render에서 배포 방법:
1. 서비스 선택
2. `Manual Deploy` 클릭
3. `Deploy latest commit` 선택
4. Logs 탭에서 시작 로그 확인

---

## 4. 배포 직후 15분 점검 (반드시 수행)

### 4.1 헬스체크

- [ ] `https://riskanvil-ai.onrender.com/health` -> `{"status":"ok"}`
- [ ] `https://riskanvil-backend.onrender.com/actuator/health` -> `"status":"UP"`

### 4.2 인증/설정 API

- [ ] 회원 로그인 성공 (`/api/auth/login`)
- [ ] `GET /api/settings` 성공 (500 없어야 함)
- [ ] `PUT /api/settings` 저장 후 재조회 시 반영 확인

### 4.3 핵심 분석 API

- [ ] `POST /api/market/insights?test=false&refresh=false` 성공
- [ ] `POST /api/market/report?test=false&refresh=false&web=true` 성공

### 4.4 프론트 핵심 플로우

- [ ] 로그인
- [ ] 분석 페이지 진입
- [ ] 인사이트 로드 버튼 동작
- [ ] 포트폴리오 조회
- [ ] 워치리스트 조회

### 4.5 수동 스모크 테스트 (Render 배포 직후 권장)

사전 준비:
- [ ] 테스트 계정 이메일/비밀번호 준비
- [ ] 워치리스트 테스트용 티커 결정 (`AAPL` 권장)
- [ ] 프론트 URL 확인 (`https://<frontend>.onrender.com`)

부팅/초기 진입:
- [ ] 프론트 첫 진입 시 흰 화면 없이 `/login` 또는 홈 화면 렌더링
- [ ] 브라우저 콘솔에 치명 오류 없음
- [ ] 네트워크 탭에서 `index`/정적 자산 404 없음
- [ ] `VITE_API_BASE_URL` 대상이 실제 backend Render URL인지 확인

로그인:
- [ ] 로그인 폼 제출 후 성공적으로 대시보드 또는 기본 보호 페이지로 이동
- [ ] 로그인 직후 `GET /api/settings` 또는 초기 보호 API가 401/500 없이 성공
- [ ] 페이지 새로고침 후 세션이 유지되고 즉시 로그아웃되지 않음
- [ ] 새 탭에서 보호 페이지 진입 시 인증 상태가 비정상적으로 깨지지 않음

워치리스트:
- [ ] 워치리스트 페이지 첫 진입 성공
- [ ] 워치리스트 목록 조회 시 500 없음
- [ ] 테스트 티커 추가 성공
- [ ] 추가 직후 목록에 새 항목이 보임
- [ ] 같은 티커를 다시 추가할 때 중복 방지 메시지가 정상 노출
- [ ] 추가한 티커 삭제 성공
- [ ] 삭제 후 목록에서 즉시 사라짐

설정:
- [ ] 설정 페이지 첫 진입 성공
- [ ] `GET /api/settings` 응답이 500 없이 로드됨
- [ ] 언어 변경 후 즉시 UI 반영
- [ ] 새로고침 후 변경한 언어가 유지됨
- [ ] 기본 시장 변경 후 저장 성공
- [ ] 새로고침 후 기본 시장 값이 유지됨
- [ ] 알림 토글 변경 후 저장 성공
- [ ] 실패 시 raw JSON 대신 사용자 친화적 메시지 노출

분석:
- [ ] 분석 페이지 첫 진입 성공
- [ ] 기본 티커 또는 입력 티커로 `인사이트 로드` 성공
- [ ] 점수/의견/가격/뉴스 영역 렌더링 성공
- [ ] `업데이트` 실행 시 최신 조회 성공
- [ ] `상세 보기` 진입 성공
- [ ] 잘못된 티커 또는 실패 상황에서 raw JSON 대신 사용자 친화적 메시지 노출

포트폴리오:
- [ ] 포트폴리오 페이지 첫 진입 성공
- [ ] 포트폴리오 목록 조회 시 500 없음
- [ ] 새 포트폴리오 생성 성공
- [ ] 같은 이름으로 다시 생성 시 중복 방지 메시지가 정상 노출
- [ ] 포트폴리오 상세 진입 성공
- [ ] 포지션 추가 성공
- [ ] 같은 티커를 같은 시장으로 다시 추가 시 중복 방지 메시지가 정상 노출
- [ ] 포지션 삭제 성공
- [ ] 생성한 테스트 포트폴리오 삭제 성공

기록:
- [ ] 실패 시 브라우저 네트워크 탭의 상태코드, 응답 본문, `requestId` 기록
- [ ] 실패 시 Render backend 로그에서 같은 `requestId` 검색
- [ ] 점검 결과를 문서 상단 `오늘 점검 기록`에 반영

### 4.6 단기/고부하 점검 (릴리즈 중요도 높을 때 필수)

- [ ] `load_test_short.py` 1회 실행
- [ ] `load_test_heavy.py` 1회 실행 (stage 기반)
- [ ] 리포트 파일 저장 확인 (`artifacts/reports/*.json`)
- [ ] `errorRate`, `p95`, `rps` 임계치 PASS 확인
- [ ] `auth_login` 실패가 전체 실패 대부분인지 확인 (대부분이면 성능 판정 보류)
- [ ] `summary.authRateLimited429` 값 확인 후 허용 범위 초과 시 재실행

---

## 5. PowerShell 스모크 테스트 명령어 (복붙용)

```powershell
$BACKEND = "https://riskanvil-backend.onrender.com"
$AI = "https://riskanvil-ai.onrender.com"

# 1) Health
Invoke-RestMethod "$AI/health"
Invoke-RestMethod "$BACKEND/actuator/health"

# 2) Login (실계정 사용)
$loginBody = @{
  email = "test@example.com"
  password = "password1234"
} | ConvertTo-Json

$login = Invoke-RestMethod -Method Post `
  -Uri "$BACKEND/api/auth/login" `
  -ContentType "application/json" `
  -Body $loginBody

$token = $login.accessToken
$headers = @{ Authorization = "Bearer $token" }

# 3) Settings
Invoke-RestMethod -Method Get -Uri "$BACKEND/api/settings" -Headers $headers

# 4) Insights
$insightBody = @{
  ticker = "AAPL"
  market = "US"
  days = 90
  newsLimit = 20
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "$BACKEND/api/market/insights?test=false&refresh=false" `
  -ContentType "application/json" `
  -Headers $headers `
  -Body $insightBody

```
정상 기준:
- 2xx 응답
- 500 없음
- 응답에 `requestId`가 있고 로그 추적 가능
---

## 6. 일일 점검 (10~15분)

- [ ] Render 서비스 상태 모두 `Live`
- [ ] Backend/AI Health 정상
- [ ] 전일 대비 5xx 급증 없음
- [ ] Sentry 신규 치명 이슈 없음
- [ ] 핵심 API 1회 스모크 성공 (`/api/market/insights`)
- [ ] 최근 재배포/재시작 반복 없음
- [ ] DB 용량/연결 상태 이상 없음

실패 시:
1. 즉시 장애 등급 분류(P1/P2/P3)
2. 원인/영향 기록
3. 필요 시 롤백

---

## 7. 주간 점검 (30~60분)

- [ ] 상위 오류 Top 5 원인 정리
- [ ] 느린 API(p95/p99) 점검
- [ ] DB 무결성 샘플 점검
- [ ] 사용하지 않는 환경변수/시크릿 정리
- [ ] 알림 임계값 노이즈 조정
- [ ] 운영 문서 최신화

---

## 8. 월간 DR(재해복구) 점검

- [ ] 백업 존재 확인 (최근 백업 시각 기록)
- [ ] 복구 리허설 실행
- [ ] 핵심 기능 재검증
- [ ] 복구 결과 리포트 저장

권장 스크립트:

```powershell
$env:SYN_BASE_URL="https://riskanvil-backend.onrender.com"
$env:SYN_AI_HEALTH_URL="https://riskanvil-ai.onrender.com/health"
$env:SYN_DURATION_MINUTES="60"
python scripts/synthetic_monitor.py

$env:LOAD_BASE_URL="https://riskanvil-backend.onrender.com"
$env:LOAD_VUS="6"
$env:LOAD_ITERATIONS="8"
python scripts/load_test_short.py

$env:LOAD_BASE_URL="https://riskanvil-backend.onrender.com"
$env:LOAD_HEAVY_STAGES="20:120,40:180,60:240"
$env:LOAD_HEAVY_MAX_VUS="80"
$env:LOAD_HEAVY_WEIGHTS="quote:30,analysis_history:15,portfolio:15,insights_test:30,insights_refresh:10"
$env:LOAD_HEAVY_MAX_ERROR_RATE="0.08"
$env:LOAD_HEAVY_MAX_P95_MS="9000"
$env:LOAD_HEAVY_MIN_RPS="0"
$env:LOAD_HEAVY_AUTH_RELOGIN_EVERY="0"
$env:LOAD_HEAVY_AUTH_LOGIN_COOLDOWN_SECONDS="10"
$env:LOAD_HEAVY_AUTH_MAX_BACKOFF_SECONDS="60"
$env:LOAD_HEAVY_AUTH_STARTUP_STAGGER_MS="800"
python scripts/load_test_heavy.py
```

고부하 테스트 합격 기준(기본):
- [ ] `errorRate <= LOAD_HEAVY_MAX_ERROR_RATE`
- [ ] `p95ms <= LOAD_HEAVY_MAX_P95_MS`
- [ ] `rps >= LOAD_HEAVY_MIN_RPS` (0보다 크게 설정한 경우)
- [ ] `sampleFailures` 주요 원인이 5xx/timeout으로 폭증하지 않음
- [ ] `summary.authFailures`가 전체 실패의 과반이 아님
- [ ] `summary.authRateLimited429`가 전체 요청의 5% 이하(권장)
- [ ] `auth_login` 429가 지배적이면 인증제어값 조정 후 재실행

---

## 9. 장애 유형별 즉시 대응표

### 9.1 `insights` 500 발생

증상:
- 프론트: `/api/market/insights` 500
- 응답 본문에 `requestId`

조치:
1. 프론트 에러 응답에서 `requestId` 확보
2. Backend 로그에서 같은 `requestId` 검색
3. `Caused by` 첫 블록 원인 확인
4. AI 로그 동시간대 확인
5. 재현 여부 확인 후 코드 수정 또는 롤백

### 9.2 `cannot execute INSERT in a read-only transaction`

원인:
- read-only 트랜잭션에서 저장(save) 실행

조치:
1. 예외 스택의 서비스/라인 확인
2. GET API에서 write 발생 여부 확인
3. read/write 트랜잭션 분리 후 재배포

### 9.3 `database "... does not exist"`

원인:
- DB_URL의 DB명 오입력

조치:
1. Render Postgres 실제 DB명 확인
2. Backend `DB_URL` 교정
3. 재배포 후 health 재검증

### 9.4 `Driver ... does not accept jdbcUrl`

원인:
- `DB_URL`이 `postgresql://...`처럼 jdbc prefix 누락

조치:
1. `DB_URL`을 `jdbc:postgresql://...`로 수정
2. 재배포

### 9.5 `YFRateLimitError: Too Many Requests`

원인:
- yfinance 호출량 초과

조치:
1. 요청 간격 늘리기 (`refresh=true` 남발 금지)
2. 합성 모니터 간격 상향
3. 필요 시 `DATA_PROVIDER=alpha_vantage` + API Key 설정

### 9.6 `Redis health check failed`

원인:
- Redis 미연결 상태

조치:
1. Redis를 실제로 안 쓰면 `SPRING_CACHE_TYPE=simple`
2. `MANAGEMENT_HEALTH_REDIS_ENABLED=false`
3. 재배포

### 9.7 `load_test_heavy.py`에서 `auth_login 429` 대량 발생

원인:
- 인증 레이트리밋(`rule=auth`) 구간에서 로그인 시도가 동시 폭주

조치:
1. `LOAD_HEAVY_AUTH_RELOGIN_EVERY=0`으로 설정 (토큰 재사용)
2. `LOAD_HEAVY_AUTH_LOGIN_COOLDOWN_SECONDS=10` 이상으로 상향
3. `LOAD_HEAVY_AUTH_MAX_BACKOFF_SECONDS=60` 유지 또는 상향
4. `LOAD_HEAVY_AUTH_STARTUP_STAGGER_MS=800` 이상으로 상향
5. 리포트에서 `summary.authRateLimited429` 감소 확인 후 기준 판정

---

## 10. DB 데이터 점검 절차 (수동)

확인 항목:
- [ ] `users` 생성 증가 확인
- [ ] `market_cache` 최근 업데이트 확인
- [ ] `api_usage_log` 로그가 쌓이는지 확인

샘플 쿼리:

```sql
select current_database();
select now();
select count(*) as users_count from users;
select count(*) as market_cache_count from market_cache;
select count(*) as usage_24h
from api_usage_log
where created_at > now() - interval '1 day';
```

---

## 11. Sentry 연결 확인 체크리스트 (상세)

### 11.1 프로젝트 생성

- [ ] Frontend 프로젝트 생성 (JavaScript/React)
- [ ] Backend 프로젝트 생성 (Java/Spring)
- [ ] AI 프로젝트 생성 (Python/FastAPI)

### 11.2 DSN 주입

- [ ] Frontend: `VITE_SENTRY_DSN`
- [ ] Backend: `SENTRY_DSN`
- [ ] AI: `SENTRY_DSN`

### 11.3 동작 확인

- [ ] 각 서비스 재배포
- [ ] Sentry Issues 탭에서 이벤트 수신 확인
- [ ] Environment가 `prod`로 표시되는지 확인

---

## 12. 운영 로그 기록 템플릿 (복붙용)

```text
[운영점검]
- 일시(KST):
- 점검자:
- 환경:
- 커밋:

[결과]
- Health: PASS/FAIL
- Auth: PASS/FAIL
- Settings: PASS/FAIL
- Insights: PASS/FAIL
- Portfolio: PASS/FAIL

[장애]
- requestId:
- 증상:
- 원인:
- 조치:
- 재발방지:
```

---

## 13. 최종 승인 기준 (GO / NO-GO)

GO 조건:
- [ ] Health 정상
- [ ] Auth/Settings/Insights/Portfolio 핵심 플로우 PASS
- [ ] 치명도 높은 신규 Sentry 이슈 없음
- [ ] 5xx 급증 없음

NO-GO 조건:
- [ ] 핵심 API 1개라도 FAIL
- [ ] DB 연결/쓰기 장애 존재
- [ ] 배포 후 재시작 반복

---

## 14. 이번 프로젝트에서 가장 중요한 5개 확인 포인트

- [ ] `GET /api/settings` 가 500 없이 동작하는지
- [ ] `POST /api/market/insights` 가 2xx인지
- [ ] Backend `DB_URL`이 정확한 JDBC 형식인지
- [ ] AI rate limit 징후(`YFRateLimitError`)가 없는지
- [ ] 장애 시 `requestId`로 로그 추적이 가능한지
