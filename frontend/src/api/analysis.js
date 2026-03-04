import { apiFetch } from "./http";

/**
 * Advanced analysis API client (Correlation, Monte Carlo)
 */

export async function analyzeCorrelation(tickers, market = "US", days = 90) {
  return apiFetch("/api/correlation", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      tickers,
      market,
      days
    })
  });
}

export async function runMonteCarloSimulation(
  ticker,
  market = "US",
  days = 90,
  simulations = 1000,
  forecastDays = 30,
  options = {}
) {
  const { model = "gbm", scenarios = false, confidenceBands = true } = options;

  return apiFetch("/api/monte-carlo", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      ticker,
      market,
      days,
      simulations,
      forecastDays,
      model,
      scenarios,
      confidenceBands
    })
  });
}
