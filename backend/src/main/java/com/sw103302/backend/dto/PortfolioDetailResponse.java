package com.sw103302.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record PortfolioDetailResponse(
    Long id,
    String name,
    String description,
    BigDecimal targetReturn,
    String riskProfile,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<PositionSummary> positions,
    PerformanceMetrics performance,
    AllocationBreakdown allocation
) {
    public record PositionSummary(
        Long id,
        String ticker,
        String market,
        BigDecimal quantity,
        BigDecimal entryPrice,
        LocalDate entryDate,
        BigDecimal currentPrice,
        BigDecimal currentValue,
        BigDecimal totalCost,
        BigDecimal unrealizedGain,
        BigDecimal unrealizedGainPercent,
        String notes
    ) {}

    public record PerformanceMetrics(
        BigDecimal totalValue,
        BigDecimal totalCost,
        BigDecimal totalReturn,
        BigDecimal totalReturnPercent,
        BigDecimal dayChange,
        BigDecimal dayChangePercent,
        BigDecimal weekChange,
        BigDecimal monthChange
    ) {}

    public record AllocationBreakdown(
        Map<String, BigDecimal> byTicker,
        Map<String, BigDecimal> bySector,
        Map<String, BigDecimal> byMarket
    ) {}
}
