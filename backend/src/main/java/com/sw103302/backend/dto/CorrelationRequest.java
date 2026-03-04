package com.sw103302.backend.dto;

import jakarta.validation.constraints.*;

import java.util.List;

public record CorrelationRequest(
    @NotNull
    @Size(min = 2, max = 10, message = "Need 2-10 tickers")
    List<String> tickers,

    @Pattern(regexp = "US|KR")
    String market,

    @Min(30)
    @Max(365)
    Integer days
) {}
