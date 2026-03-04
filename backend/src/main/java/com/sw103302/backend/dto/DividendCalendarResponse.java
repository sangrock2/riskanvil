package com.sw103302.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DividendCalendarResponse(
        List<CalendarEvent> upcoming,
        List<CalendarEvent> past,
        BigDecimal totalUpcoming,
        BigDecimal totalPast
) {
    public record CalendarEvent(
            String ticker,
            LocalDate exDate,
            LocalDate paymentDate,
            BigDecimal dividendPerShare,
            BigDecimal quantity,
            BigDecimal totalAmount,
            String currency
    ) {}
}
