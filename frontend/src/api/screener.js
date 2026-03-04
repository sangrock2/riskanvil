import { apiFetch } from "./http";

/**
 * Stock screener API client
 */

export async function screenStocks(filters, market = "US", sortBy = "pe", sortOrder = "asc") {
  return apiFetch("/api/screener", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      market,
      filters,
      sortBy,
      sortOrder
    })
  });
}

export async function saveScreenerPreset(data) {
  return apiFetch("/api/screener/preset", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data)
  });
}

export async function getScreenerPresets() {
  return apiFetch("/api/screener/preset");
}

export async function getPublicPresets() {
  return apiFetch("/api/screener/preset/public");
}
