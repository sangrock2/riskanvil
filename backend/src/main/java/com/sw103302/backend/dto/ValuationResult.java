package com.sw103302.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ValuationResult(
        String ticker,
        String market,
        boolean testMode,
        LocalDateTime computedAt,

        Double pe,
        Double ps,
        Double pb,
        Double revYoY,

        int score,
        String label,
        List<String> rationales,

        CacheMeta _cache
) {
    public record CacheMeta(
            boolean cached,
            String insightsUpdatedAt
    ) {}
}
