package com.sw103302.backend.dto;

import java.time.LocalDateTime;

public record TagResponse(
        Long id,
        String name,
        String color,
        LocalDateTime createdAt,
        Integer itemCount
) {}
