import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { AUTH_SYNC_STORAGE_KEY } from "../auth/token";
import { useAuthSync } from "./useAuthSync";

class MockBroadcastChannel {
  static instances = [];

  constructor() {
    this.listeners = new Set();
    MockBroadcastChannel.instances.push(this);
  }

  addEventListener(type, handler) {
    if (type === "message") {
      this.listeners.add(handler);
    }
  }

  removeEventListener(type, handler) {
    if (type === "message") {
      this.listeners.delete(handler);
    }
  }

  postMessage() {}

  emit(data) {
    for (const handler of this.listeners) {
      handler({ data });
    }
  }
}

function SyncHarness() {
  useAuthSync();

  return (
    <Routes>
      <Route path="/login" element={<div>login-page</div>} />
      <Route path="/dashboard" element={<div>dashboard-page</div>} />
    </Routes>
  );
}

function dispatchAuthSync(message) {
  window.dispatchEvent(new StorageEvent("storage", {
    key: AUTH_SYNC_STORAGE_KEY,
    newValue: JSON.stringify({
      senderId: "remote-tab",
      eventId: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      ...message,
    }),
  }));
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

describe("useAuthSync", () => {
  beforeEach(() => {
    MockBroadcastChannel.instances = [];
    vi.stubGlobal("BroadcastChannel", MockBroadcastChannel);
    window.BroadcastChannel = MockBroadcastChannel;
    localStorage.clear();
    sessionStorage.clear();
    sessionStorage.setItem("lastActivity", String(Date.now()));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    delete window.BroadcastChannel;
    localStorage.clear();
    sessionStorage.clear();
  });

  test("hydrates access and refresh tokens from another tab login", async () => {
    render(
      <MemoryRouter initialEntries={["/login"]}>
        <SyncHarness />
      </MemoryRouter>
    );

    MockBroadcastChannel.instances[0].emit({
      type: "tokens",
      accessToken: "access-1",
      senderId: "remote-tab",
      eventId: "evt-1",
    });

    expect(await screen.findByText("dashboard-page")).toBeInTheDocument();
    expect(sessionStorage.getItem("accessToken")).toBe("access-1");
    expect(localStorage.getItem("accessToken")).toBeNull();
  });

  test("clears local state when another tab logs out", async () => {
    sessionStorage.setItem("accessToken", "access-1");

    render(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <SyncHarness />
      </MemoryRouter>
    );

    dispatchAuthSync({
      type: "logout",
      reason: "logout",
    });

    expect(await screen.findByText("login-page")).toBeInTheDocument();
    await waitFor(() => {
      expect(sessionStorage.getItem("accessToken")).toBeNull();
      expect(sessionStorage.getItem("refreshToken")).toBeNull();
    });
  });

  test("refreshes access token from cookie-backed session when storage fallback fires", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(
      createJsonResponse({ accessToken: "access-from-cookie" })
    ));

    render(
      <MemoryRouter initialEntries={["/login"]}>
        <SyncHarness />
      </MemoryRouter>
    );

    dispatchAuthSync({
      type: "session-updated",
    });

    expect(await screen.findByText("dashboard-page")).toBeInTheDocument();
    expect(sessionStorage.getItem("accessToken")).toBe("access-from-cookie");
  });
});
