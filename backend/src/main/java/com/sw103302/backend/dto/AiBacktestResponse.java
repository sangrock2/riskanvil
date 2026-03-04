package com.sw103302.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiBacktestResponse(
        Summary summary,
        List<EquityPoint> equityCurve
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
            String ticker,
            String market,
            String strategy,
            LocalDate start,
            LocalDate end,
            Double initialCapital,
            Double feeBps,
            Double totalReturn,
            Double cagr,
            Double maxDrawdown,
            Double sharpe,
            Integer numTrades,
            String note
    ){}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EquityPoint(
            LocalDate date,
            Double equity
    ) {}
}
