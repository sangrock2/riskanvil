package com.sw103302.backend.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdatePortfolioRequest(
    @Size(max = 100)
    String name,

    @Size(max = 500)
    String description,

    @DecimalMin("0.0")
    @DecimalMax("100.0")
    BigDecimal targetReturn,

    @Pattern(regexp = "conservative|moderate|aggressive")
    String riskProfile
) {}
