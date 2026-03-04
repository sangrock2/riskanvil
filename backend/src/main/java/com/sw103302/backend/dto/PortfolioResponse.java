package com.sw103302.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PortfolioResponse(
    Long id,
    String name,
    String description,
    BigDecimal targetReturn,
    String riskProfile,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    int positionCount,
    BigDecimal totalValue,
    BigDecimal totalCost,
    BigDecimal totalReturn,
    BigDecimal totalReturnPercent
) {}
