package com.sw103302.backend.dto;

import java.time.LocalDateTime;

public record ChatMessageResponse(
    Long id,
    String role,
    String content,
    String model,
    Integer tokensIn,
    Integer tokensOut,
    LocalDateTime createdAt
) {}
