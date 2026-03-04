import { normalizeConfiguredWsUrl, resolveDefaultWsUrl } from "./ws";

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
