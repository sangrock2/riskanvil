package com.sw103302.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WatchlistAddRequest(
        @NotBlank(message = "Ticker symbol is required")
        @Size(min = 1, max = 10, message = "Ticker must be between 1 and 10 characters")
        @Pattern(regexp = "^[A-Z0-9]+$", message = "Ticker must contain only uppercase letters and numbers")
        String ticker,

        @NotBlank(message = "Market is required")
        @Pattern(regexp = "US|KR", message = "Market must be either US or KR")
        String market
) {
}
