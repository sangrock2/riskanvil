package com.sw103302.backend.dto;

import java.math.BigDecimal;

public record ScreenerRequest(
    String market,
    ScreenerFilters filters,
    String sortBy,
    String sortOrder
) {
    public record ScreenerFilters(
        BigDecimal peMin,
        BigDecimal peMax,
        BigDecimal psMin,
        BigDecimal psMax,
        BigDecimal pbMin,
        BigDecimal pbMax,
        BigDecimal roeMin,
        BigDecimal roeMax,
        BigDecimal revenueGrowthMin,
        BigDecimal dividendYieldMin,
        Long marketCapMin,
        Long marketCapMax,
        String sector,
        Integer rsiMin,
        Integer rsiMax
    ) {}
}
