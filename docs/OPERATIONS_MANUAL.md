# Operations Manual

이 문서는 배포/운영/장애 대응 문서 정본입니다.

## 1. Deployment Models

### 1.1 Local Development

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- AI: `http://localhost:8000`

### 1.2 Container Stack (권장)

`docker-compose.yml` 구성:

- Core: `frontend`, `backend`, `ai`, `nginx`
- Infra: `mysql`, `redis`
- Monitoring: `prometheus`, `grafana`

## 2. Environment Baseline

최소 필수 변수:

- DB: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- JWT: `JWT_SECRET`
- AI: `AI_BASE_URL`, `OPENAI_API_KEY`(필요 시)
- Docker/MySQL: `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`
- CORS: `APP_CORS_ALLOWED_ORIGIN_PATTERNS`

기준 템플릿: `.env.example`

## 3. Standard Commands

### 3.1 Start/Stop

```bash
docker compose up -d
docker compose ps
docker compose logs -f backend
docker compose down
```

### 3.2 Health Check

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8000/health
```

### 3.3 Monitoring Access

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`

## 4. Public URL Deployment (요약)

권장 순서:

1. 서버 준비(Ubuntu + Docker/Compose)
2. 코드 배포(`git clone` 후 `.env` 설정)
3. `docker compose up -d --build`
4. 도메인 DNS 연결
5. Reverse Proxy(TLS) 설정
6. 헬스체크/로그/알림 검증

상세는 이 문서와 `nginx/nginx.conf`를 기준으로 운영합니다.

## 5. Runtime Policies

- Backend timeout/retry: 환경변수(`AI_CLIENT_*`)로 제어
- 에러 추적: Sentry DSN 설정 시 활성화
- 리소스 제한: compose `deploy.resources.limits` 준수
- 로그 로테이션: json-file `max-size/max-file` 사용

## 6. Incident Response

### 6.1 공통 절차

1. 영향 범위 확인(기능/사용자/시장)
2. 최근 배포/설정 변경 확인
3. 서비스별 헬스체크
4. 로그/메트릭 기반 원인 분리
5. 완화 조치 -> 근본 원인 수정 -> 패치노트 기록

### 6.2 빈발 시나리오

- AI 지연/타임아웃
  - `AI_CLIENT_TIMEOUT_REPORT_SECONDS` 확인
  - 외부 LLM/API 상태 확인
- DB 연결 실패
  - MySQL 컨테이너 상태/계정/비밀번호 확인
- Redis 장애
  - 캐시 실패 graceful fallback 확인
- WebSocket/SSE 이상
  - Nginx 업그레이드/버퍼링 설정과 네트워크 경로 점검

## 7. Error Handling Standard

- API는 HTTP 상태코드 + 구조화된 JSON 에러를 반환
- 클라이언트는 사용자 메시지와 내부 디버깅 메시지를 분리
- 운영 로그에는 request id/correlation id를 남김

## 8. Release Checklist (운영용)

배포 전:

- DB 마이그레이션 파일 검토
- 환경변수 diff 검토
- smoke 테스트 실행
- 롤백 계획 확인

배포 후:

- `/actuator/health`, `/health` 확인
- 주요 사용자 플로우 로그인/분석/포트폴리오 검증
- Grafana 에러율/응답시간 급증 여부 확인
- `scripts/verify_public_service.ps1` 실행 후 리포트 보관
- `scripts/capture_monitoring_screenshots.ps1` 실행 후 증빙 스크린샷 보관

## 9. Backup & Recovery

- MySQL: 정기 dump + 보관 정책 운영
- Redis: 캐시 재생성 가능 정책 유지
- 복구 목표 예시:
  - RPO: 24시간 이내
  - RTO: 2시간 이내

## 10. Demo Seed Operations

### 10.1 Docker 기반 자동 주입

```powershell
pwsh -File scripts/seed_demo_data.ps1 -UserEmail "user@example.com"
pwsh -File scripts/seed_demo_data.ps1 -AllUsers
```

### 10.2 MySQL CLI 직접 주입

```sql
USE stock_ai;
SELECT id, email FROM users;
SET @target_user_id := 1;
SOURCE C:/Users/Sw103/Desktop/stock-ai/scripts/sql/seed_demo_data.sql;
```

## 11. Automated Verification Commands

```powershell
# Core E2E 3-flow smoke
python scripts/e2e_smoke.py

# Backtest/Risk validation report
pwsh -File scripts/validate_backtest_risk.ps1

# Public URL verification report
pwsh -File scripts/verify_public_service.ps1 -PublicAppUrl "https://your-domain.com"
```

## 12. Security Operations

- `.env` 및 API 키는 Git 커밋 금지
- 운영 비밀번호/토큰 주기적 로테이션
- 2FA 활성화 사용자 계정 모니터링
- Refresh Token 해시 저장 정책 유지

## 13. Ownership

- 코드 변경 담당: 개발팀
- 배포/장애 대응 담당: 운영 담당자
- 문서 정합성 책임: 릴리즈 담당자가 `PATCH_NOTES.md`와 함께 갱신

## 14. Public Evidence Pack

포트폴리오/대외 공유용 운영 증빙 기준 문서:

- `docs/PUBLIC_SERVICE_EVIDENCE.md`
