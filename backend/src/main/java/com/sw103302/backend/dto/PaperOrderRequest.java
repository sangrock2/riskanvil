package com.sw103302.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record PaperOrderRequest(
    @NotBlank @Pattern(regexp = "US|KR") String market,
    @NotBlank String ticker,
    @NotBlank @Pattern(regexp = "BUY|SELL") String direction,
    @NotNull @DecimalMin("0.0001") BigDecimal quantity
) {}
