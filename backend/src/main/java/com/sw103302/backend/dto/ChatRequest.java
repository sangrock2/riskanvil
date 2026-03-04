package com.sw103302.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
    Long conversationId,

    @NotBlank
    String message,

    String model
) {}
