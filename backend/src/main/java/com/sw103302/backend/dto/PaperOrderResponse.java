package com.sw103302.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaperOrderResponse(
    Long id,
    String ticker,
    String direction,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal amount,
    BigDecimal commission,
    BigDecimal balanceAfter,
    LocalDateTime createdAt
) {}
