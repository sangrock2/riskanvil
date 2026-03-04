package com.sw103302.backend.dto;

import java.math.BigDecimal;

public record PaperPositionResponse(
    Long id,
    String ticker,
    BigDecimal quantity,
    BigDecimal avgPrice,
    BigDecimal totalCost,
    BigDecimal currentPrice,
    BigDecimal currentValue,
    BigDecimal unrealizedGain,
    BigDecimal unrealizedGainPct
) {}
