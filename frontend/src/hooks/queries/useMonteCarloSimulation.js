import { useMutation } from "@tanstack/react-query";
import { runMonteCarloSimulation } from "../../api/analysis";

export function useMonteCarloSimulation() {
  return useMutation({
    mutationFn: ({ ticker, market, days, simulations, forecastDays, model, scenarios, confidenceBands }) =>
      runMonteCarloSimulation(ticker, market, days, simulations, forecastDays, { model, scenarios, confidenceBands }),
  });
}
