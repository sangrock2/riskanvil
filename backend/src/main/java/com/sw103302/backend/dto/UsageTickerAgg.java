package com.sw103302.backend.dto;

public record UsageTickerAgg(
        String ticker,
        long totalCalls,
        long cachedCalls,
        long alphaCalls,
        long openaiCalls
) {
}
