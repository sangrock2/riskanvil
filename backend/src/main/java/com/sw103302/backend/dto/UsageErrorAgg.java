package com.sw103302.backend.dto;

public record UsageErrorAgg(
        String errorText,
        long count
) {
}
