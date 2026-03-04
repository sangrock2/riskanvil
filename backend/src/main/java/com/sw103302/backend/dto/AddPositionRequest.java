package com.sw103302.backend.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AddPositionRequest(
    @NotBlank(message = "Ticker is required")
    @Pattern(regexp = "^[A-Z0-9.\\-]{1,32}$")
    String ticker,

    @NotBlank(message = "Market is required")
    @Pattern(regexp = "US|KR")
    String market,

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.0001", message = "Quantity must be positive")
    BigDecimal quantity,

    @NotNull(message = "Entry price is required")
    @DecimalMin(value = "0.01", message = "Entry price must be positive")
    BigDecimal entryPrice,

    LocalDate entryDate,

    @Size(max = 500)
    String notes
) {}
