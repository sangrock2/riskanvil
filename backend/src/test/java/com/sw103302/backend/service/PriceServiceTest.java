package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    @Mock
    private AiClient aiClient;

    private ObjectMapper objectMapper;
    private PriceService priceService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        priceService = new PriceService(aiClient, objectMapper);
    }

    @Test
    void fetchPricesBatch_withValidTickers_shouldReturnPrices() throws Exception {
        // Given
        List<String> tickers = Arrays.asList("AAPL", "MSFT");
        String market = "US";

        String mockResponse = """
            {
                "prices": {
                    "AAPL": {
                        "price": 175.50,
                        "currency": "USD"
                    },
                    "MSFT": {
                        "price": 380.25,
                        "currency": "USD"
                    }
                }
            }
            """;

        when(aiClient.post(eq("/prices/batch"), any())).thenReturn(mockResponse);

        // When
        Map<String, BigDecimal> result = priceService.fetchPricesBatch(tickers, market);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get("AAPL")).isEqualByComparingTo(new BigDecimal("175.50"));
        assertThat(result.get("MSFT")).isEqualByComparingTo(new BigDecimal("380.25"));
    }

    @Test
    void fetchPricesBatch_withEmptyResponse_shouldReturnEmptyMap() {
        // Given
        List<String> tickers = Arrays.asList("INVALID");
        String market = "US";

        when(aiClient.post(eq("/prices/batch"), any())).thenReturn("{}");

        // When
        Map<String, BigDecimal> result = priceService.fetchPricesBatch(tickers, market);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void fetchPricesBatch_withNullPriceData_shouldSkipTicker() throws Exception {
        // Given
        List<String> tickers = Arrays.asList("AAPL", "INVALID");
        String market = "US";

        String mockResponse = """
            {
                "prices": {
                    "AAPL": {
                        "price": 175.50
                    },
                    "INVALID": null
                }
            }
            """;

        when(aiClient.post(eq("/prices/batch"), any())).thenReturn(mockResponse);

        // When
        Map<String, BigDecimal> result = priceService.fetchPricesBatch(tickers, market);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get("AAPL")).isEqualByComparingTo(new BigDecimal("175.50"));
        assertThat(result.get("INVALID")).isNull();
    }

    @Test
    void fetchPricesBatch_withException_shouldReturnEmptyMap() {
        // Given
        List<String> tickers = Arrays.asList("AAPL");
        String market = "US";

        when(aiClient.post(eq("/prices/batch"), any())).thenThrow(new RuntimeException("AI service error"));

        // When
        Map<String, BigDecimal> result = priceService.fetchPricesBatch(tickers, market);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void fetchPrice_withValidTicker_shouldReturnPrice() throws Exception {
        // Given
        String ticker = "AAPL";
        String market = "US";

        String mockResponse = """
            {
                "prices": {
                    "AAPL": {
                        "price": 175.50
                    }
                }
            }
            """;

        when(aiClient.post(eq("/prices/batch"), any())).thenReturn(mockResponse);

        // When
        BigDecimal result = priceService.fetchPrice(ticker, market);

        // Then
        assertThat(result).isEqualByComparingTo(new BigDecimal("175.50"));
    }

    @Test
    void fetchPrice_withInvalidTicker_shouldReturnNull() {
        // Given
        String ticker = "INVALID";
        String market = "US";

        when(aiClient.post(eq("/prices/batch"), any())).thenReturn("{}");

        // When
        BigDecimal result = priceService.fetchPrice(ticker, market);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void fetchPricesWithFallback_shouldUseFallbackForMissingPrices() throws Exception {
        // Given
        List<String> tickers = Arrays.asList("AAPL", "MSFT", "GOOGL");
        String market = "US";
        Map<String, BigDecimal> fallbackPrices = Map.of(
            "MSFT", new BigDecimal("380.00"),
            "GOOGL", new BigDecimal("140.00")
        );

        String mockResponse = """
            {
                "prices": {
                    "AAPL": {
                        "price": 175.50
                    }
                }
            }
            """;

        when(aiClient.post(eq("/prices/batch"), any())).thenReturn(mockResponse);

        // When
        Map<String, BigDecimal> result = priceService.fetchPricesWithFallback(tickers, market, fallbackPrices);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get("AAPL")).isEqualByComparingTo(new BigDecimal("175.50"));
        assertThat(result.get("MSFT")).isEqualByComparingTo(new BigDecimal("380.00")); // Fallback
        assertThat(result.get("GOOGL")).isEqualByComparingTo(new BigDecimal("140.00")); // Fallback
    }
}
