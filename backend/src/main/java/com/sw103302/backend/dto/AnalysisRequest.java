package com.sw103302.backend.dto;

import jakarta.validation.constraints.*;

public record AnalysisRequest(
        @NotBlank(message = "Ticker symbol is required")
        @Size(min = 1, max = 10, message = "Ticker must be between 1 and 10 characters")
        @Pattern(regexp = "^[A-Z0-9]+$", message = "Ticker must contain only uppercase letters and numbers")
        String ticker,

        @NotBlank(message = "Market is required")
        @Pattern(regexp = "US|KR", message = "Market must be either US or KR")
        String market,

        @Min(value = 1, message = "Horizon days must be at least 1")
        @Max(value = 1000, message = "Horizon days cannot exceed 1000")
        Integer horizonDays,

        @Pattern(regexp = "conservative|moderate|aggressive", message = "Risk profile must be conservative, moderate, or aggressive")
        String riskProfile
) {
}
