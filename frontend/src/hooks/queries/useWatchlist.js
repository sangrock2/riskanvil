import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../api/http";
import { STALE_TIMES } from "../../api/queryClient";

export function useWatchlist(test = false) {
  return useQuery({
    queryKey: ["watchlist"],
    queryFn: () => apiFetch(`/api/watchlist?test=${test}`),
    staleTime: STALE_TIMES.WATCHLIST,
    select: (data) => (Array.isArray(data) ? data : []),
  });
}

export function useWatchlistTags() {
  return useQuery({
    queryKey: ["watchlist-tags"],
    queryFn: () => apiFetch("/api/watchlist/tags"),
    staleTime: STALE_TIMES.WATCHLIST,
    select: (data) => (Array.isArray(data) ? data : []),
  });
}

export function useWatchlistMutations(test = false) {
  const qc = useQueryClient();
  const invalidate = () => qc.invalidateQueries({ queryKey: ["watchlist"] });

  const addItem = useMutation({
    mutationFn: ({ ticker, market }) =>
      apiFetch(`/api/watchlist?test=${test}`, {
        method: "POST",
        body: JSON.stringify({ ticker, market }),
      }),
    onSuccess: invalidate,
  });

  const removeItem = useMutation({
    mutationFn: ({ ticker, market }) =>
      apiFetch(`/api/watchlist?ticker=${encodeURIComponent(ticker)}&market=${encodeURIComponent(market)}&test=${test}`, {
        method: "DELETE",
      }),
    onSuccess: invalidate,
  });

  const updateNotes = useMutation({
    mutationFn: ({ itemId, notes }) =>
      apiFetch(`/api/watchlist/${itemId}/notes`, {
        method: "PUT",
        body: JSON.stringify({ notes }),
      }),
    onSuccess: invalidate,
  });

  const updateItemTags = useMutation({
    mutationFn: ({ itemId, tagIds }) =>
      apiFetch(`/api/watchlist/${itemId}/tags`, {
        method: "PUT",
        body: JSON.stringify({ tagIds }),
      }),
    onSuccess: invalidate,
  });

  const createTag = useMutation({
    mutationFn: ({ name, color }) =>
      apiFetch("/api/watchlist/tags", {
        method: "POST",
        body: JSON.stringify({ name, color }),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["watchlist-tags"] }),
  });

  const deleteTag = useMutation({
    mutationFn: (tagId) =>
      apiFetch(`/api/watchlist/tags/${tagId}`, { method: "DELETE" }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["watchlist-tags"] });
      invalidate();
    },
  });

  const updateInsights = useMutation({
    mutationFn: ({ ticker, market }) =>
      apiFetch(`/api/market/insights?test=${test}&refresh=true`, {
        method: "POST",
        body: JSON.stringify({ ticker, market, days: 90, newsLimit: 20 }),
      }),
    onSuccess: invalidate,
  });

  return { addItem, removeItem, updateNotes, updateItemTags, createTag, deleteTag, updateInsights };
}
