import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getPortfolios,
  getPortfolioDetail,
  createPortfolio,
  deletePortfolio,
  addPosition,
  deletePosition,
  getPortfolioEarningsCalendar,
  getPortfolioRiskDashboard
} from "../../api/portfolio";
import { STALE_TIMES } from "../../api/queryClient";

export function usePortfolios() {
  return useQuery({
    queryKey: ["portfolios"],
    queryFn: getPortfolios,
    staleTime: STALE_TIMES.PORTFOLIO,
  });
}

export function usePortfolioDetail(id) {
  return useQuery({
    queryKey: ["portfolio", id],
    queryFn: () => getPortfolioDetail(id),
    enabled: !!id,
    staleTime: STALE_TIMES.PORTFOLIO,
  });
}

export function usePortfolioMutations() {
  const qc = useQueryClient();
  const invalidateAll = () => qc.invalidateQueries({ queryKey: ["portfolios"] });

  const create = useMutation({
    mutationFn: createPortfolio,
    onSuccess: invalidateAll,
  });

  const remove = useMutation({
    mutationFn: deletePortfolio,
    onSuccess: invalidateAll,
  });

  const addPos = useMutation({
    mutationFn: ({ portfolioId, data }) => addPosition(portfolioId, data),
    onSuccess: (_, { portfolioId }) => {
      qc.invalidateQueries({ queryKey: ["portfolio", portfolioId] });
    },
  });

  const removePos = useMutation({
    mutationFn: ({ portfolioId, positionId }) => deletePosition(portfolioId, positionId),
    onSuccess: (_, { portfolioId }) => {
      qc.invalidateQueries({ queryKey: ["portfolio", portfolioId] });
    },
  });

  return { create, remove, addPos, removePos };
}

export function usePortfolioEarningsCalendar(id, daysAhead = 90) {
  return useQuery({
    queryKey: ["portfolio-earnings-calendar", id, daysAhead],
    queryFn: () => getPortfolioEarningsCalendar(id, daysAhead),
    enabled: !!id,
    staleTime: STALE_TIMES.PORTFOLIO,
  });
}

export function usePortfolioRiskDashboard(id, lookbackDays = 252) {
  return useQuery({
    queryKey: ["portfolio-risk-dashboard", id, lookbackDays],
    queryFn: () => getPortfolioRiskDashboard(id, lookbackDays),
    enabled: !!id,
    staleTime: STALE_TIMES.PORTFOLIO,
  });
}
