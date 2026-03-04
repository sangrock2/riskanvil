package com.sw103302.backend.dto;

import java.util.List;

public record RiskDashboardResponse(
        String generatedAt,
        String riskLevel,
        Double annualizedVolatilityPct,
        Double maxDrawdownPct,
        Double valueAtRisk95Pct,
        Double expectedShortfall95Pct,
        Double sharpeRatio,
        Double betaToMarket,
        Double diversificationScore,
        Double concentrationScore,
        List<HoldingRisk> holdings,
        List<RiskPoint> timeSeries
) {
    public record HoldingRisk(
            String ticker,
            String market,
            Double weightPct,
            Double value
    ) {}

    public record RiskPoint(
            String date,
            Double portfolioIndex,
            Double drawdownPct,
            Double rollingVolatilityPct
    ) {}
}
