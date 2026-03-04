package com.sw103302.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 포트폴리오 리밸런싱 결과
 * trades: 권장 매수/매도 거래 목록
 */
public record RebalanceResponse(
    BigDecimal totalValue,        // 포트폴리오 현재 총 평가금액
    List<RebalanceTrade> trades   // 권장 거래 목록
) {
    public record RebalanceTrade(
        String ticker,
        String action,            // "BUY" or "SELL"
        double currentWeight,     // 현재 비중 (0.0~1.0)
        double targetWeight,      // 목표 비중 (0.0~1.0)
        double diffWeight,        // 차이 (targetWeight - currentWeight)
        BigDecimal estimatedAmount // 매수/매도 예상 금액
    ) {}
}
