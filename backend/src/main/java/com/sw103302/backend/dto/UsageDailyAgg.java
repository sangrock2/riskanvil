package com.sw103302.backend.dto;

public record UsageDailyAgg(
        String date,
        long total,
        long cached,
        long refresh,
        long web,
        long alphaCalls,
        long openaiCalls,
        long openaiTokensIn,
        long openaiTokensOut
) {
}
