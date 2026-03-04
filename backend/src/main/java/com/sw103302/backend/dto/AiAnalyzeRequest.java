package com.sw103302.backend.dto;

public record AiAnalyzeRequest(
        String ticker,
        String market,
        Integer horizonDays,
        String riskProfile
) { }
