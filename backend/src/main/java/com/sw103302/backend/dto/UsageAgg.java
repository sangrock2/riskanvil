package com.sw103302.backend.dto;

public record UsageAgg(
        String endpoint,
        long total,
        long cached,
        long refresh,
        long web,
        long alphaCalls,
        long openaiCalls
) {
}
