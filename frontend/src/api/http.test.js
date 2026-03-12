import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { apiFetch } from "./http";
import ProtectedRoute from "../components/ProtectedRoute";

function base64UrlEncode(value) {
  return btoa(JSON.stringify(value))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function makeToken(expiresInSeconds) {
  const exp = Math.floor(Date.now() / 1000) + expiresInSeconds;
  return `header.${base64UrlEncode({ exp })}.signature`;
}

function createJsonResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ "content-type": "application/json" }),
    json: async () => body,
    text: async () => JSON.stringify(body),
  };
}

describe("apiFetch auth refresh", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    sessionStorage.setItem("lastActivity", String(Date.now()));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    localStorage.clear();
    sessionStorage.clear();
  });

  test("refreshes an expired access token before a protected request", async () => {
    const expiredToken = makeToken(-30);
    const freshToken = makeToken(300);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(createJsonResponse({
        accessToken: freshToken,
        refreshToken: "refresh-2",
      }))
      .mockResolvedValueOnce(createJsonResponse({ ok: true }));

    vi.stubGlobal("fetch", fetchMock);
    localStorage.setItem("accessToken", expiredToken);
    sessionStorage.setItem("refreshToken", "refresh-1");

    const result = await apiFetch("/api/protected");

    expect(result).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/auth/refresh");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/protected");
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe(`Bearer ${freshToken}`);
    expect(sessionStorage.getItem("refreshToken")).toBe("refresh-2");
  });

  test("does not try to refresh before a public auth request", async () => {
    const expiredToken = makeToken(-30);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(createJsonResponse({ accessToken: "issued-now" }));

    vi.stubGlobal("fetch", fetchMock);
    localStorage.setItem("accessToken", expiredToken);
    sessionStorage.setItem("refreshToken", "refresh-1");

    await apiFetch("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email: "user@example.com", password: "pw" }),
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/auth/login");
  });

  test("parses json error payload without exposing the whole raw body as the message", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce({
      ok: false,
      status: 400,
      headers: new Headers({
        "content-type": "application/json",
        "X-Request-Id": "req-123",
      }),
      text: async () => JSON.stringify({
        timestamp: "2026-03-12T18:57:59.832816031Z",
        status: 400,
        error: "illegal_state",
        message: "already exists",
        path: "/api/watchlist",
        requestId: "req-123",
      }),
    });

    vi.stubGlobal("fetch", fetchMock);

    await expect(apiFetch("/api/watchlist", {
      method: "POST",
      body: JSON.stringify({ ticker: "AAPL", market: "US" }),
      retry: 0,
    })).rejects.toMatchObject({
      message: "already exists",
      status: 400,
      code: "illegal_state",
      requestId: "req-123",
      path: "/api/watchlist",
    });
  });
});

describe("ProtectedRoute", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    sessionStorage.setItem("lastActivity", String(Date.now()));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    localStorage.clear();
    sessionStorage.clear();
  });

  test("keeps the user inside a protected route after silent refresh", async () => {
    const expiredToken = makeToken(-30);
    const freshToken = makeToken(300);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(createJsonResponse({
        accessToken: freshToken,
        refreshToken: "refresh-2",
      }));

    vi.stubGlobal("fetch", fetchMock);
    localStorage.setItem("accessToken", expiredToken);
    sessionStorage.setItem("refreshToken", "refresh-1");

    render(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <Routes>
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <div>protected-content</div>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<div>login-page</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText("protected-content")).toBeInTheDocument();
    expect(screen.queryByText("login-page")).not.toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(fetchMock.mock.calls[0][0]).toBe("/api/auth/refresh");
  });
});
