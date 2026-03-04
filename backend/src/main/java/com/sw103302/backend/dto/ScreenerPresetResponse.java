package com.sw103302.backend.dto;

import java.time.LocalDateTime;

public record ScreenerPresetResponse(
    Long id,
    String name,
    String description,
    ScreenerRequest.ScreenerFilters filters,
    boolean isPublic,
    LocalDateTime createdAt
) {}
