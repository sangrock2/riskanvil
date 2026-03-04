import { apiFetch } from "./http";

/**
 * Portfolio management API client
 */

export async function getPortfolios() {
  return apiFetch("/api/portfolio");
}

export async function getPortfolioDetail(id) {
  return apiFetch(`/api/portfolio/${id}`);
}

export async function createPortfolio(data) {
  return apiFetch("/api/portfolio", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data)
  });
}

export async function updatePortfolio(id, data) {
  return apiFetch(`/api/portfolio/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data)
  });
}

export async function deletePortfolio(id) {
  return apiFetch(`/api/portfolio/${id}`, {
    method: "DELETE"
  });
}

export async function addPosition(portfolioId, data) {
  return apiFetch(`/api/portfolio/${portfolioId}/position`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data)
  });
}

export async function updatePosition(portfolioId, positionId, data) {
  return apiFetch(`/api/portfolio/${portfolioId}/position/${positionId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data)
  });
}

export async function deletePosition(portfolioId, positionId) {
  return apiFetch(`/api/portfolio/${portfolioId}/position/${positionId}`, {
    method: "DELETE"
  });
}

export async function getPortfolioEarningsCalendar(portfolioId, daysAhead = 90) {
  return apiFetch(`/api/portfolio/${portfolioId}/earnings-calendar?daysAhead=${daysAhead}`);
}

export async function getPortfolioRiskDashboard(portfolioId, lookbackDays = 252) {
  return apiFetch(`/api/portfolio/${portfolioId}/risk-dashboard?lookbackDays=${lookbackDays}`);
}

/**
 * 포트폴리오 리밸런싱 추천
 * @param {number} portfolioId
 * @param {Object} targetWeights - { ticker: weight (0~1), ... }, 합계 1.0
 * @returns {Promise<{totalValue: number, trades: Array}>}
 */
export async function rebalancePortfolio(portfolioId, targetWeights) {
  return apiFetch(`/api/portfolio/${portfolioId}/rebalance`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ targetWeights })
  });
}

/**
 * 효율적 프론티어 계산 (AI 서비스 직접 호출)
 * @param {string[]} tickers
 * @param {Object} options - { start, end, nSimulations }
 */
export async function getEfficientFrontier(tickers, options = {}) {
  return apiFetch("/api/portfolio/efficient-frontier", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ tickers, ...options })
  });
}

/**
 * 유사 종목 추천
 * @param {string} ticker
 * @param {Object} options - { topN, excludeSector }
 */
export async function getSimilarStocks(ticker, options = {}) {
  return apiFetch(`/api/similarity/find`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ticker, ...options })
  });
}
