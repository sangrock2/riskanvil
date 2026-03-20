# Render 배포 가이드

Stock-AI를 Render에 배포할 때 필요한 최소 설정입니다.

- 저장소 루트에 `render.yaml`이 포함되어 있어 Blueprint로 바로 가져올 수 있습니다.
- Blueprint 적용 후 `sync: false`로 지정된 변수는 Render Dashboard에서 직접 입력해야 합니다.

## 1. 서비스 구성 권장안

1. `ai`: **Private Service (Docker)**  
   Root Directory: `ai`  
   Health Check Path: `/health`
2. `backend`: **Web Service (Docker)**  
   Root Directory: `backend`  
   Health Check Path: `/actuator/health/readiness`
3. `frontend`: **Static Site**  
   Root Directory: `frontend`  
   Build Command: `npm ci && npm run build`  
   Publish Directory: `dist`
4. `postgres`: Render PostgreSQL (Managed)
5. `redis`: 선택 (미사용 시 `SPRING_CACHE_TYPE=simple`)

## 2. Backend 필수 환경변수

아래 값은 Render Dashboard에서 `backend` 서비스에 설정합니다.

```env
SPRING_PROFILES_ACTIVE=prod,postgres
JWT_SECRET=<32바이트 이상 랜덤 문자열>
REFRESH_TOKEN_PEPPER=<추가 시크릿 권장>
TOTP_ENCRYPTION_KEY=<32바이트 이상 랜덤 문자열, 재배포 시 동일 값 유지>

# DB (PostgreSQL 16)
DB_URL=jdbc:postgresql://<host>:5432/stock_ai
DB_USERNAME=<db_user>
DB_PASSWORD=<db_password>
DB_DRIVER_CLASS_NAME=org.postgresql.Driver
JPA_DDL_AUTO=validate
FLYWAY_ENABLED=true
FLYWAY_BASELINE_VERSION=2

# AI 연결 (둘 중 하나)
AI_BASE_URL=http://<ai-host>:8000
# 또는
AI_SERVICE_HOSTPORT=<ai-host>:8000

# CORS (배포 후 프론트 URL로 교체)
APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://<your-frontend>.onrender.com

# Redis (선택)
# Redis를 쓰지 않으면 simple 캐시로 시작
SPRING_CACHE_TYPE=simple
# Redis를 쓸 때만 아래 사용
# REDIS_HOST=<redis-host>
# REDIS_PORT=6379
# REDIS_PASSWORD=<redis-password>
```

중요:
- `TOTP_ENCRYPTION_KEY` 는 `backend` 시작 전에 반드시 설정되어 있어야 하며, 문자열 기준 최소 32바이트 이상이어야 합니다.
- 이미 운영 DB에 저장된 2FA 시크릿은 이 키로 암호화되므로, 값을 바꾸면 기존 사용자의 TOTP 복호화가 실패할 수 있습니다. 키 회전 절차를 별도로 준비하지 않았다면 재배포 때도 같은 값을 유지하세요.
- PowerShell 예시: `[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))`

### 2.1 Backend + AI 공통 시크릿

아래 값은 `backend` 와 `ai` 서비스에 **같은 값으로** 넣어야 합니다.

```env
AI_INTERNAL_SERVICE_TOKEN=<32자 이상 강한 랜덤 문자열>
```

중요:
- `backend` 에만 넣거나 `ai` 에만 넣으면 두 서비스 모두 기동에 실패할 수 있습니다.
- 기본값, 예제값, `change-me`, `dev-only` 같은 토큰은 운영/스테이징에서 거부됩니다.
- 이 값은 외부 공개 토큰이 아니라 backend -> ai 내부 호출용 공유 시크릿입니다.

참고:
- `DB_URL`에 실수로 `postgresql://...` 또는 `postgres://...`를 넣어도 앱 시작 시 자동으로 `jdbc:postgresql://...`로 보정됩니다.
- 운영 안정성을 위해 Dashboard에는 처음부터 `jdbc:postgresql://...` 형식으로 입력하는 것을 권장합니다.
- 기존 운영 Postgres처럼 이미 테이블이 있는 DB는 첫 배포에서 Flyway가 `baseline version 2`만 기록하고 baseline DDL은 다시 실행하지 않습니다.

## 3. Frontend 환경변수

`frontend` Static Site에 설정합니다.

```env
VITE_API_BASE_URL=https://<your-backend>.onrender.com
# 선택: 비워두면 기본 규칙 사용
# 입력 시 https:// 주소를 넣어도 자동으로 wss://.../ws/quotes 로 변환됨
VITE_WS_URL=https://<your-backend>.onrender.com
```

## 4. 배포 순서

1. `ai`와 `backend`에 같은 `AI_INTERNAL_SERVICE_TOKEN` 입력
2. `ai` 배포
3. `backend` 배포 (AI/PostgreSQL/보안 env 설정)
4. `frontend` 배포 (`VITE_API_BASE_URL`을 backend URL로 설정)
5. frontend URL 확정 후 backend의 `APP_CORS_ALLOWED_ORIGIN_PATTERNS` 값을 최종 URL로 업데이트 후 재배포

## 5. 이번 코드 반영 사항

1. Frontend API 호출이 `VITE_API_BASE_URL`을 지원하도록 변경
2. SSE 호출도 `VITE_API_BASE_URL`을 지원하도록 변경
3. WebSocket env 값에 `https://...`를 넣어도 자동으로 `wss://.../ws/quotes`로 정규화
4. Backend가 Render 기본 `PORT` 환경변수를 우선 사용하도록 변경
5. Backend AI/DB 주소를 `AI_SERVICE_HOSTPORT`, `DB_HOSTPORT`로도 주입 가능하게 변경
6. Postgres 전용 `postgres` 프로필 추가 (`SPRING_PROFILES_ACTIVE=prod,postgres`)
7. Postgres 전용 Flyway baseline(V1/V2) 추가 및 운영 기본값을 `FLYWAY_ENABLED=true`, `JPA_DDL_AUTO=validate`로 정렬
