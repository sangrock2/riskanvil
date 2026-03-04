package com.sw103302.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MonteCarloResponse(
    String ticker,
    String model,
    List<SimulationPath> paths,
    ProbabilityDistribution distribution,
    Statistics stats,
    ConfidenceBands confidenceBands,
    Map<String, ScenarioResult> scenarios,
    JumpParams jumpParams
) {
    public record SimulationPath(
        int pathId,
        List<Double> prices
    ) {}

    public record ProbabilityDistribution(
        double percentile5,
        double percentile25,
        double median,
        double percentile75,
        double percentile95
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Statistics(
        double expectedReturn,
        double expectedReturnPercent,
        double volatility,
        double maxDrawdown,
        double valueAtRisk95,
        double conditionalVaR95,
        Double skewness,
        Double kurtosis,
        Double probabilityProfit
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConfidenceBands(
        List<Double> p5,
        List<Double> p25,
        List<Double> p50,
        List<Double> p75,
        List<Double> p95
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScenarioResult(
        double finalPrice,
        double returnPercent,
        double maxDrawdown,
        List<Double> path
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JumpParams(
        double lambda,
        double mu,
        double sigma
    ) {}
}
