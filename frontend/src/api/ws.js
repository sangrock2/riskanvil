/**
 * 실시간 시세 WebSocket 클라이언트
 * Backend WebSocket 서버에 연결하여 실시간 시세 수신
 */

export function resolveDefaultWsUrl(loc = null) {
  if (typeof window === "undefined" && !loc) {
    return "ws://localhost:8080/ws/quotes";
  }

  const location = loc || window.location;
  const isLocalDevPort = location.port === "3000" || location.port === "5173";
  const host = isLocalDevPort ? `${location.hostname}:8080` : location.host;
  const proto = location.protocol === "https:" ? "wss" : "ws";
  return `${proto}://${host}/ws/quotes`;
}

export function normalizeConfiguredWsUrl(url) {
  if (!url) return null;

  const trimmed = url.trim();
  if (!trimmed) return null;

  if (/^wss?:\/\//i.test(trimmed)) {
    return trimmed;
  }

  if (/^https?:\/\//i.test(trimmed)) {
    const parsed = new URL(trimmed);
    const protocol = parsed.protocol === "https:" ? "wss:" : "ws:";
    const pathname = parsed.pathname === "/" ? "/ws/quotes" : parsed.pathname;
    return `${protocol}//${parsed.host}${pathname}${parsed.search}${parsed.hash}`;
  }

  const parsed = new URL(`https://${trimmed.replace(/^\/+/, "")}`);
  const pathname = parsed.pathname === "/" ? "/ws/quotes" : parsed.pathname;
  return `wss://${parsed.host}${pathname}${parsed.search}${parsed.hash}`;
}

const WS_URL =
  normalizeConfiguredWsUrl(
    import.meta.env.VITE_WS_URL ||
    import.meta.env.REACT_APP_WS_URL
  ) ||
  resolveDefaultWsUrl();

class QuoteWebSocket {
  constructor() {
    this.ws = null;
    this.subscribers = new Map(); // ticker -> Set<callback>
    this.reconnectDelay = 1000;
    this.maxReconnectDelay = 30000;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 20;
    this._connecting = false;
    this._intentionalClose = false;
    this._reconnectTimer = null;
    this._heartbeatTimer = null;
    this._pongTimeoutTimer = null;
    this._visibilityHandler = null;
  }

  /**
   * WebSocket 연결을 수립하고, 연결 성공 시 기존 구독 티커를 재등록한다.
   */
  connect() {
    if (this.ws?.readyState === WebSocket.OPEN || this._connecting) return;
    if (this.reconnectAttempts >= this.maxReconnectAttempts && this.subscribers.size > 0) {
      console.warn(`[WS] Reconnect attempts exceeded (${this.maxReconnectAttempts})`);
      return;
    }

    this._connecting = true;
    this._intentionalClose = false;

    try {
      this.ws = new WebSocket(WS_URL);

      this.ws.onopen = () => {
        this._connecting = false;
        this.reconnectAttempts = 0;
        this.reconnectDelay = 1000;
        this._clearReconnectTimer();
        this._startHeartbeat();
        console.log('[WS] Connected to quote stream');

        // 기존 구독 복원
        for (const ticker of this.subscribers.keys()) {
          this._send({ action: 'subscribe', ticker });
        }
      };

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data?.type === "pong") {
            this._clearPongTimeout();
            return;
          }
          const ticker = data.ticker;
          if (ticker && this.subscribers.has(ticker)) {
            for (const cb of this.subscribers.get(ticker)) {
              cb(data);
            }
          }
        } catch (e) {
          // ignore parse errors
        }
      };

      this.ws.onclose = () => {
        this._connecting = false;
        this._stopHeartbeat();
        if (!this._intentionalClose) {
          this._scheduleReconnect();
        }
      };

      this.ws.onerror = (err) => {
        this._connecting = false;
        console.error('[WS] Error:', err);
      };
    } catch (e) {
      this._connecting = false;
      console.error('[WS] Connection failed:', e);
      this._scheduleReconnect();
    }
  }

  /**
   * 티커 구독 콜백을 등록한다.
   * 연결이 없으면 자동 연결 후 onopen 시점에 subscribe를 전송한다.
   */
  subscribe(ticker, callback) {
    if (!ticker || !callback) return;
    const upper = ticker.toUpperCase();

    if (!this.subscribers.has(upper)) {
      this.subscribers.set(upper, new Set());
    }
    this.subscribers.get(upper).add(callback);

    this._ensureVisibilityHandler();

    // 자동 연결
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      this.connect();
    } else {
      this._send({ action: 'subscribe', ticker: upper });
    }
  }

  /**
   * 티커 구독 콜백을 해제한다.
   * 마지막 구독자가 사라지면 연결도 함께 종료한다.
   */
  unsubscribe(ticker, callback) {
    if (!ticker || !callback) return;
    const upper = ticker.toUpperCase();

    const callbacks = this.subscribers.get(upper);
    if (callbacks) {
      callbacks.delete(callback);
      if (callbacks.size === 0) {
        this.subscribers.delete(upper);
        this._send({ action: 'unsubscribe', ticker: upper });
      }
    }

    // 구독자가 없으면 연결 종료
    if (this.subscribers.size === 0) {
      this.disconnect();
    }
  }

  /**
   * 의도적인 종료 플래그를 설정하고 소켓을 닫는다.
   */
  disconnect() {
    this._intentionalClose = true;
    this._clearReconnectTimer();
    this._stopHeartbeat();
    this._teardownVisibilityHandler();
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  /**
   * 소켓이 열린 상태일 때만 JSON 메시지를 전송한다.
   */
  _send(data) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
    }
  }

  _scheduleReconnect() {
    if (this.subscribers.size === 0) {
      return;
    }
    this.reconnectAttempts += 1;
    const jitter = 0.8 + Math.random() * 0.4;
    const delay = Math.floor(this.reconnectDelay * jitter);
    console.log(`[WS] Disconnected. Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})...`);
    this._clearReconnectTimer();
    this._reconnectTimer = setTimeout(() => this.connect(), delay);
    this.reconnectDelay = Math.min(this.reconnectDelay * 2, this.maxReconnectDelay);
  }

  _clearReconnectTimer() {
    if (this._reconnectTimer) {
      clearTimeout(this._reconnectTimer);
      this._reconnectTimer = null;
    }
  }

  _startHeartbeat() {
    this._stopHeartbeat();
    this._heartbeatTimer = setInterval(() => {
      if (typeof document !== "undefined" && document.visibilityState === "hidden") {
        return;
      }
      this._send({ action: "ping" });
      this._clearPongTimeout();
      this._pongTimeoutTimer = setTimeout(() => {
        if (this.ws?.readyState === WebSocket.OPEN) {
          console.warn("[WS] Pong timeout. Closing stale socket.");
          this.ws.close();
        }
      }, 10000);
    }, 25000);
  }

  _stopHeartbeat() {
    if (this._heartbeatTimer) {
      clearInterval(this._heartbeatTimer);
      this._heartbeatTimer = null;
    }
    this._clearPongTimeout();
  }

  _clearPongTimeout() {
    if (this._pongTimeoutTimer) {
      clearTimeout(this._pongTimeoutTimer);
      this._pongTimeoutTimer = null;
    }
  }

  _ensureVisibilityHandler() {
    if (typeof document === "undefined" || this._visibilityHandler) return;
    this._visibilityHandler = () => {
      if (document.visibilityState === "visible" && this.subscribers.size > 0) {
        if (!this.ws || this.ws.readyState === WebSocket.CLOSED) {
          this.connect();
        }
      }
    };
    document.addEventListener("visibilitychange", this._visibilityHandler);
  }

  _teardownVisibilityHandler() {
    if (typeof document === "undefined" || !this._visibilityHandler) return;
    document.removeEventListener("visibilitychange", this._visibilityHandler);
    this._visibilityHandler = null;
  }
}

export const quoteWS = new QuoteWebSocket();
