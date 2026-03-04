import { QueryClient } from "@tanstack/react-query";

// Stale time constants per data type (milliseconds)
export const STALE_TIMES = {
  QUOTE: 30 * 1000,          // 30s - real-time quotes
  PRICES: 2 * 60 * 1000,     // 2min - historical prices
  INSIGHTS: 5 * 60 * 1000,   // 5min - analysis insights
  FUNDAMENTALS: 6 * 60 * 60 * 1000, // 6h - fundamentals
  WATCHLIST: 1 * 60 * 1000,  // 1min - watchlist
  PORTFOLIO: 2 * 60 * 1000,  // 2min - portfolio
  REPORT: 30 * 60 * 1000,    // 30min - reports
  CONVERSATIONS: 1 * 60 * 1000, // 1min - chat conversations
  DIVIDENDS: 5 * 60 * 1000,  // 5min - dividend calendar
  BACKTEST: 5 * 60 * 1000,   // 5min - backtest history
};

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: STALE_TIMES.INSIGHTS,
      gcTime: 10 * 60 * 1000, // 10min garbage collection
      retry: 2,
      refetchOnWindowFocus: false,
    },
  },
});
