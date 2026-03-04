package com.sw103302.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DividendResponse(
    Long id,
    Long positionId,
    String ticker,
    BigDecimal amount,
    String currency,
    LocalDate exDate,
    LocalDate paymentDate,
    LocalDate recordDate,
    LocalDate declaredDate,
    String frequency
) {}
