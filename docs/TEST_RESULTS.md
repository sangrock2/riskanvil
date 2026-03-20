# Stock-AI Test Validation Guide

생성일: 2026-03-19  
성격: 고정 통과 숫자 보고서가 아니라, 현재 저장소 기준 테스트 범위와 해석 기준을 설명하는 문서

---

## 1. 원칙

- 이 문서는 `97/97`, `100%` 같은 고정 수치를 장기간 보존하는 용도가 아니다.
- 최신 결과의 단일 출처는 CI 로그와 리포트 아티팩트다.
- 테스트 범위는 `단위/통합/API smoke`로 구분하고, 브라우저 E2E와 혼동하지 않는다.

## 2. 현재 자동 검증 범위

### 2.1 CI

- OpenAPI snapshot + generated typed client 동기화
- Frontend Vitest 테스트
- Frontend Vite 프로덕션 빌드
- Backend 전체 Gradle 테스트
- AI pytest
- Docker Compose 기반 API smoke (`scripts/e2e_smoke.py`)
- Docker Compose 기반 Playwright browser smoke (`frontend/e2e/smoke.spec.js`)

### 2.2 로컬 종합 스크립트

`scripts/run_all_tests.ps1`는 아래를 실행한다.

1. Backend build (`clean build -x test`)
2. Backend full test suite (`gradlew test`)
3. Frontend test suite (`npm test`)
4. Frontend build (`npm run build`)
5. AI unit tests (`pytest -q tests`)
6. 선택: localhost backend/ai가 이미 실행 중이면 `scripts/integration_tests.ps1`
7. 선택: localhost frontend/backend/ai가 이미 실행 중이면 `npm run test:e2e`

## 3. 현재 해석 시 주의점

- `scripts/e2e_smoke.py`는 여전히 HTTP 기반 API smoke다.
- 브라우저 경계는 Playwright smoke가 추가로 커버하지만, 현재 범위는 `인증 가드`, `회원가입 세션 수립`, `관심종목 추가`, `포트폴리오 생성/포지션 추가` 수준이다.
- 즉 크로스브라우저 호환성, 시각 회귀, 전체 페이지 망라 수준의 E2E까지 확보된 상태는 아니다.

## 4. 권장 실행 명령

### Backend

```bash
cd backend
./gradlew test
```

### Frontend

```bash
cd frontend
npm test
npm run build
npm run test:e2e
```

### AI

```bash
cd ai
pytest -q tests
```

### 로컬 API smoke

```bash
pwsh -File scripts/run_all_tests.ps1
```

또는 전체 스택이 이미 떠 있는 경우:

```bash
pwsh -File scripts/integration_tests.ps1
python scripts/e2e_smoke.py
```

## 5. 아직 비어 있는 검증 영역

- 실제 브라우저 E2E
- 실제 브라우저 E2E의 광범위한 커버리지
- 보안 스캔/의존성 취약점 스캔
- 부하 테스트 결과의 CI 게이트화

## 6. 운영 기준

- 릴리즈 판단 시 이 문서의 서술보다 CI 결과와 최신 smoke 리포트를 우선한다.
- 수치형 결과를 문서에 남길 때는 날짜, 환경, 커밋, 실행 명령을 함께 기록한다.
