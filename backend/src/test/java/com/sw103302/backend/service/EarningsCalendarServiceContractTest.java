package com.sw103302.backend.service;

import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.EarningsCalendarResponse;
import com.sw103302.backend.entity.Portfolio;
import com.sw103302.backend.entity.PortfolioPosition;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.PortfolioPositionRepository;
import com.sw103302.backend.repository.PortfolioRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EarningsCalendarServiceContractTest {

    @Mock
    private AiClient aiClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private PortfolioPositionRepository positionRepository;

    private EarningsCalendarService service;
    private MockedStatic<SecurityUtil> securityUtilMock;

    private User user;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        service = new EarningsCalendarService(
                aiClient,
                new ObjectMapper(),
                userRepository,
                portfolioRepository,
                positionRepository
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
    void getPortfolioCalendar_shouldParseAndSortAiContractAcrossMarkets() {
        PortfolioPosition usAapl = new PortfolioPosition(
                portfolio, "AAPL", "US",
                new BigDecimal("2"), new BigDecimal("190"),
                LocalDate.of(2025, 1, 1), null
        );
        PortfolioPosition usAaplDup = new PortfolioPosition(
                portfolio, "AAPL", "US",
                new BigDecimal("1"), new BigDecimal("200"),
                LocalDate.of(2025, 1, 2), null
        );
        PortfolioPosition krSamsung = new PortfolioPosition(
                portfolio, "005930", "KR",
                new BigDecimal("10"), new BigDecimal("80000"),
                LocalDate.of(2025, 1, 3), null
        );

        when(positionRepository.findByPortfolio_IdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(usAapl, usAaplDup, krSamsung));

        when(aiClient.post(eq("/earnings/calendar"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = (Map<String, Object>) inv.getArgument(1);
            String market = (String) req.get("market");
            if ("US".equals(market)) {
                return """
                    {
                      "daysAhead": 7,
                      "generatedAt": "2026-02-26T10:00:00Z",
                      "events": [
                        {
                          "ticker": "AAPL",
                          "market": "US",
                          "earningsDate": "2026-03-10",
                          "fiscalDateEnding": "2025-12-31",
                          "time": "AMC",
                          "epsEstimate": 2.31,
                          "epsActual": null,
                          "revenueEstimate": 101200000000,
                          "revenueActual": null,
                          "extraField": "ignored"
                        },
                        {
                          "ticker": "AAPL",
                          "market": "US",
                          "earningsDate": "invalid-date",
                          "fiscalDateEnding": null,
                          "time": null,
                          "epsEstimate": "not-number"
                        }
                      ]
                    }
                    """;
            }
            return """
                {
                  "daysAhead": 7,
                  "generatedAt": "2026-02-26T10:00:00Z",
                  "events": [
                    {
                      "ticker": "005930",
                      "market": "KR",
                      "earningsDate": "2026-03-05",
                      "fiscalDateEnding": null,
                      "time": "BMO",
                      "epsEstimate": 1200.5,
                      "epsActual": null,
                      "revenueEstimate": null,
                      "revenueActual": null
                    }
                  ]
                }
                """;
        });

        EarningsCalendarResponse response = service.getPortfolioCalendar(10L, 3);

        assertThat(response.daysAhead()).isEqualTo(7);
        assertThat(response.events()).hasSize(3);
        assertThat(response.events().get(0).ticker()).isEqualTo("005930");
        assertThat(response.events().get(1).ticker()).isEqualTo("AAPL");
        assertThat(response.events().get(2).earningsDate()).isEqualTo("invalid-date");
        assertThat(response.events().get(2).epsEstimate()).isNull();

        ArgumentCaptor<Object> reqCaptor = ArgumentCaptor.forClass(Object.class);
        verify(aiClient, times(2)).post(eq("/earnings/calendar"), reqCaptor.capture());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> requests = reqCaptor.getAllValues().stream()
                .map(v -> (Map<String, Object>) v)
                .toList();

        assertThat(requests).allSatisfy(req -> assertThat(req.get("daysAhead")).isEqualTo(7));

        @SuppressWarnings("unchecked")
        Map<String, Object> usReq = requests.stream()
                .filter(req -> "US".equals(req.get("market")))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        List<String> usTickers = (List<String>) usReq.get("tickers");
        assertThat(usTickers).containsExactly("AAPL");
    }

    @Test
    void getPortfolioCalendar_shouldContinueWhenOneMarketFails() {
        PortfolioPosition usPos = new PortfolioPosition(
                portfolio, "AAPL", "US",
                new BigDecimal("1"), new BigDecimal("200"),
                LocalDate.of(2025, 1, 1), null
        );
        PortfolioPosition krPos = new PortfolioPosition(
                portfolio, "005930", "KR",
                new BigDecimal("5"), new BigDecimal("78000"),
                LocalDate.of(2025, 1, 1), null
        );

        when(positionRepository.findByPortfolio_IdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(usPos, krPos));

        when(aiClient.post(eq("/earnings/calendar"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = (Map<String, Object>) inv.getArgument(1);
            if ("US".equals(req.get("market"))) {
                throw new RuntimeException("upstream timeout");
            }
            return """
                {
                  "events": [
                    {
                      "ticker": "005930",
                      "market": "KR",
                      "earningsDate": "2026-03-05"
                    }
                  ]
                }
                """;
        });

        EarningsCalendarResponse response = service.getPortfolioCalendar(10L, 90);

        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).ticker()).isEqualTo("005930");
        assertThat(response.daysAhead()).isEqualTo(90);
    }
}
