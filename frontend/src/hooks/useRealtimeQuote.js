import { useState, useEffect, useCallback } from 'react';
import { quoteWS } from '../api/ws';

/**
 * 실시간 시세 Hook
 * @param {string} ticker - 종목 티커
 * @returns {{ price: number|null, change: number|null, volume: number|null, timestamp: number|null }}
 */
export function useRealtimeQuote(ticker) {
  const [quote, setQuote] = useState(null);

  const handleUpdate = useCallback((data) => {
    setQuote({
      price: data.price,
      change: data.change,
      volume: data.volume,
      timestamp: data.timestamp,
      ticker: data.ticker,
    });
  }, []);

  useEffect(() => {
    if (!ticker) return;
    const upper = ticker.toUpperCase();

    quoteWS.subscribe(upper, handleUpdate);

    return () => {
      quoteWS.unsubscribe(upper, handleUpdate);
    };
  }, [ticker, handleUpdate]);

  return quote;
}
