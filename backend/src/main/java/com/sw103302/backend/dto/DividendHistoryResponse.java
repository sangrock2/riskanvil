package com.sw103302.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DividendHistoryResponse(
        String ticker,
        String market,
        List<DividendEvent> dividends,
        BigDecimal totalDividends
) {
    public record DividendEvent(
            Long id,
            LocalDate exDate,
            LocalDate paymentDate,
            BigDecimal amount,
            String currency,
            String frequency
    ) {}
}
