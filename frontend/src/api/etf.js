import { apiFetch } from "./http";

/**
 * Get ETF holdings and composition
 */
export async function getETFHoldings(ticker, market = "US") {
    const params = new URLSearchParams({ market });
    return apiFetch(`/api/ai/etf/holdings/${ticker}?${params}`);
}

/**
 * Get basic ETF information
 */
export async function getETFInfo(ticker, market = "US") {
    const params = new URLSearchParams({ market });
    return apiFetch(`/api/ai/etf/info/${ticker}?${params}`);
}
