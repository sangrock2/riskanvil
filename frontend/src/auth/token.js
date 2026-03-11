/**
 * Get the access token from localStorage
 * @returns {string|null} The access token or null if not found
 */
export function getToken() {
  return localStorage.getItem("accessToken");
}

/**
 * Store the access token in localStorage
 * @param {string} token - The JWT access token
 */
export function setToken(token) {
  localStorage.setItem("accessToken", token);
}

/**
 * Remove the access token from localStorage
 */
export function clearToken() {
  localStorage.removeItem("accessToken");
}

/**
 * Get the refresh token from localStorage
 * @returns {string|null} The refresh token or null if not found
 */
export function getRefreshToken() {
  return sessionStorage.getItem("refreshToken");
}

/**
 * Store the refresh token in localStorage
 * @param {string} token - The refresh token
 */
export function setRefreshToken(token) {
  if (!token) return;
  sessionStorage.setItem("refreshToken", token);
}

/**
 * Remove the refresh token from localStorage
 */
export function clearRefreshToken() {
  sessionStorage.removeItem("refreshToken");
  // backward compatibility: remove legacy localStorage key if present
  localStorage.removeItem("refreshToken");
}

/**
 * Store both access and refresh tokens
 * @param {string} accessToken - The JWT access token
 * @param {string} refreshToken - The refresh token
 */
export function setTokens(accessToken, refreshToken) {
  clearServiceWorkerCache();
  setToken(accessToken);
  if (refreshToken) {
    setRefreshToken(refreshToken);
  }

  // Dispatch event for multi-tab sync
  window.dispatchEvent(new CustomEvent('auth:login', {
    detail: { accessToken, refreshToken }
  }));
}

/**
 * Clear all tokens (logout)
 */
export function clearAllTokens() {
  clearToken();
  clearRefreshToken();
  clearServiceWorkerCache();

  // Dispatch event for multi-tab sync
  window.dispatchEvent(new CustomEvent('auth:logout'));
}

/**
 * Check if user is authenticated (has valid, non-expired token)
 * @returns {boolean} True if authenticated with valid token
 */
export function isAuthenticated() {
  const token = getToken();
  if (!token) return false;

  // Import isTokenExpired from http.js to avoid circular dependency
  // This is a simple check - full validation happens in http.js
  try {
    const [, payload] = token.split(".");
    if (!payload) return false;

    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    const data = JSON.parse(decodeURIComponent(escape(json)));
    const exp = data?.exp;

    if (!exp) return true; // If no exp, let server decide

    const now = Math.floor(Date.now() / 1000);
    return exp > now + 10; // 10 second skew
  } catch {
    return false;
  }
}

function clearServiceWorkerCache() {
  try {
    if ("serviceWorker" in navigator && navigator.serviceWorker.controller) {
      navigator.serviceWorker.controller.postMessage({ type: "CLEAR_CACHE" });
    }
  } catch {
    // no-op
  }
}
