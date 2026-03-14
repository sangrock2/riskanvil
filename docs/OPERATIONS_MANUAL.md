# Operations Manual

이 문서는 Stock-AI 실서비스 운영(특히 Render 배포)을 위한 정본입니다.  
실행용 점검 항목은 [OPERATIONS_CHECKLIST.md](./OPERATIONS_CHECKLIST.md)와 함께 사용합니다.
운영 체크리스트는 `일일 빠른 점검`과 `릴리즈 전 전체 기능 검증 매트릭스`를 모두 포함합니다.

## 1. 운영 범위

- Frontend: Render Static Site
- Backend: Render Web Service (Spring Boot)
- AI: Render Private Service (FastAPI)
- Database: Render PostgreSQL (Managed)
- Error Tracking: Sentry (Frontend/Backend/AI)

## 2. 필수 운영 환경변수

### 2.1 Backend

- `SPRING_PROFILES_ACTIVE=prod,postgres`
- `JWT_SECRET` (32바이트 이상 랜덤 문자열)
- `REFRESH_TOKEN_PEPPER` (운영 필수)
- `DB_URL` (반드시 JDBC 형식)
- `DB_USERNAME`, `DB_PASSWORD`
- `FLYWAY_ENABLED=true`
- `FLYWAY_BASELINE_VERSION=2`
- `JPA_DDL_AUTO=validate`
- `APP_CORS_ALLOWED_ORIGIN_PATTERNS`
- `AI_BASE_URL` 또는 `AI_SERVICE_HOSTPORT`
- 선택: `SENTRY_DSN`, `SENTRY_TRACES_SAMPLE_RATE`

예시:

```env
DB_URL=jdbc:postgresql://dpg-xxxx-a:5432/your_db_name
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password
```

### 2.2 AI

- 선택: `SENTRY_DSN`
- 선택: `OPENAI_API_KEY`, `ALPHAVANTAGE_API_KEY`

### 2.3 Frontend

- `VITE_API_BASE_URL=https://<backend-domain>`
- 선택: `VITE_SENTRY_DSN`

## 3. 관측성(Observability) 표준

### 3.1 Health

- Backend: `/actuator/health`
- AI: `/health`

### 3.2 Logs

- Render Dashboard -> 서비스 -> `Logs`에서 확인
- 장애 분석은 `requestId` 기준으로 추적
- 에러 로그는 최소 `timestamp`, `status`, `path`, `requestId`를 남겨야 함

### 3.3 Error Tracking

- Sentry를 Frontend/Backend/AI 각각 연결
- 알림 규칙:
  - 5분 내 에러 급증
  - 신규 이슈 발생
  - 재오픈 이슈 발생
- 상세 알림 템플릿: `docs/ALERTING_TEMPLATES.md`

### 3.4 Metrics

- Backend: `/actuator/prometheus`
- 최소 추적 지표:
  - HTTP 5xx 비율
  - 응답시간 p95/p99
  - `auth_flow_latency`, `auth_flow_stage_latency`
  - `app_cache_events_total`
  - AI 타임아웃 비율
  - DB 연결 실패 건수
  - `app_error_total`(error_code/status 기준 API 오류 카운트)

### 3.5 운영 자동 진단(Backend)

- `DB_URL` 자동 보정:
  - `postgresql://...` 또는 `postgres://...` 입력 시 `jdbc:postgresql://...`로 자동 정규화
- 앱 기동 시 운영 기준 로그 출력:
  - active profiles, datasource, aiBaseUrl, cacheType, sentryEnabled
- prod 프로필에서 기본값/리스크 설정 경고:
  - 개발용 JWT 시크릿 사용
  - CORS placeholder 도메인 미변경
  - Sentry 미설정

## 4. 운영 절차

### 4.1 일일 운영

1. 서비스 상태 `Live` 확인
2. 헬스체크 2종 확인(Backend/AI)
3. 최근 배포 실패/재시작 반복 여부 확인
4. Sentry 신규 에러 확인
5. 핵심 사용자 플로우 스모크 테스트

### 4.2 자동 검증 도구

- 24시간 합성 모니터링:
  - `python scripts/synthetic_monitor.py`
  - 리포트: `artifacts/reports/synthetic-monitor-report.json`
- 단기 부하 테스트:
  - `python scripts/load_test_short.py`
  - 리포트: `artifacts/reports/load-test-short-report.json`
- 월간 DR 리허설:
  - `pwsh -File scripts/dr_rehearsal_run.ps1 ...`
  - 리포트: `artifacts/reports/dr-rehearsal/dr-rehearsal-summary.json`

### 4.3 배포 운영

1. 배포 전 체크리스트 수행
2. Render 배포 시작
3. 배포 로그에서 서버 시작/포트 바인딩 확인
4. 배포 후 체크리스트 수행
5. 이상 징후 시 즉시 롤백

### 4.4 롤백 기준

- 로그인/분석/포트폴리오 핵심 기능 중 1개 이상 장애
- 5xx 급증이 5분 이상 지속
- DB 연결 실패가 연속 발생

롤백 방법:

1. Render `Manual Deploy`로 직전 정상 배포 선택
2. 재배포 후 헬스체크 및 핵심 플로우 검증
3. 장애 타임라인 기록

## 5. 장애 대응 Runbook

### 5.1 공통 트리아지

1. 장애 등급 분류(P1/P2/P3)
2. 영향 범위 기록(사용자/기능/API)
3. 최근 변경 확인(코드/환경변수/인프라)
4. `requestId` 확보
5. Backend -> AI -> DB 순서로 원인 분리

### 5.2 API 500 발생 시

1. Frontend 네트워크 탭에서 실패 API와 `requestId` 확인
2. Backend 로그에서 동일 `requestId` 검색
3. AI 호출 오류 여부 확인
4. DB 예외(SQL/연결/권한) 여부 확인
5. 즉시 완화(재시도 정책, 캐시 사용, 롤백) 후 근본 원인 수정

### 5.3 DB 연결 오류 시

자주 발생하는 원인:

- `DB_URL`이 `postgresql://...` 형식(오류)
- DB 이름 오입력
- 계정/비밀번호 불일치

조치:

1. `DB_URL`를 `jdbc:postgresql://.../<db_name>`으로 교정
2. Render PostgreSQL의 실제 DB 이름 확인
3. `DB_USERNAME`, `DB_PASSWORD` 재설정
4. 재배포 후 `/actuator/health` 확인

### 5.4 AI 지연/타임아웃 시

1. AI 서비스 헬스체크 확인
2. 외부 API 한도/장애 여부 확인
3. `AI_CLIENT_TIMEOUT_*`, `AI_CLIENT_RETRY_*` 설정 확인
4. 필요 시 타임아웃 상향 또는 재시도 완화 적용

## 6. 로그 운영 규칙

- 민감정보(비밀번호, 토큰 원문, API 키)는 로그 금지
- 사용자 문의 대응 시 `requestId`를 우선 수집
- 동일 장애는 이슈 단위로 묶어서 추적
- 장애 종료 후 Postmortem 문서화

## 7. DB 운영

### 7.1 상태 확인

- Render PostgreSQL Dashboard에서:
  - 상태, 연결, 스토리지, 백업 확인

### 7.2 데이터 확인 예시

```sql
SELECT current_database();
SELECT now();
SELECT count(*) FROM users;
```

### 7.3 백업/복구

- RPO 목표: 24시간 이내
- RTO 목표: 2시간 이내
- 월 1회 복구 리허설 수행

## 8. 보안 운영

- `.env`/시크릿 값 Git 커밋 금지
- 운영 시크릿 정기 로테이션
- 기본 개발용 시크릿 사용 금지
- CORS를 실제 도메인으로 제한
- 에러 응답 스택트레이스 비노출 유지

## 9. 운영 증빙

운영/포트폴리오 증빙은 아래 문서 기준으로 수집:

- `docs/PUBLIC_SERVICE_EVIDENCE.md`
- `artifacts/reports/*`
- `artifacts/screenshots/*`

## 10. 문서 유지 정책

- 운영 절차 변경 시 `OPERATIONS_MANUAL.md` 우선 수정
- 점검 항목 변경 시 `OPERATIONS_CHECKLIST.md` 동시 수정
- 알림 기준 변경 시 `ALERTING_TEMPLATES.md` 동시 수정
- 릴리즈 시 `PATCH_NOTES.md`에 변경 기록 추가
