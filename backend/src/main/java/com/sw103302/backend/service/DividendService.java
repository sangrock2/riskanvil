package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.DividendCalendarResponse;
import com.sw103302.backend.dto.DividendHistoryResponse;
import com.sw103302.backend.entity.Dividend;
import com.sw103302.backend.entity.PortfolioPosition;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.DividendRepository;
import com.sw103302.backend.repository.PortfolioPositionRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DividendService {
    private final DividendRepository dividendRepository;
    private final PortfolioPositionRepository positionRepository;
    private final UserRepository userRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public DividendService(DividendRepository dividendRepository,
                           PortfolioPositionRepository positionRepository,
                           UserRepository userRepository,
                           AiClient aiClient,
                           ObjectMapper objectMapper) {
        this.dividendRepository = dividendRepository;
        this.positionRepository = positionRepository;
        this.userRepository = userRepository;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void fetchAndStoreDividends(Long positionId) {
        String userEmail = SecurityUtil.requireCurrentEmail();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        PortfolioPosition position = positionRepository.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Position not found"));

        // Verify ownership
        if (!position.getPortfolio().getUser().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized to access this position");
        }

        try {
            // Call AI service to fetch dividend data
            Map<String, Object> request = new HashMap<>();
            request.put("ticker", position.getTicker());
            request.put("market", position.getMarket());

            String jsonResponse = aiClient.post("/dividend/history", request);
            JsonNode response = objectMapper.readTree(jsonResponse);

            JsonNode dividendsArray = response.get("dividends");
            if (dividendsArray == null || !dividendsArray.isArray()) {
                return; // No dividends to store
            }

            // Delete existing dividends for this position
            List<Dividend> existing = dividendRepository.findByPortfolioPosition_IdOrderByExDateDesc(positionId);
            dividendRepository.deleteAll(existing);

            // Store new dividends
            for (JsonNode divNode : dividendsArray) {
                String exDateStr = divNode.get("exDate").asText();
                double amount = divNode.get("amount").asDouble();
                String currency = divNode.get("currency").asText();

                LocalDate exDate = LocalDate.parse(exDateStr, DateTimeFormatter.ISO_DATE);

                // Parse optional fields
                LocalDate paymentDate = null;
                if (divNode.has("paymentDate") && !divNode.get("paymentDate").isNull()) {
                    paymentDate = LocalDate.parse(divNode.get("paymentDate").asText());
                }

                LocalDate recordDate = null;
                if (divNode.has("recordDate") && !divNode.get("recordDate").isNull()) {
                    recordDate = LocalDate.parse(divNode.get("recordDate").asText());
                }

                LocalDate declaredDate = null;
                if (divNode.has("declaredDate") && !divNode.get("declaredDate").isNull()) {
                    declaredDate = LocalDate.parse(divNode.get("declaredDate").asText());
                }

                String frequency = null;
                if (divNode.has("frequency") && !divNode.get("frequency").isNull()) {
                    frequency = divNode.get("frequency").asText();
                }

                // Create dividend using constructor
                Dividend dividend = new Dividend(
                    position,
                    BigDecimal.valueOf(amount),
                    currency,
                    exDate,
                    paymentDate,
                    recordDate,
                    declaredDate,
                    frequency
                );

                dividendRepository.save(dividend);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch dividends: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public DividendHistoryResponse getDividendHistory(Long positionId) {
        String userEmail = SecurityUtil.requireCurrentEmail();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        PortfolioPosition position = positionRepository.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Position not found"));

        if (!position.getPortfolio().getUser().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized");
        }

        List<Dividend> dividends = dividendRepository.findByPortfolioPosition_IdOrderByExDateDesc(positionId);

        List<DividendHistoryResponse.DividendEvent> events = dividends.stream()
                .map(d -> new DividendHistoryResponse.DividendEvent(
                        d.getId(),
                        d.getExDate(),
                        d.getPaymentDate(),
                        d.getAmount(),
                        d.getCurrency(),
                        d.getFrequency()
                ))
                .collect(Collectors.toList());

        BigDecimal totalDividends = dividends.stream()
                .map(Dividend::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DividendHistoryResponse(
                position.getTicker(),
                position.getMarket(),
                events,
                totalDividends
        );
    }

    @Transactional(readOnly = true)
    public DividendCalendarResponse getPortfolioDividendCalendar(Long portfolioId) {
        String userEmail = SecurityUtil.requireCurrentEmail();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // Get all positions in portfolio
        List<PortfolioPosition> positions = positionRepository.findByPortfolio_IdOrderByCreatedAtDesc(portfolioId);

        // Verify ownership
        if (!positions.isEmpty() && !positions.get(0).getPortfolio().getUser().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized");
        }

        // Get dividends for all positions
        List<Long> positionIds = positions.stream().map(PortfolioPosition::getId).collect(Collectors.toList());
        List<Dividend> allDividends = new ArrayList<>();
        for (Long posId : positionIds) {
            allDividends.addAll(dividendRepository.findByPortfolioPosition_IdOrderByExDateDesc(posId));
        }

        // Separate upcoming and past
        LocalDate today = LocalDate.now();
        List<DividendCalendarResponse.CalendarEvent> upcoming = new ArrayList<>();
        List<DividendCalendarResponse.CalendarEvent> past = new ArrayList<>();

        Map<Long, PortfolioPosition> positionMap = positions.stream()
                .collect(Collectors.toMap(PortfolioPosition::getId, p -> p));

        for (Dividend div : allDividends) {
            PortfolioPosition pos = positionMap.get(div.getPortfolioPosition().getId());
            if (pos == null) continue;

            BigDecimal totalAmount = div.getAmount().multiply(pos.getQuantity());

            DividendCalendarResponse.CalendarEvent event = new DividendCalendarResponse.CalendarEvent(
                    pos.getTicker(),
                    div.getExDate(),
                    div.getPaymentDate(),
                    div.getAmount(),
                    pos.getQuantity(),
                    totalAmount,
                    div.getCurrency()
            );

            if (div.getExDate().isAfter(today) || div.getExDate().isEqual(today)) {
                upcoming.add(event);
            } else {
                past.add(event);
            }
        }

        // Sort upcoming by date ascending, past by date descending
        upcoming.sort(Comparator.comparing(DividendCalendarResponse.CalendarEvent::exDate));
        past.sort(Comparator.comparing(DividendCalendarResponse.CalendarEvent::exDate).reversed());

        // Calculate totals
        BigDecimal totalUpcoming = upcoming.stream()
                .map(DividendCalendarResponse.CalendarEvent::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPast = past.stream()
                .map(DividendCalendarResponse.CalendarEvent::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DividendCalendarResponse(upcoming, past, totalUpcoming, totalPast);
    }
}
