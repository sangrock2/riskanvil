package com.sw103302.backend.dto;

public record AiBacktestRequest(
        String ticker,
        String market,
        String strategy,
        String start,
        String end,
        Double initialCapital,
        Double feeBps,
        Integer maxPoints
) {
}
