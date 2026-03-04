import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../api/http";
import { STALE_TIMES } from "../../api/queryClient";

export function useInsights(ticker, market = "US", opts = {}) {
  const { days = 90, newsLimit = 20, test = false, refresh = false, ...queryOpts } = opts;

  return useQuery({
    queryKey: ["insights", ticker, market, days, newsLimit],
    queryFn: () =>
      apiFetch(`/api/market/insights?test=${test}&refresh=${refresh}`, {
        method: "POST",
        body: JSON.stringify({ ticker, market, days, newsLimit }),
      }),
    enabled: !!ticker,
    staleTime: STALE_TIMES.INSIGHTS,
    ...queryOpts,
  });
}
