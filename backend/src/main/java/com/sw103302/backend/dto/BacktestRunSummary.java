package com.sw103302.backend.dto;

import java.time.Instant;

public record BacktestRunSummary(
        Long id,
        String ticker,
        String market,
        String strategy,
        Double totalReturn,
        Double maxDrawdown,
        Double sharpe,
        Double cagr,
        Instant createdAt
) {
}
