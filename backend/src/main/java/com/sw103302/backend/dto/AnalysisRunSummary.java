package com.sw103302.backend.dto;

import java.time.Instant;

public record AnalysisRunSummary(
        Long id,
        String ticker,
        String market,
        String action,
        Double confidence,
        Instant createdAt
) {
}
