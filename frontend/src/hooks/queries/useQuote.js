import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../api/http";
import { STALE_TIMES } from "../../api/queryClient";

export function useQuote(ticker, market = "US", options = {}) {
  return useQuery({
    queryKey: ["quote", ticker, market],
    queryFn: () => apiFetch(`/api/market/quote?ticker=${encodeURIComponent(ticker)}&market=${encodeURIComponent(market)}`),
    enabled: !!ticker,
    staleTime: STALE_TIMES.QUOTE,
    ...options,
  });
}
