package com.sw103302.backend.dto;

import java.util.List;
import java.util.Map;

public record CorrelationResponse(
    List<String> tickers,
    double[][] correlationMatrix,
    Map<String, StockStats> stats
) {
    public record StockStats(
        String ticker,
        double mean,
        double stdDev,
        double sharpe,
        double beta
    ) {}
}
