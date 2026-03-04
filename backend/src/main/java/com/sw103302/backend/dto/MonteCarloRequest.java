package com.sw103302.backend.dto;

import jakarta.validation.constraints.*;

public record MonteCarloRequest(
    @NotBlank
    String ticker,

    @Pattern(regexp = "US|KR")
    String market,

    @Min(30)
    @Max(365)
    Integer days,

    @Min(100)
    @Max(10000)
    Integer simulations,

    @Min(1)
    @Max(365)
    Integer forecastDays,

    @Pattern(regexp = "gbm|jump_diffusion|historical_bootstrap")
    String model,

    Boolean scenarios,

    Boolean confidenceBands
) {}
