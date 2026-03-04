package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.EarningsCalendarResponse;
import com.sw103302.backend.entity.PortfolioPosition;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.PortfolioPositionRepository;
import com.sw103302.backend.repository.PortfolioRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EarningsCalendarService {
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;

    public EarningsCalendarService(AiClient aiClient,
                                   ObjectMapper objectMapper,
                                   UserRepository userRepository,
                                   PortfolioRepository portfolioRepository,
                                   PortfolioPositionRepository positionRepository) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
    }

    @Transactional(readOnly = true)
    public EarningsCalendarResponse getPortfolioCalendar(Long portfolioId, int daysAhead) {
        int normalizedDaysAhead = Math.max(7, Math.min(daysAhead, 365));

        String email = SecurityUtil.currentEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        portfolioRepository.findByIdAndUser_Id(portfolioId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        List<PortfolioPosition> positions = positionRepository.findByPortfolio_IdOrderByCreatedAtDesc(portfolioId);
        if (positions.isEmpty()) {
            return new EarningsCalendarResponse(
                    normalizedDaysAhead,
                    OffsetDateTime.now().toString(),
                    List.of()
            );
        }

        Map<String, List<String>> tickersByMarket = positions.stream()
                .collect(Collectors.groupingBy(
                        PortfolioPosition::getMarket,
                        Collectors.mapping(PortfolioPosition::getTicker, Collectors.collectingAndThen(
                                Collectors.toCollection(LinkedHashSet::new),
                                ArrayList::new
                        ))
                ));

        List<EarningsCalendarResponse.EarningsEvent> merged = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : tickersByMarket.entrySet()) {
            String market = entry.getKey();
            List<String> tickers = entry.getValue();

            if (tickers.isEmpty()) {
                continue;
            }

            try {
                Map<String, Object> req = Map.of(
                        "tickers", tickers,
                        "market", market,
                        "daysAhead", normalizedDaysAhead
                );

                String json = aiClient.post("/earnings/calendar", req);
                JsonNode root = objectMapper.readTree(json);
                JsonNode events = root.get("events");
                if (events == null || !events.isArray()) {
                    continue;
                }

                for (JsonNode node : events) {
                    merged.add(new EarningsCalendarResponse.EarningsEvent(
                            text(node, "ticker"),
                            text(node, "market"),
                            text(node, "earningsDate"),
                            text(node, "fiscalDateEnding"),
                            text(node, "time"),
                            number(node, "epsEstimate"),
                            number(node, "epsActual"),
                            number(node, "revenueEstimate"),
                            number(node, "revenueActual")
                    ));
                }
            } catch (Exception e) {
                // best-effort per market
            }
        }

        merged.sort(Comparator.comparing(e -> parseDate(e.earningsDate())));

        return new EarningsCalendarResponse(
                normalizedDaysAhead,
                OffsetDateTime.now().toString(),
                merged
        );
    }

    private String text(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText();
    }

    private Double number(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull() || !v.isNumber()) {
            return null;
        }
        return v.asDouble();
    }

    private LocalDate parseDate(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return LocalDate.MAX;
        }
        try {
            return LocalDate.parse(dateText);
        } catch (Exception ignore) {
            return LocalDate.MAX;
        }
    }
}
