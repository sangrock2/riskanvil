package com.sw103302.backend.dto;

import java.util.List;

public record EarningsCalendarResponse(
        int daysAhead,
        String generatedAt,
        List<EarningsEvent> events
) {
    public record EarningsEvent(
            String ticker,
            String market,
            String earningsDate,
            String fiscalDateEnding,
            String time,
            Double epsEstimate,
            Double epsActual,
            Double revenueEstimate,
            Double revenueActual
    ) {}
}
