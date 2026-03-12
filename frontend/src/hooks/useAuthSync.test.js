import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { AUTH_SYNC_STORAGE_KEY } from "../auth/token";
import { useAuthSync } from "./useAuthSync";

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

describe("useAuthSync", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  test("hydrates access and refresh tokens from another tab login", async () => {
    render(
      <MemoryRouter initialEntries={["/login"]}>
        <SyncHarness />
      </MemoryRouter>
    );

    dispatchAuthSync({
      type: "tokens",
      accessToken: "access-1",
      refreshToken: "refresh-1",
    });

    expect(await screen.findByText("dashboard-page")).toBeInTheDocument();
    expect(localStorage.getItem("accessToken")).toBe("access-1");
    expect(sessionStorage.getItem("refreshToken")).toBe("refresh-1");
  });

  test("clears local state when another tab logs out", async () => {
    localStorage.setItem("accessToken", "access-1");
    sessionStorage.setItem("refreshToken", "refresh-1");

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
      expect(localStorage.getItem("accessToken")).toBeNull();
      expect(sessionStorage.getItem("refreshToken")).toBeNull();
    });
  });
});
