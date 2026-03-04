package com.sw103302.backend.dto;

import jakarta.validation.constraints.*;

public record BacktestRequest(
        @NotBlank(message = "Ticker symbol is required")
        @Size(min = 1, max = 10, message = "Ticker must be between 1 and 10 characters")
        @Pattern(regexp = "^[A-Z0-9]+$", message = "Ticker must contain only uppercase letters and numbers")
        String ticker,

        @NotBlank(message = "Market is required")
        @Pattern(regexp = "US|KR", message = "Market must be either US or KR")
        String market,

        @NotBlank(message = "Strategy is required")
        @Pattern(regexp = "SMA_CROSS|RSI_STRATEGY|MACD_STRATEGY", message = "Invalid strategy type")
        String strategy,

        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Start date must be in YYYY-MM-DD format")
        String start,

        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "End date must be in YYYY-MM-DD format")
        String end,

        @Positive(message = "Initial capital must be positive")
        @Max(value = 1_000_000_000, message = "Initial capital cannot exceed 1 billion")
        Double initialCapital,

        @PositiveOrZero(message = "Fee cannot be negative")
        @Max(value = 1000, message = "Fee BPS cannot exceed 1000 (10%)")
        Double feeBps
) {
}
