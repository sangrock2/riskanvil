# Refresh Token & Multi-Tab Synchronization

## 개요

사용자가 서비스를 실제로 사용하고 있을 때만 토큰을 갱신하고, 여러 브라우저 탭에서 인증 상태를 동기화하는 기능입니다.

## 주요 기능

### 1. Refresh Token 메커니즘

**토큰 수명:**
- **Access Token**: 15분 (짧은 수명으로 보안 강화)
- **Refresh Token**: 7일 (데이터베이스에 저장)

**토큰 갱신 흐름:**
1. Access Token 만료 시 401 응답 수신
2. 사용자 활동 확인 (최근 5분 이내 활동 필수)
3. 활동이 감지된 경우에만 Refresh Token으로 새 Access Token 발급
4. 실패한 요청을 새 토큰으로 자동 재시도
5. 여러 동시 요청이 401을 받은 경우 큐 기반으로 처리 (중복 갱신 방지)

**활동 기반 갱신:**
사용자가 명시적으로 요청: "사용자가 서비스를 실제로 사용하고 있을때만 갱신하게 해줘"
- 마우스, 키보드, 터치, 스크롤 이벤트 추적
- 1초 간격으로 throttle하여 성능 최적화
- sessionStorage에 마지막 활동 시간 저장
- 5분 이상 비활성 상태면 토큰 갱신 거부 → 로그인 페이지로 리디렉션

### 2. 다중 탭 동기화

**동기화 메커니즘:**
- **Custom Events**: `auth:login`, `auth:logout` 이벤트 디스패치
- **Storage Events**: localStorage 변경 시 브라우저가 자동으로 다른 탭에 전파

**동작 방식:**
- 한 탭에서 로그인 → 모든 탭에서 대시보드로 이동
- 한 탭에서 로그아웃 → 모든 탭에서 로그인 페이지로 이동
- 한 탭에서 토큰 갱신 → 다른 탭도 새 토큰 자동 사용

## 백엔드 구현

### 데이터베이스 스키마

**refresh_tokens 테이블:**
```sql
CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_used_at TIMESTAMP(6),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
);
```

### API 엔드포인트

#### POST /api/auth/login
**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**동작:**
- 기존 Refresh Token 모두 무효화 (보안)
- 새로운 Access Token (15분) 및 Refresh Token (7일) 발급

#### POST /api/auth/register
**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**동작:**
- 회원가입과 동시에 Access Token 및 Refresh Token 발급

#### POST /api/auth/refresh
**Request:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**에러:**
- `400 Bad Request`: Invalid or expired refresh token
- `401 Unauthorized`: Refresh token not found

**동작:**
- Refresh Token 유효성 검증 (존재 여부, 만료 여부)
- last_used_at 타임스탬프 업데이트
- 새로운 Access Token 발급
- 동일한 Refresh Token 반환 (재사용 가능)

#### POST /api/auth/logout
**Request:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:**
```
200 OK (empty body)
```

**동작:**
- 데이터베이스에서 Refresh Token 삭제
- 사용자의 모든 세션 무효화

### 보안 고려사항

1. **Refresh Token Rotation**: 로그인 시 기존 토큰 모두 무효화
2. **Database Storage**: Refresh Token을 데이터베이스에 저장하여 중앙 집중식 관리
3. **CASCADE Delete**: 사용자 삭제 시 모든 Refresh Token 자동 삭제
4. **Expiration Cleanup**: 만료된 토큰 자동 정리 메커니즘
5. **UUID-based Tokens**: 예측 불가능한 UUID v4 사용
6. **Last Used Tracking**: 의심스러운 활동 감지 가능

## 프론트엔드 구현

### 토큰 관리 (frontend/src/auth/token.js)

**주요 함수:**
```javascript
// 토큰 저장 (다중 탭 이벤트 디스패치)
setTokens(accessToken, refreshToken)

// 토큰 조회
getToken()
getRefreshToken()

// 토큰 삭제 (다중 탭 이벤트 디스패치)
clearAllTokens()
```

**다중 탭 이벤트 디스패치:**
```javascript
export function setTokens(accessToken, refreshToken) {
  setToken(accessToken);
  setRefreshToken(refreshToken);

  // Dispatch event for multi-tab sync
  window.dispatchEvent(new CustomEvent('auth:login', {
    detail: { accessToken, refreshToken }
  }));
}

export function clearAllTokens() {
  clearToken();
  clearRefreshToken();

  // Dispatch event for multi-tab sync
  window.dispatchEvent(new CustomEvent('auth:logout'));
}
```

### HTTP 클라이언트 (frontend/src/api/http.js)

**자동 토큰 갱신:**
```javascript
// 401 응답 시 자동으로 refresh 시도
if (res.status === 401) {
  if (isRefreshing) {
    // 이미 갱신 중이면 큐에 추가하고 대기
    return new Promise((resolve, reject) => {
      subscribeTokenRefresh((newToken) => {
        // 새 토큰으로 원래 요청 재시도
      });
    });
  }

  // Refresh 프로세스 시작
  isRefreshing = true;
  const newAccessToken = await refreshAccessToken();
  isRefreshing = false;
  onTokenRefreshed(newAccessToken);

  // 원래 요청 재시도
}
```

**활동 기반 갱신 체크:**
```javascript
async function refreshAccessToken() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new Error("No refresh token available");
  }

  // Check if user was recently active (within 5 minutes)
  if (!wasRecentlyActive(5 * 60 * 1000)) {
    clearAllTokens();
    window.location.replace("/login?reason=inactive");
    throw new Error("User inactive - session expired");
  }

  // Proceed with refresh...
}
```

### 활동 감지 (frontend/src/hooks/useActivityDetection.js)

**추적 이벤트:**
- `mousedown`, `mousemove`, `keypress`, `scroll`, `touchstart`, `click`

**Throttling:**
- 1초 간격으로 throttle하여 과도한 업데이트 방지

**비활성 체크:**
- 30초마다 마지막 활동 시간 확인
- 5분 이상 비활성 시 `user:activity` 이벤트 디스패치

**사용 예:**
```javascript
function AppContent() {
  // User activity detection (for token refresh eligibility)
  useActivityDetection();

  return (
    <>
      <NavBar />
      <Routes>...</Routes>
    </>
  );
}
```

### 다중 탭 동기화 (frontend/src/hooks/useAuthSync.js)

**Custom Events 리스닝:**
```javascript
// Login event from another tab
window.addEventListener("auth:login", handleLogin);

// Logout event from another tab
window.addEventListener("auth:logout", handleLogout);
```

**Storage Events 리스닝:**
```javascript
window.addEventListener("storage", handleStorageChange);

function handleStorageChange(event) {
  // Access token removed in another tab
  if (event.key === "accessToken" && !event.newValue) {
    navigate("/login?reason=logout");
  }

  // Access token added in another tab
  if (event.key === "accessToken" && event.newValue && !event.oldValue) {
    navigate("/dashboard");
  }
}
```

**사용 예:**
```javascript
function AppContent() {
  // Multi-tab authentication synchronization
  useAuthSync();

  return (
    <>
      <NavBar />
      <Routes>...</Routes>
    </>
  );
}
```

## 테스트 시나리오

### 시나리오 1: 활동 중인 사용자의 토큰 갱신

**단계:**
1. 로그인 후 15분 대기 (Access Token 만료)
2. API 호출 (예: 주식 분석)
3. 자동으로 Refresh Token을 사용하여 새 Access Token 발급
4. 원래 요청이 새 토큰으로 자동 재시도
5. 사용자는 중단 없이 계속 사용 가능

**예상 결과:**
- 사용자 경험 중단 없음
- 백그라운드에서 자동 갱신
- 새 Access Token으로 요청 성공

### 시나리오 2: 비활성 사용자의 세션 만료

**단계:**
1. 로그인 후 서비스 사용
2. 5분 이상 아무 활동 없음 (마우스/키보드/터치/스크롤)
3. API 호출 시도
4. 활동 감지 실패로 토큰 갱신 거부
5. 자동으로 로그인 페이지로 리디렉션

**예상 결과:**
- `/login?reason=inactive` 리디렉션
- "사용자 활동이 감지되지 않아 세션이 종료되었습니다" 메시지 표시
- 모든 토큰 삭제

### 시나리오 3: 다중 탭 로그인 동기화

**단계:**
1. 탭 A와 탭 B를 열고 둘 다 로그인 페이지
2. 탭 A에서 로그인
3. 탭 B 확인

**예상 결과:**
- 탭 A: 대시보드로 이동
- 탭 B: 자동으로 대시보드로 이동 (리디렉션)
- 두 탭 모두 동일한 Access Token 및 Refresh Token 사용

### 시나리오 4: 다중 탭 로그아웃 동기화

**단계:**
1. 탭 A와 탭 B를 열고 둘 다 로그인 상태
2. 탭 A에서 로그아웃
3. 탭 B 확인

**예상 결과:**
- 탭 A: 로그인 페이지로 이동
- 탭 B: 자동으로 로그인 페이지로 이동
- 서버에서 Refresh Token 무효화
- 모든 탭에서 토큰 삭제

### 시나리오 5: 동시 다중 요청 시 토큰 갱신

**단계:**
1. Access Token 만료 상태
2. 여러 API 호출을 동시에 실행 (예: 3개 요청)
3. 모든 요청이 401 응답 받음

**예상 결과:**
- 첫 번째 요청만 Refresh Token 호출
- 나머지 요청들은 큐에서 대기
- 새 Access Token 발급 후 모든 요청이 새 토큰으로 자동 재시도
- 중복 갱신 방지 (효율성)

### 시나리오 6: Refresh Token 만료

**단계:**
1. 로그인 후 7일 경과 (Refresh Token 만료)
2. API 호출 시도
3. Refresh Token 갱신 실패

**예상 결과:**
- `/login?reason=expired` 리디렉션
- "세션이 만료되었습니다. 다시 로그인해주세요." 메시지 표시
- 모든 토큰 삭제

## 로그인 페이지 리디렉션 사유

| 사유 (`?reason=`) | 메시지 | 발생 시점 |
|-------------------|--------|-----------|
| `expired` | 세션이 만료되었습니다. 다시 로그인해주세요. | Access Token 만료 및 Refresh Token 갱신 실패 |
| `missing` | 로그인이 필요합니다. | 토큰 없이 보호된 페이지 접근 |
| `inactive` | 사용자 활동이 감지되지 않아 세션이 종료되었습니다. | 5분 이상 비활성 상태에서 토큰 갱신 시도 |
| `logout` | 로그아웃되었습니다. | 사용자가 명시적으로 로그아웃 |

## 보안 권장사항

1. **HTTPS 필수**: 프로덕션 환경에서는 반드시 HTTPS 사용
2. **HttpOnly Cookies 고려**: localStorage 대신 HttpOnly 쿠키 사용 시 XSS 방지 강화
3. **Refresh Token Rotation**: 갱신 시마다 새 Refresh Token 발급 고려 (현재는 재사용)
4. **Rate Limiting**: /api/auth/refresh 엔드포인트에 rate limiting 적용
5. **Token Binding**: IP 주소 또는 User-Agent와 토큰 바인딩 고려
6. **Suspicious Activity Detection**: last_used_at 추적으로 의심스러운 활동 감지

## 성능 최적화

1. **Throttling**: 활동 감지 이벤트를 1초 간격으로 throttle
2. **Queue-based Refresh**: 동시 다중 요청 시 중복 갱신 방지
3. **Indexed Database**: user_id 및 expires_at에 인덱스 설정
4. **Lazy Loading**: useActivityDetection 및 useAuthSync를 필요한 곳에서만 사용
5. **Passive Event Listeners**: 스크롤 및 터치 이벤트에 passive 옵션 사용

## 트러블슈팅

### 문제: 토큰이 계속 갱신되지 않음
**원인**: 사용자 비활성 (5분 이상 활동 없음)
**해결**: 사용자에게 활동 필요성 안내, 또는 비활성 임계값 조정

### 문제: 다중 탭 동기화가 작동하지 않음
**원인**: Storage Events는 같은 출처(origin)의 다른 탭에서만 발생
**해결**: 같은 도메인에서 테스트, Private/Incognito 모드는 Storage Events 제한 가능

### 문제: Refresh Token이 데이터베이스에 쌓임
**원인**: 만료된 토큰 정리 작업 미실행
**해결**: 정기적인 cleanup 작업 스케줄링 (예: `@Scheduled` 사용)

## 추가 개선 사항

1. **Refresh Token Rotation**: 갱신 시마다 새 Refresh Token 발급
2. **Device Management**: 사용자가 활성 세션 관리 UI 제공
3. **Session Analytics**: 사용자의 세션 활동 추적 및 분석
4. **Graceful Token Refresh**: 만료 1분 전에 자동 갱신 (proactive refresh)
5. **WebSocket Support**: 웹소켓 연결에도 토큰 갱신 메커니즘 적용

## 관련 파일

### 백엔드
- `backend/src/main/java/com/sw103302/backend/entity/RefreshToken.java`
- `backend/src/main/java/com/sw103302/backend/repository/RefreshTokenRepository.java`
- `backend/src/main/java/com/sw103302/backend/service/AuthService.java`
- `backend/src/main/java/com/sw103302/backend/controller/AuthController.java`
- `backend/src/main/java/com/sw103302/backend/dto/RefreshRequest.java`
- `backend/src/main/java/com/sw103302/backend/dto/AuthResponse.java`
- `backend/src/main/resources/db/migration/V10__create_refresh_tokens.sql`
- `backend/src/main/resources/application.properties`

### 프론트엔드
- `frontend/src/auth/token.js`
- `frontend/src/api/http.js`
- `frontend/src/hooks/useAuthSync.js`
- `frontend/src/hooks/useActivityDetection.js`
- `frontend/src/pages/Login.js`
- `frontend/src/pages/Register.js`
- `frontend/src/components/NavBar.js`
- `frontend/src/App.js`

## 참고 자료

- [RFC 6749 - OAuth 2.0 Authorization Framework](https://tools.ietf.org/html/rfc6749)
- [OWASP - Token-based Authentication](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- [MDN - Web Storage API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Storage_API)
- [MDN - Storage Event](https://developer.mozilla.org/en-US/docs/Web/API/StorageEvent)
