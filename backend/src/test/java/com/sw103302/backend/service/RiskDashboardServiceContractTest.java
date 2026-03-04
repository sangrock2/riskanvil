package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.RiskDashboardResponse;
import com.sw103302.backend.entity.Portfolio;
import com.sw103302.backend.entity.PortfolioPosition;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.PortfolioPositionRepository;
import com.sw103302.backend.repository.PortfolioRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskDashboardServiceContractTest {

    @Mock
    private AiClient aiClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private PortfolioPositionRepository positionRepository;
    @Mock
    private PriceService priceService;

    private RiskDashboardService service;
    private MockedStatic<SecurityUtil> securityUtilMock;

    private User user;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        service = new RiskDashboardService(
                aiClient,
                new ObjectMapper(),
                userRepository,
                portfolioRepository,
                positionRepository,
                priceService
        );

        user = new User("contract-user@example.com", "hash", "ROLE_USER");
        ReflectionTestUtils.setField(user, "id", 1L);

        portfolio = new Portfolio(user, "Core", "Contract Test Portfolio", null, "balanced");
        ReflectionTestUtils.setField(portfolio, "id", 10L);

        securityUtilMock = org.mockito.Mockito.mockStatic(SecurityUtil.class);
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(user.getEmail());

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(portfolioRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(portfolio));
    }

    @AfterEach
    void tearDown() {
        securityUtilMock.close();
    }

    @Test
    void getPortfolioRisk_shouldParseAiContractAndSendNormalizedRequest() {
        PortfolioPosition aapl = new PortfolioPosition(
                portfolio, "AAPL", "US",
                new BigDecimal("2"), new BigDecimal("190"),
                LocalDate.of(2025, 1, 1), null
        );
        PortfolioPosition msft = new PortfolioPosition(
                portfolio, "MSFT", "US",
                new BigDecimal("1"), new BigDecimal("350"),
                LocalDate.of(2025, 1, 1), null
        );

        when(positionRepository.findByPortfolio_IdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(aapl, msft));

        when(priceService.fetchPricesBatch(anyList(), eq("US"))).thenReturn(Map.of(
                "AAPL", new BigDecimal("210"),
                "MSFT", new BigDecimal("380")
        ));

        when(aiClient.post(eq("/portfolio/risk"), any())).thenReturn("""
            {
              "generatedAt": "2026-02-26T12:00:00Z",
              "riskLevel": "MEDIUM",
              "annualizedVolatilityPct": 18.4,
              "maxDrawdownPct": 12.1,
              "valueAtRisk95Pct": 2.9,
              "expectedShortfall95Pct": 3.5,
              "sharpeRatio": 1.08,
              "betaToMarket": 0.97,
              "diversificationScore": 57.2,
              "concentrationScore": 52.5,
              "holdings": [
                { "ticker": "AAPL", "market": "US", "weightPct": 52.5, "value": 420.0 },
                { "ticker": "MSFT", "market": "US", "weightPct": 47.5, "value": 380.0 }
              ],
              "timeSeries": [
                { "date": "2026-01-01", "portfolioIndex": 100.0, "drawdownPct": 0.0, "rollingVolatilityPct": 15.1 },
                { "date": "2026-01-02", "portfolioIndex": 101.3, "drawdownPct": -0.2, "rollingVolatilityPct": 15.4 }
              ]
            }
            """);

        RiskDashboardResponse response = service.getPortfolioRisk(10L, 10);

        assertThat(response.riskLevel()).isEqualTo("MEDIUM");
        assertThat(response.annualizedVolatilityPct()).isEqualTo(18.4);
        assertThat(response.valueAtRisk95Pct()).isEqualTo(2.9);
        assertThat(response.holdings()).hasSize(2);
        assertThat(response.timeSeries()).hasSize(2);

        ArgumentCaptor<Object> reqCaptor = ArgumentCaptor.forClass(Object.class);
        verify(aiClient, times(1)).post(eq("/portfolio/risk"), reqCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) reqCaptor.getValue();
        assertThat(request.get("lookbackDays")).isEqualTo(60);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> holdings = (List<Map<String, Object>>) request.get("holdings");
        assertThat(holdings).hasSize(2);

        double totalWeight = holdings.stream()
                .mapToDouble(h -> ((Number) h.get("weightPct")).doubleValue())
                .sum();
        assertThat(totalWeight).isCloseTo(100.0, within(0.001));
    }

    @Test
    void getPortfolioRisk_shouldReturnDefaultsWhenNoPositions() {
        when(positionRepository.findByPortfolio_IdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of());

        RiskDashboardResponse response = service.getPortfolioRisk(10L, 252);

        assertThat(response.riskLevel()).isEqualTo("LOW");
        assertThat(response.holdings()).isEmpty();
        assertThat(response.timeSeries()).isEmpty();
        verifyNoInteractions(aiClient);
        verifyNoInteractions(priceService);
    }

    @Test
    void getPortfolioRisk_shouldWrapInvalidAiPayload() {
        PortfolioPosition aapl = new PortfolioPosition(
                portfolio, "AAPL", "US",
                new BigDecimal("1"), new BigDecimal("190"),
                LocalDate.of(2025, 1, 1), null
        );

        when(positionRepository.findByPortfolio_IdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(aapl));

        when(priceService.fetchPricesBatch(anyList(), eq("US"))).thenReturn(Map.of(
                "AAPL", new BigDecimal("210")
        ));

        when(aiClient.post(eq("/portfolio/risk"), any())).thenReturn("not-json");

        assertThatThrownBy(() -> service.getPortfolioRisk(10L, 252))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to generate risk dashboard");
    }
}
