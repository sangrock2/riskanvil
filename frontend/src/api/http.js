import { getToken, clearToken, getRefreshToken, setTokens, clearAllTokens } from "../auth/token";
import { wasRecentlyActive } from "../hooks/useActivityDetection";

// Track if we're currently refreshing to avoid multiple simultaneous refresh attempts
let isRefreshing = false;
let refreshSubscribers = [];
const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ||
  import.meta.env.REACT_APP_API_BASE_URL ||
  ""
).replace(/\/+$/, "");

/**
 * URL query에 `test=true`가 붙어 있는지 확인한다.
 * 테스트 데이터 모드에서만 백엔드 테스트 플래그를 전달한다.
 */
function isTestMode() {
  try {
    const qs = new URLSearchParams(window.location.search);
    return qs.get("test") === "true";
  } catch {
    return false;
  }
}

/**
 * 테스트 모드일 때 요청 URL에 `test=true`를 주입한다.
 * 상대/절대 경로를 모두 지원하며 원래 경로 형식을 최대한 유지한다.
 */
function withTestParam(path) {
  if (!isTestMode()) return path;

  // 상대경로/절대경로 모두 처리
  const url = new URL(path, window.location.origin);

  if (!url.searchParams.has("test")) {
    url.searchParams.set("test", "true");
  }

  // 원래가 상대경로였다면 상대경로로 되돌려줌 (proxy 환경 깔끔)
  const isAbsolute = /^https?:\/\//i.test(path);
  return isAbsolute ? url.toString() : (url.pathname + url.search + url.hash);
}

function isAbsoluteHttpUrl(path) {
  return /^https?:\/\//i.test(path);
}

function resolveApiPath(path) {
  const testedPath = withTestParam(path);
  if (!API_BASE_URL || isAbsoluteHttpUrl(testedPath)) {
    return testedPath;
  }
  if (testedPath.startsWith("/")) {
    return `${API_BASE_URL}${testedPath}`;
  }
  return `${API_BASE_URL}/${testedPath}`;
}

/**
 * JWT payload를 디코딩해 만료시간(exp) 선검사에 사용한다.
 */
function decodeJwtPayload(token) {
  try {
    const [, payload] = token.split(".");
    if (!payload) return null;
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(decodeURIComponent(escape(json)));
  } catch {
    return null;
  }
}

// 선택: 앱 시작 시 선제 체크에도 쓸 수 있음
export function isTokenExpired(token, skewSec = 10) {
  const p = decodeJwtPayload(token);
  const exp = p?.exp;
  if (!exp) return false; // exp 없으면 서버 판단에 맡김
  const now = Math.floor(Date.now() / 1000);
  return exp <= now + skewSec;
}

/**
 * 호출자가 기대하는 응답 타입(auto/json/text/blob)에 맞춰 본문을 파싱한다.
 */
async function parseResponseBody(res, responseType = "auto") {
  if (responseType === "blob") return res.blob();
  if (responseType === "text") return res.text();
  if (responseType === "json") return res.json();

  const ct = res.headers.get("content-type") || "";
  if (ct.includes("application/json")) return res.json();
  return res.text();
}


// Add subscriber to queue waiting for token refresh
function subscribeTokenRefresh(cb) {
  refreshSubscribers.push(cb);
}

// Notify all subscribers when token is refreshed
function onTokenRefreshed(newAccessToken) {
  refreshSubscribers.forEach((cb) => cb(newAccessToken));
  refreshSubscribers = [];
}

/**
 * refresh token으로 access token을 재발급한다.
 * 비활성 사용자/만료 사용자의 경우 토큰을 제거하고 로그인으로 이동한다.
 */
async function refreshAccessToken() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new Error("No refresh token available");
  }

  // Check if user was recently active (within 5 minutes)
  // Only refresh tokens for active users as requested by the user
  if (!wasRecentlyActive(5 * 60 * 1000)) {
    // User inactive - don't refresh, redirect to login
    clearAllTokens();
    window.location.replace("/login?reason=inactive");
    throw new Error("User inactive - session expired");
  }

  try {
    const response = await fetch(resolveApiPath("/api/auth/refresh"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });

    if (!response.ok) {
      throw new Error("Refresh failed");
    }

    const data = await response.json();
    setTokens(data.accessToken, data.refreshToken);
    return data.accessToken;
  } catch (error) {
    // Refresh failed - clear tokens and redirect to login
    clearAllTokens();
    window.location.replace("/login?reason=expired");
    throw error;
  }
}

// Exponential backoff delay
function getRetryDelay(attempt) {
  return Math.min(1000 * Math.pow(2, attempt), 10000);
}

// Check if error is retryable
function isRetryableError(error, status) {
  // Network errors
  if (error.name === "TypeError" || error.message.includes("Failed to fetch")) {
    return true;
  }
  // 5xx server errors
  if (status >= 500 && status < 600) {
    return true;
  }
  // 429 Too Many Requests
  if (status === 429) {
    return true;
  }
  return false;
}

/**
 * 공통 API fetch 래퍼.
 * - 인증 헤더 주입
 * - 401 시 단일 refresh + 대기열 재시도
 * - 네트워크/5xx/429 재시도(지수 백오프)
 */
export async function apiFetch(path, options = {}) {
  const { retry = 3, retryDelay = null, responseType = "auto", ...fetchOptions } = options;
  let lastError = null;

  for (let attempt = 0; attempt <= retry; attempt++) {
    try {
      const token = getToken();

      if (token && isTokenExpired(token)) {
        clearToken();
        window.location.replace("/login?reason=expired");
        throw new Error("token_expired");
      }

      const headers = { ...(fetchOptions.headers || {}) };
      const isFormData = fetchOptions.body instanceof FormData;

      if (!isFormData && fetchOptions.body && !headers["Content-Type"]) {
        headers["Content-Type"] = "application/json";
      }

      if (token) headers.Authorization = `Bearer ${token}`;

      const finalPath = resolveApiPath(path);

      const res = await fetch(finalPath, { ...fetchOptions, headers });

      if (res.status === 401) {
        // Token expired - try to refresh
        if (isRefreshing) {
          // Already refreshing - wait for it to complete
          return new Promise((resolve, reject) => {
            subscribeTokenRefresh((newToken) => {
              // Retry original request with new token
              fetchOptions.headers = {
                ...fetchOptions.headers,
                Authorization: `Bearer ${newToken}`,
              };
              fetch(finalPath, fetchOptions)
                .then((newRes) => {
                  if (!newRes.ok) {
                    reject(new Error(`HTTP ${newRes.status}`));
                  }
                  parseResponseBody(newRes, responseType).then(resolve).catch(reject);
                })
                .catch(reject);
            });
          });
        }

        // Start refresh process
        isRefreshing = true;
        try {
          const newAccessToken = await refreshAccessToken();
          isRefreshing = false;
          onTokenRefreshed(newAccessToken);

          // Retry original request with new token
          headers.Authorization = `Bearer ${newAccessToken}`;
          const newRes = await fetch(finalPath, { ...fetchOptions, headers });

          if (!newRes.ok) {
            throw new Error(`HTTP ${newRes.status}`);
          }

          return parseResponseBody(newRes, responseType);
        } catch (refreshError) {
          isRefreshing = false;
          refreshSubscribers = [];
          throw refreshError;
        }
      }

      if (res.status === 403) {
        throw new Error("Access forbidden");
      }

      if (!res.ok) {
        const text = await res.text().catch(() => "");
        const rid = res.headers.get("X-Request-Id");
        const suffix = rid ? `\nrequestId=${rid}` : "";
        const error = new Error(text || `HTTP ${res.status}` + suffix);
        error.status = res.status;

        // Don't retry client errors (4xx except 429)
        if (res.status >= 400 && res.status < 500 && res.status !== 429) {
          throw error;
        }

        // Check if retryable
        if (attempt < retry && isRetryableError(error, res.status)) {
          lastError = error;
          const delay = retryDelay || getRetryDelay(attempt);
          await new Promise((resolve) => setTimeout(resolve, delay));
          continue;
        }

        throw error;
      }

      return parseResponseBody(res, responseType);
    } catch (error) {
      // Network error or fetch failed
      if (attempt < retry && isRetryableError(error, error.status)) {
        lastError = error;
        const delay = retryDelay || getRetryDelay(attempt);
        await new Promise((resolve) => setTimeout(resolve, delay));
        continue;
      }

      throw error;
    }
  }

  throw lastError || new Error("Request failed after retries");
}
