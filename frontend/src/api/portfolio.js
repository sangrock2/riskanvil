import { apiFetch } from "./http";

/**
 * Portfolio management API client
 */

const PORTFOLIO_REQUEST_OPTIONS = {
  timeoutMs: 15000,
  retry: 0,
};

export async function getPortfolios(options = {}) {
  return apiFetch("/api/portfolio", {
    ...PORTFOLIO_REQUEST_OPTIONS,
    ...options,
  });
}

export async function getPortfolioDetail(id, options = {}) {
  return apiFetch(`/api/portfolio/${id}`, {
    ...PORTFOLIO_REQUEST_OPTIONS,
    ...options,
  });
}

export async function createPortfolio(data) {
  return apiFetch("/api/portfolio", {
    ...PORTFOLIO_REQUEST_OPTIONS,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data)
  });
}

export async function updatePortfolio(id, data) {
  return apiFetch(`/api/portfolio/${id}`, {
    ...PORTFOLIO_REQUEST_OPTIONS,
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data)
  });
}

export async function deletePortfolio(id) {
  return apiFetch(`/api/portfolio/${id}`, {
    ...PORTFOLIO_REQUEST_OPTIONS,
    method: "DELETE"
  });
}

export async function addPosition(portfolioId, data) {
  return apiFetch(`/api/portfolio/${portfolioId}/position`, {
    ...PORTFOLIO_REQUEST_OPTIONS,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data)
  });
}

export async function updatePosition(portfolioId, positionId, data) {
  return apiFetch(`/api/portfolio/${portfolioId}/position/${positionId}`, {
    ...PORTFOLIO_REQUEST_OPTIONS,
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data)
  });
}

export async function deletePosition(portfolioId, positionId) {
  return apiFetch(`/api/portfolio/${portfolioId}/position/${positionId}`, {
    ...PORTFOLIO_REQUEST_OPTIONS,
    method: "DELETE"
  });
}

export async function getPortfolioEarningsCalendar(portfolioId, daysAhead = 90, options = {}) {
  return apiFetch(`/api/portfolio/${portfolioId}/earnings-calendar?daysAhead=${daysAhead}`, {
    ...PORTFOLIO_REQUEST_OPTIONS,
    ...options,
  });
}

export async function getPortfolioRiskDashboard(portfolioId, lookbackDays = 252, options = {}) {
  return apiFetch(`/api/portfolio/${portfolioId}/risk-dashboard?lookbackDays=${lookbackDays}`, {
    ...PORTFOLIO_REQUEST_OPTIONS,
    ...options,
  });
}

/**
 * 포트폴리오 리밸런싱 추천
 * @param {number} portfolioId
 * @param {Object} targetWeights - { ticker: weight } 또는 { "TICKER:MARKET": weight }, 합계 1.0
 * @returns {Promise<{totalValue: number, trades: Array}>}
 */
export async function rebalancePortfolio(portfolioId, targetWeights) {
  return apiFetch(`/api/portfolio/${portfolioId}/rebalance`, {
    ...PORTFOLIO_REQUEST_OPTIONS,
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
    ...PORTFOLIO_REQUEST_OPTIONS,
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
    ...PORTFOLIO_REQUEST_OPTIONS,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ticker, ...options })
  });
}
