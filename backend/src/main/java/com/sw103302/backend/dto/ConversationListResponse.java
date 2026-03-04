package com.sw103302.backend.dto;

import java.time.LocalDateTime;

public record ConversationListResponse(
    Long id,
    String title,
    String model,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    int messageCount
) {}
