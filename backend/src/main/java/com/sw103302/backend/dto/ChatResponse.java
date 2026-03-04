package com.sw103302.backend.dto;

import java.time.LocalDateTime;

public record ChatResponse(
    Long conversationId,
    String message,
    String model,
    int tokensIn,
    int tokensOut,
    LocalDateTime timestamp
) {}
