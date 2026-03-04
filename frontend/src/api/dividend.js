import { apiFetch } from "./http";

/**
 * Fetch dividend history for a specific position
 */
export async function getDividendHistory(positionId) {
    return apiFetch(`/api/dividend/position/${positionId}/history`);
}

/**
 * Fetch and store dividends for a position from yfinance
 */
export async function fetchDividendsForPosition(positionId) {
    return apiFetch(`/api/dividend/position/${positionId}/fetch`, {
        method: "POST"
    });
}

/**
 * Get dividend calendar for entire portfolio
 */
export async function getPortfolioDividendCalendar(portfolioId) {
    return apiFetch(`/api/dividend/portfolio/${portfolioId}/calendar`);
}
