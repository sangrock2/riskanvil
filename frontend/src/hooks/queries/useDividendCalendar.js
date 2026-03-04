import { useQuery } from "@tanstack/react-query";
import { getPortfolioDividendCalendar } from "../../api/dividend";
import { STALE_TIMES } from "../../api/queryClient";

export function useDividendCalendar(portfolioId) {
  return useQuery({
    queryKey: ["dividend-calendar", portfolioId],
    queryFn: () => getPortfolioDividendCalendar(portfolioId),
    enabled: !!portfolioId,
    staleTime: STALE_TIMES.DIVIDENDS,
  });
}
