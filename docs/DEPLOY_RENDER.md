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
   Health Check Path: `/actuator/health`
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

# DB (PostgreSQL 16)
DB_URL=jdbc:postgresql://<host>:5432/stock_ai
DB_USERNAME=<db_user>
DB_PASSWORD=<db_password>
DB_DRIVER_CLASS_NAME=org.postgresql.Driver
JPA_DDL_AUTO=update
FLYWAY_ENABLED=false

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

## 3. Frontend 환경변수

`frontend` Static Site에 설정합니다.

```env
VITE_API_BASE_URL=https://<your-backend>.onrender.com
# 선택: 비워두면 기본 규칙 사용
# 입력 시 https:// 주소를 넣어도 자동으로 wss://.../ws/quotes 로 변환됨
VITE_WS_URL=https://<your-backend>.onrender.com
```

## 4. 배포 순서

1. `ai` 배포
2. `backend` 배포 (AI/PostgreSQL/보안 env 설정)
3. `frontend` 배포 (`VITE_API_BASE_URL`을 backend URL로 설정)
4. frontend URL 확정 후 backend의 `APP_CORS_ALLOWED_ORIGIN_PATTERNS` 값을 최종 URL로 업데이트 후 재배포

## 5. 이번 코드 반영 사항

1. Frontend API 호출이 `VITE_API_BASE_URL`을 지원하도록 변경
2. SSE 호출도 `VITE_API_BASE_URL`을 지원하도록 변경
3. WebSocket env 값에 `https://...`를 넣어도 자동으로 `wss://.../ws/quotes`로 정규화
4. Backend가 Render 기본 `PORT` 환경변수를 우선 사용하도록 변경
5. Backend AI/DB 주소를 `AI_SERVICE_HOSTPORT`, `DB_HOSTPORT`로도 주입 가능하게 변경
6. Postgres 전용 `postgres` 프로필 추가 (`SPRING_PROFILES_ACTIVE=prod,postgres`)
