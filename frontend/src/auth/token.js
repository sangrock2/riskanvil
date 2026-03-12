const AUTH_SYNC_STORAGE_KEY = "stock-ai:auth-sync";
const AUTH_SYNC_CHANNEL_NAME = "stock-ai-auth";
const AUTH_SYNC_SENDER_ID = `tab-${Date.now()}-${Math.random().toString(36).slice(2)}`;
let authSyncChannel = null;

export { AUTH_SYNC_STORAGE_KEY };

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
  applyTokens(accessToken, refreshToken);
  publishAuthSync({
    type: "tokens",
    accessToken,
    refreshToken: refreshToken || getRefreshToken(),
  });
}

export function syncTokens(accessToken, refreshToken) {
  applyTokens(accessToken, refreshToken);
}

/**
 * Clear all tokens (logout)
 */
export function clearAllTokens(reason = "logout") {
  clearTokenState();
  publishAuthSync({ type: "logout", reason });
}

export function clearTokensLocally() {
  clearTokenState();
}

export function subscribeAuthSync(listener) {
  const seenEventIds = new Set();

  function deliver(message) {
    if (!message || typeof listener !== "function") return;
    if (message.senderId === AUTH_SYNC_SENDER_ID) return;
    if (message.eventId && seenEventIds.has(message.eventId)) return;
    if (message.eventId) {
      seenEventIds.add(message.eventId);
      if (seenEventIds.size > 20) {
        const first = seenEventIds.values().next().value;
        if (first) seenEventIds.delete(first);
      }
    }
    listener(message);
  }

  function handleStorage(event) {
    if (event.key !== AUTH_SYNC_STORAGE_KEY || !event.newValue) return;
    try {
      deliver(JSON.parse(event.newValue));
    } catch {
      // ignore malformed sync payloads
    }
  }

  window.addEventListener("storage", handleStorage);

  const channel = getAuthSyncChannel();
  let handleMessage = null;
  if (channel) {
    handleMessage = (event) => deliver(event.data);
    channel.addEventListener("message", handleMessage);
  }

  return () => {
    window.removeEventListener("storage", handleStorage);
    if (channel && handleMessage) {
      channel.removeEventListener("message", handleMessage);
    }
  };
}

function getAuthSyncChannel() {
  if (typeof window === "undefined" || typeof BroadcastChannel === "undefined") {
    return null;
  }
  if (!authSyncChannel) {
    authSyncChannel = new BroadcastChannel(AUTH_SYNC_CHANNEL_NAME);
  }
  return authSyncChannel;
}

function publishAuthSync(message) {
  const payload = {
    ...message,
    senderId: AUTH_SYNC_SENDER_ID,
    eventId: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
  };

  const channel = getAuthSyncChannel();
  if (channel) {
    channel.postMessage(payload);
  }

  try {
    localStorage.setItem(AUTH_SYNC_STORAGE_KEY, JSON.stringify(payload));
    localStorage.removeItem(AUTH_SYNC_STORAGE_KEY);
  } catch {
    // storage can fail in private mode or strict browser settings
  }
}

function applyTokens(accessToken, refreshToken) {
  clearServiceWorkerCache();
  if (accessToken) {
    setToken(accessToken);
  } else {
    clearToken();
  }
  if (refreshToken) {
    setRefreshToken(refreshToken);
  }
}

function clearTokenState() {
  clearToken();
  clearRefreshToken();
  clearServiceWorkerCache();
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
