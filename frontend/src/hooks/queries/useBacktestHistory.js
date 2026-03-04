import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../api/http";
import { STALE_TIMES } from "../../api/queryClient";

export function useBacktestHistory(page = 0, size = 20, sort = "createdAt,desc", filters = {}) {
  const qs = new URLSearchParams();
  qs.set("page", String(page));
  qs.set("size", String(size));
  qs.set("sort", sort);
  if (filters.ticker) qs.set("ticker", filters.ticker.toUpperCase());
  if (filters.strategy) qs.set("strategy", filters.strategy);

  return useQuery({
    queryKey: ["backtest-history", page, size, sort, filters.ticker, filters.strategy],
    queryFn: () => apiFetch(`/api/backtest/history?${qs.toString()}`),
    staleTime: STALE_TIMES.BACKTEST,
  });
}

export function useBacktestDetail(id) {
  return useQuery({
    queryKey: ["backtest", id],
    queryFn: () => apiFetch(`/api/backtest/${id}`),
    enabled: !!id,
    staleTime: STALE_TIMES.BACKTEST,
  });
}

export function useBacktestMutation() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (data) =>
      apiFetch("/api/backtest", {
        method: "POST",
        body: JSON.stringify(data),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["backtest-history"] }),
  });
}
