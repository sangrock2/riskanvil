package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.RiskDashboardResponse;
import com.sw103302.backend.entity.PortfolioPosition;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.PortfolioPositionRepository;
import com.sw103302.backend.repository.PortfolioRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RiskDashboardService {
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final PriceService priceService;

    public RiskDashboardService(AiClient aiClient,
                                ObjectMapper objectMapper,
                                UserRepository userRepository,
                                PortfolioRepository portfolioRepository,
                                PortfolioPositionRepository positionRepository,
                                PriceService priceService) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.priceService = priceService;
    }

    @Transactional(readOnly = true)
    public RiskDashboardResponse getPortfolioRisk(Long portfolioId, int lookbackDays) {
        int normalizedLookback = Math.max(60, Math.min(lookbackDays, 365));

        String email = SecurityUtil.currentEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        portfolioRepository.findByIdAndUser_Id(portfolioId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        List<PortfolioPosition> positions = positionRepository.findByPortfolio_IdOrderByCreatedAtDesc(portfolioId);
        if (positions.isEmpty()) {
            return new RiskDashboardResponse(
                    null,
                    "LOW",
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    List.of(),
                    List.of()
            );
        }

        Map<String, List<PortfolioPosition>> byMarket = positions.stream()
                .collect(Collectors.groupingBy(PortfolioPosition::getMarket));

        Map<String, BigDecimal> currentPrices = new java.util.HashMap<>();
        for (Map.Entry<String, List<PortfolioPosition>> entry : byMarket.entrySet()) {
            String market = entry.getKey();
            List<String> tickers = entry.getValue().stream()
                    .map(PortfolioPosition::getTicker)
                    .distinct()
                    .toList();
            currentPrices.putAll(priceService.fetchPricesBatch(tickers, market));
        }

        BigDecimal totalValue = BigDecimal.ZERO;
        List<Map<String, Object>> holdings = new ArrayList<>();

        for (PortfolioPosition pos : positions) {
            BigDecimal price = currentPrices.getOrDefault(pos.getTicker(), pos.getEntryPrice());
            BigDecimal value = price.multiply(pos.getQuantity());
            totalValue = totalValue.add(value);

            holdings.add(Map.of(
                    "ticker", pos.getTicker(),
                    "market", pos.getMarket(),
                    "quantity", pos.getQuantity(),
                    "price", price,
                    "value", value
            ));
        }

        if (totalValue.compareTo(BigDecimal.ZERO) <= 0) {
            return new RiskDashboardResponse(
                    null,
                    "LOW",
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    List.of(),
                    List.of()
            );
        }

        List<Map<String, Object>> weightedHoldings = new ArrayList<>();
        for (Map<String, Object> h : holdings) {
            BigDecimal value = (BigDecimal) h.get("value");
            BigDecimal weightPct = value.divide(totalValue, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            weightedHoldings.add(Map.of(
                    "ticker", h.get("ticker"),
                    "market", h.get("market"),
                    "value", value,
                    "weightPct", weightPct
            ));
        }

        try {
            Map<String, Object> req = Map.of(
                    "holdings", weightedHoldings,
                    "lookbackDays", normalizedLookback
            );

            String json = aiClient.post("/portfolio/risk", req);
            return objectMapper.readValue(json, RiskDashboardResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate risk dashboard: " + e.getMessage(), e);
        }
    }
}
