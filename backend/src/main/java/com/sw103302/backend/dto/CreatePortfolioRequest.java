package com.sw103302.backend.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreatePortfolioRequest(
    @NotBlank(message = "Portfolio name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    String name,

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    String description,

    @DecimalMin(value = "0.0", message = "Target return must be positive")
    @DecimalMax(value = "100.0", message = "Target return cannot exceed 100%")
    BigDecimal targetReturn,

    @Pattern(regexp = "conservative|moderate|aggressive", message = "Invalid risk profile")
    String riskProfile
) {}
