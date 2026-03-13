package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.dto.*;
import com.sw103302.backend.entity.Portfolio;
import com.sw103302.backend.entity.PortfolioPosition;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.DividendRepository;
import com.sw103302.backend.repository.PortfolioPositionRepository;
import com.sw103302.backend.repository.PortfolioRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioPositionRepository positionRepository;

    @Mock
    private DividendRepository dividendRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PriceService priceService;

    private PortfolioService portfolioService;
    private MockedStatic<SecurityUtil> securityUtilMock;
    private User testUser;

    @BeforeEach
    void setUp() {
        portfolioService = new PortfolioService(
            portfolioRepository,
            positionRepository,
            dividendRepository,
            userRepository,
            priceService,
            new ObjectMapper()
        );

        securityUtilMock = mockStatic(SecurityUtil.class);
        testUser = new User("user@example.com", "hash", "ROLE_USER");
        ReflectionTestUtils.setField(testUser, "id", 1L);
    }

    @AfterEach
    void tearDown() {
        securityUtilMock.close();
    }

    @Test
    void create_withValidRequest_shouldCreatePortfolio() {
        // Given
        CreatePortfolioRequest request = new CreatePortfolioRequest(
            "My Portfolio",
            "Test portfolio",
            new BigDecimal("10.0"),
            "moderate"
        );

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(portfolioRepository.findByUser_IdAndName(testUser.getId(), request.name()))
            .thenReturn(Optional.empty());

        Portfolio savedPortfolio = new Portfolio(testUser, request.name(), request.description(),
            request.targetReturn(), request.riskProfile());
        ReflectionTestUtils.setField(savedPortfolio, "id", 1L);
        ReflectionTestUtils.setField(savedPortfolio, "createdAt", LocalDateTime.now());

        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(savedPortfolio);
        when(positionRepository.findByPortfolio_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        // When
        PortfolioResponse response = portfolioService.create(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("My Portfolio");
        assertThat(response.targetReturn()).isEqualByComparingTo(new BigDecimal("10.0"));
        assertThat(response.riskProfile()).isEqualTo("moderate");

        verify(portfolioRepository).save(any(Portfolio.class));
    }

    @Test
    void create_withDuplicateName_shouldThrowException() {
        // Given
        CreatePortfolioRequest request = new CreatePortfolioRequest(
            "My Portfolio",
            "Test portfolio",
            new BigDecimal("10.0"),
            "moderate"
        );

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));

        Portfolio existingPortfolio = new Portfolio(testUser, request.name(), null, null, null);
        when(portfolioRepository.findByUser_IdAndName(testUser.getId(), request.name()))
            .thenReturn(Optional.of(existingPortfolio));

        // When/Then
        assertThatThrownBy(() -> portfolioService.create(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");

        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void addPosition_withValidRequest_shouldAddPosition() {
        // Given
        Long portfolioId = 1L;
        AddPositionRequest request = new AddPositionRequest(
            "AAPL",
            "US",
            new BigDecimal("10"),
            new BigDecimal("150.00"),
            LocalDate.now(),
            "Test position"
        );

        Portfolio portfolio = new Portfolio(testUser, "My Portfolio", null, null, null);
        ReflectionTestUtils.setField(portfolio, "id", portfolioId);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(portfolioRepository.findByIdAndUser_Id(portfolioId, testUser.getId()))
            .thenReturn(Optional.of(portfolio));
        when(positionRepository.findByPortfolio_IdAndTickerAndMarket(portfolioId, "AAPL", "US"))
            .thenReturn(Optional.empty());
        when(positionRepository.save(any(PortfolioPosition.class))).thenAnswer(invocation -> {
            PortfolioPosition saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        // When
        Long positionId = portfolioService.addPosition(portfolioId, request);

        // Then
        assertThat(positionId).isEqualTo(10L);
        verify(positionRepository).save(any(PortfolioPosition.class));
    }

    @Test
    void addPosition_withDuplicateTicker_shouldThrowException() {
        // Given
        Long portfolioId = 1L;
        AddPositionRequest request = new AddPositionRequest(
            "AAPL",
            "US",
            new BigDecimal("10"),
            new BigDecimal("150.00"),
            null,
            null
        );

        Portfolio portfolio = new Portfolio(testUser, "My Portfolio", null, null, null);
        ReflectionTestUtils.setField(portfolio, "id", portfolioId);

        PortfolioPosition existingPosition = new PortfolioPosition(
            portfolio, "AAPL", "US", BigDecimal.TEN, new BigDecimal("150"), null, null
        );

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(portfolioRepository.findByIdAndUser_Id(portfolioId, testUser.getId()))
            .thenReturn(Optional.of(portfolio));
        when(positionRepository.findByPortfolio_IdAndTickerAndMarket(portfolioId, "AAPL", "US"))
            .thenReturn(Optional.of(existingPosition));

        // When/Then
        assertThatThrownBy(() -> portfolioService.addPosition(portfolioId, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");

        verify(positionRepository, never()).save(any());
    }

    @Test
    void detail_withRealPrices_shouldCalculateCorrectMetrics() {
        // Given
        Long portfolioId = 1L;

        Portfolio portfolio = new Portfolio(testUser, "My Portfolio", "Desc",
            new BigDecimal("10"), "moderate");
        ReflectionTestUtils.setField(portfolio, "id", portfolioId);
        ReflectionTestUtils.setField(portfolio, "createdAt", LocalDateTime.now());

        PortfolioPosition position1 = new PortfolioPosition(
            portfolio, "AAPL", "US", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.now(), null
        );
        ReflectionTestUtils.setField(position1, "id", 1L);

        PortfolioPosition position2 = new PortfolioPosition(
            portfolio, "MSFT", "US", new BigDecimal("5"), new BigDecimal("300.00"), LocalDate.now(), null
        );
        ReflectionTestUtils.setField(position2, "id", 2L);

        List<PortfolioPosition> positions = Arrays.asList(position1, position2);

        Map<String, BigDecimal> currentPrices = Map.of(
            "AAPL", new BigDecimal("175.00"),  // +25 from entry
            "MSFT", new BigDecimal("350.00")   // +50 from entry
        );

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(portfolioRepository.findByIdAndUser_Id(portfolioId, testUser.getId()))
            .thenReturn(Optional.of(portfolio));
        when(positionRepository.findByPortfolio_IdOrderByCreatedAtDesc(portfolioId))
            .thenReturn(positions);
        when(priceService.fetchPricesBatch(any(), eq("US"))).thenReturn(currentPrices);

        // When
        PortfolioDetailResponse response = portfolioService.detail(portfolioId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.positions()).hasSize(2);

        // Total cost: (10 * 150) + (5 * 300) = 1500 + 1500 = 3000
        // Total value: (10 * 175) + (5 * 350) = 1750 + 1750 = 3500
        // Total return: 3500 - 3000 = 500
        // Return %: (500 / 3000) * 100 = 16.67%

        assertThat(response.performance().totalCost()).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(response.performance().totalValue()).isEqualByComparingTo(new BigDecimal("3500"));
        assertThat(response.performance().totalReturn()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void delete_withValidPortfolio_shouldDelete() {
        // Given
        Long portfolioId = 1L;

        Portfolio portfolio = new Portfolio(testUser, "My Portfolio", null, null, null);
        ReflectionTestUtils.setField(portfolio, "id", portfolioId);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(portfolioRepository.findByIdAndUser_Id(portfolioId, testUser.getId()))
            .thenReturn(Optional.of(portfolio));

        // When
        portfolioService.delete(portfolioId);

        // Then
        verify(dividendRepository).deleteByPortfolioPosition_Portfolio_Id(portfolioId);
        verify(portfolioRepository).delete(portfolio);
    }

    @Test
    void deletePosition_withValidPosition_shouldDelete() {
        // Given
        Long portfolioId = 1L;
        Long positionId = 10L;

        Portfolio portfolio = new Portfolio(testUser, "My Portfolio", null, null, null);
        ReflectionTestUtils.setField(portfolio, "id", portfolioId);

        PortfolioPosition position = new PortfolioPosition(
            portfolio, "AAPL", "US", BigDecimal.ONE, new BigDecimal("150.00"), LocalDate.now(), null
        );
        ReflectionTestUtils.setField(position, "id", positionId);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(positionRepository.findByIdAndPortfolio_User_Id(positionId, testUser.getId()))
            .thenReturn(Optional.of(position));

        // When
        portfolioService.deletePosition(portfolioId, positionId);

        // Then
        verify(dividendRepository).deleteByPortfolioPosition_Id(positionId);
        verify(positionRepository).delete(position);
    }

    @Test
    void detail_withSameTickerDifferentMarkets_shouldKeepPricesSeparatedByMarket() {
        // Given
        Long portfolioId = 1L;
        Portfolio portfolio = new Portfolio(testUser, "Mixed Market", "Desc",
            new BigDecimal("10"), "moderate");
        ReflectionTestUtils.setField(portfolio, "id", portfolioId);

        PortfolioPosition usPosition = new PortfolioPosition(
            portfolio, "AAPL", "US", BigDecimal.ONE, new BigDecimal("100.00"), LocalDate.now(), null
        );
        ReflectionTestUtils.setField(usPosition, "id", 11L);

        PortfolioPosition krPosition = new PortfolioPosition(
            portfolio, "AAPL", "KR", BigDecimal.ONE, new BigDecimal("200.00"), LocalDate.now(), null
        );
        ReflectionTestUtils.setField(krPosition, "id", 12L);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(portfolioRepository.findByIdAndUser_Id(portfolioId, testUser.getId()))
            .thenReturn(Optional.of(portfolio));
        when(positionRepository.findByPortfolio_IdOrderByCreatedAtDesc(portfolioId))
            .thenReturn(List.of(usPosition, krPosition));
        when(priceService.fetchPricesBatch(anyList(), eq("US")))
            .thenReturn(Map.of("AAPL", new BigDecimal("150.00")));
        when(priceService.fetchPricesBatch(anyList(), eq("KR")))
            .thenReturn(Map.of("AAPL", new BigDecimal("250.00")));

        // When
        PortfolioDetailResponse response = portfolioService.detail(portfolioId);

        // Then
        assertThat(response.positions()).hasSize(2);
        Map<String, BigDecimal> priceByMarket = response.positions().stream()
            .collect(java.util.stream.Collectors.toMap(
                PortfolioDetailResponse.PositionSummary::market,
                PortfolioDetailResponse.PositionSummary::currentPrice
            ));

        assertThat(priceByMarket.get("US")).isEqualByComparingTo("150.00");
        assertThat(priceByMarket.get("KR")).isEqualByComparingTo("250.00");
        assertThat(response.performance().totalValue()).isEqualByComparingTo("400.00");
    }
}
