package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for fetching real-time stock prices
 * Implements caching to reduce API calls
 */
@Slf4j
@Service
public class PriceService {
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public PriceService(AiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Get AiClient for direct API calls
     * Used by other services that need custom AI endpoints
     */
    public AiClient getAiClient() {
        return aiClient;
    }

    /**
     * Fetch prices for multiple tickers in batch
     * Results are cached for 1 minute to reduce API calls
     */
    @Cacheable(value = "prices", key = "#root.target.pricesCacheKey(#tickers, #market)")
    public Map<String, BigDecimal> fetchPricesBatch(List<String> tickers, String market) {
        List<String> normalizedTickers = normalizeTickersForRequest(tickers);
        if (normalizedTickers.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("tickers", normalizedTickers);
            request.put("market", market);

            // Call AI service and get JSON string response
            String jsonResponse = aiClient.post("/prices/batch", request);

            if (jsonResponse == null || jsonResponse.isBlank()) {
                return Collections.emptyMap();
            }

            // Parse JSON response
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode pricesNode = root.get("prices");

            if (pricesNode == null || !pricesNode.isObject()) {
                return Collections.emptyMap();
            }

            Map<String, BigDecimal> result = new HashMap<>();
            pricesNode.fields().forEachRemaining(entry -> {
                JsonNode priceData = entry.getValue();
                if (priceData != null && !priceData.isNull() && priceData.has("price")) {
                    JsonNode priceNode = priceData.get("price");
                    if (priceNode.isNumber()) {
                        result.put(entry.getKey(), BigDecimal.valueOf(priceNode.asDouble()));
                    }
                }
            });

            return result;
        } catch (Exception e) {
            // Log error and return empty map
            log.error("Failed to fetch prices: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Fetch price for a single ticker
     * Cached for 1 minute
     */
    @Cacheable(value = "price", key = "#ticker + '-' + #market")
    public BigDecimal fetchPrice(String ticker, String market) {
        String normalizedTicker = normalizeTicker(ticker);
        return fetchPricesBatch(Collections.singletonList(normalizedTicker), market).get(normalizedTicker);
    }

    public Map<String, BigDecimal> fetchPricesWithFallback(List<String> tickers, String market, Map<String, BigDecimal> fallbackPrices) {
        Map<String, BigDecimal> result = new HashMap<>(fetchPricesBatch(tickers, market));
        for (String ticker : tickers) {
            if (!result.containsKey(ticker) && fallbackPrices.containsKey(ticker)) {
                result.put(ticker, fallbackPrices.get(ticker));
            }
        }
        return result;
    }

    /**
     * Cache key를 티커 순서/중복에 독립적으로 생성한다.
     * 예: [AAPL, MSFT]와 [MSFT, AAPL, AAPL]은 동일 키로 매핑한다.
     */
    public String pricesCacheKey(List<String> tickers, String market) {
        List<String> canonical = normalizeTickersForRequest(tickers).stream()
            .map(String::toUpperCase)
            .sorted()
            .toList();
        String marketKey = market == null ? "" : market.trim().toUpperCase();
        return marketKey + ":" + String.join(",", canonical);
    }

    private List<String> normalizeTickersForRequest(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }
        return tickers.stream()
            .map(this::normalizeTicker)
            .filter(s -> !s.isBlank())
            .distinct()
            .collect(Collectors.toList());
    }

    private String normalizeTicker(String ticker) {
        return ticker == null ? "" : ticker.trim();
    }
}
