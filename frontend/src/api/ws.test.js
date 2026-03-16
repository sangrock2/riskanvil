import { beforeEach, describe, expect, test, vi } from "vitest";

vi.mock("./http", () => ({
  ensureValidAccessToken: vi.fn(),
}));

import { ensureValidAccessToken } from "./http";
import { QuoteWebSocket, normalizeConfiguredWsUrl, resolveDefaultWsUrl } from "./ws";

class MockWebSocket {
  static OPEN = 1;
  static CLOSED = 3;
  static instances = [];

  constructor(url) {
    this.url = url;
    this.readyState = 0;
    this.sent = [];
    MockWebSocket.instances.push(this);
  }

  addEventListener() {}

  send(payload) {
    this.sent.push(payload);
  }

  close() {
    this.readyState = MockWebSocket.CLOSED;
    this.onclose?.();
  }

  open() {
    this.readyState = MockWebSocket.OPEN;
    this.onopen?.();
  }

  receive(payload) {
    this.onmessage?.({ data: payload });
  }
}

beforeEach(() => {
  MockWebSocket.instances = [];
  vi.stubGlobal("WebSocket", MockWebSocket);
  vi.clearAllMocks();
});

describe("resolveDefaultWsUrl", () => {
  test("maps dev server port 3000 to backend 8080", () => {
    const url = resolveDefaultWsUrl({
      protocol: "http:",
      port: "3000",
      hostname: "localhost",
      host: "localhost:3000",
    });

    expect(url).toBe("ws://localhost:8080/ws/quotes");
  });

  test("uses current host for non-dev ports", () => {
    const url = resolveDefaultWsUrl({
      protocol: "https:",
      port: "443",
      hostname: "example.com",
      host: "example.com",
    });

    expect(url).toBe("wss://example.com/ws/quotes");
  });
});

describe("normalizeConfiguredWsUrl", () => {
  test("converts https URL to wss URL with default quotes path", () => {
    const url = normalizeConfiguredWsUrl("https://api.example.com");
    expect(url).toBe("wss://api.example.com/ws/quotes");
  });

  test("converts host-only value to wss URL with default quotes path", () => {
    const url = normalizeConfiguredWsUrl("api.example.com");
    expect(url).toBe("wss://api.example.com/ws/quotes");
  });
});

describe("QuoteWebSocket", () => {
  test("authenticates before restoring subscriptions", async () => {
    ensureValidAccessToken.mockResolvedValue("access-token-1");
    const client = new QuoteWebSocket({ url: "ws://example.test/ws/quotes" });
    const callback = vi.fn();

    client.subscribe("aapl", callback);

    const socket = MockWebSocket.instances[0];
    socket.open();
    await vi.waitFor(() => expect(socket.sent[0]).toBeTruthy());

    expect(JSON.parse(socket.sent[0])).toEqual({
      action: "auth",
      token: "access-token-1",
    });

    socket.receive(JSON.stringify({ type: "auth", status: "ok" }));

    expect(JSON.parse(socket.sent[1])).toEqual({
      action: "subscribe",
      ticker: "AAPL",
    });
  });
});
