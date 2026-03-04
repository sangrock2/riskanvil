import { apiFetch } from "./http";

/**
 * User settings and 2FA API client
 */

// User Settings
export async function getSettings() {
  return apiFetch("/api/settings");
}

export async function updateSettings(data) {
  return apiFetch("/api/settings", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data)
  });
}

// Two-Factor Authentication
export async function setupTotp() {
  return apiFetch("/api/2fa/setup", {
    method: "POST"
  });
}

export async function verifyTotp(code) {
  return apiFetch("/api/2fa/verify", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code })
  });
}

export async function disableTotp(password, totpCode) {
  return apiFetch("/api/2fa/disable", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ password, totpCode })
  });
}
