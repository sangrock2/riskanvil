package com.sw103302.backend.dto;

import java.math.BigDecimal;

public record ScreenerResult(
    String ticker,
    String name,
    String sector,
    BigDecimal price,
    BigDecimal pe,
    BigDecimal ps,
    BigDecimal pb,
    BigDecimal roe,
    BigDecimal revenueGrowth,
    BigDecimal dividendYield,
    Long marketCap,
    Integer overallScore
) {}
