package com.sw103302.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 포트폴리오 리밸런싱 요청
 * targetWeights: 목표 비중 (티커 → 0.0~1.0, 합계 = 1.0)
 */
public record RebalanceRequest(
    @NotNull(message = "Target weights are required")
    Map<String, Double> targetWeights
) {}
