package com.sw103302.backend.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdatePositionRequest(
    @DecimalMin("0.0001")
    BigDecimal quantity,

    @DecimalMin("0.01")
    BigDecimal entryPrice,

    LocalDate entryDate,

    @Size(max = 500)
    String notes
) {}
