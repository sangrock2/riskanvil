package com.sw103302.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 서비스 기반 포트폴리오 최적화 및 유사 종목 추천 엔드포인트
 * - POST /api/portfolio/efficient-frontier → AI /portfolio/efficient-frontier
 * - POST /api/similarity/find → AI /similarity/find
 */
@RestController
@Tag(name = "Portfolio AI", description = "AI-powered portfolio optimization and similar stocks")
public class PortfolioAiController {
    private static final Logger log = LoggerFactory.getLogger(PortfolioAiController.class);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public PortfolioAiController(AiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    private ResponseEntity<String> json(String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @PostMapping("/api/portfolio/efficient-frontier")
    @Operation(summary = "Efficient Frontier", description = "Calculate efficient frontier using scipy optimization")
    public ResponseEntity<String> efficientFrontier(@RequestBody Map<String, Object> req) {
        Map<String, Object> normalized = normalizeEfficientFrontierRequest(req);
        try {
            // 시뮬레이션 수가 많으면 시간이 오래 걸리므로 60초 타임아웃
            String json = aiClient.post("/portfolio/efficient-frontier", normalized, Duration.ofSeconds(60));
            return json(json);
        } catch (Exception e) {
            log.warn("Efficient frontier degraded fallback. reason={}", e.toString());
            return json(fallbackEfficientFrontier(normalized, e.getClass().getSimpleName()).toString());
        }
    }

    @PostMapping("/api/similarity/find")
    @Operation(summary = "Find Similar Stocks", description = "Find similar stocks by fundamental cosine similarity")
    public ResponseEntity<String> findSimilarStocks(@RequestBody Map<String, Object> req) {
        try {
            // 100~150개 종목 병렬 조회 → 최대 30초
            String json = aiClient.post("/similarity/find", req, Duration.ofSeconds(45));
            return json(json);
        } catch (Exception e) {
            log.warn("Similarity degraded fallback. reason={}", e.toString());
            return json(fallbackSimilarStocks(req, e.getClass().getSimpleName()).toString());
        }
    }

    private Map<String, Object> normalizeEfficientFrontierRequest(Map<String, Object> req) {
        Map<String, Object> in = (req == null) ? Map.of() : req;
        List<String> tickers = extractTickers(in.get("tickers"));
        if (tickers.size() < 2) {
            tickers = List.of("AAPL", "MSFT");
        }

        int nSimulations = extractInt(in.get("nSimulations"), 3000);
        if (in.containsKey("points")) {
            // legacy 옵션(points)도 시뮬레이션 횟수로 흡수
            nSimulations = extractInt(in.get("points"), nSimulations);
        }
        nSimulations = Math.max(200, Math.min(nSimulations, 10000));

        String start = asString(in.get("start"));
        String end = asString(in.get("end"));
        if ((start == null || start.isBlank()) && (end == null || end.isBlank()) && in.containsKey("days")) {
            int days = Math.max(30, Math.min(extractInt(in.get("days"), 365), 3650));
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(days);
            start = from.format(ISO_DATE);
            end = to.format(ISO_DATE);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tickers", tickers);
        out.put("nSimulations", nSimulations);
        if (start != null && !start.isBlank()) out.put("start", start);
        if (end != null && !end.isBlank()) out.put("end", end);
        return out;
    }

    private JsonNode fallbackEfficientFrontier(Map<String, Object> req, String reason) {
        List<String> tickers = extractTickers(req.get("tickers"));
        if (tickers.isEmpty()) tickers = List.of("AAPL", "MSFT");

        double w = 1.0 / tickers.size();
        Map<String, Double> weights = new LinkedHashMap<>();
        for (String t : tickers) {
            weights.put(t, w);
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("tickers", tickers);
        fallback.put("minVarianceWeights", weights);
        fallback.put("maxSharpeWeights", weights);
        fallback.put("minVarianceStats", Map.of("annReturn", 0.0, "annVol", 0.0, "sharpe", 0.0));
        fallback.put("maxSharpeStats", Map.of("annReturn", 0.0, "annVol", 0.0, "sharpe", 0.0));
        fallback.put("frontier", List.of());
        fallback.put("_degraded", Map.of("source", "backend_fallback", "reason", reason == null ? "unknown" : reason));
        return objectMapper.valueToTree(fallback);
    }

    private JsonNode fallbackSimilarStocks(Map<String, Object> req, String reason) {
        String ticker = asString(req == null ? null : req.get("ticker"));
        if (ticker == null || ticker.isBlank()) ticker = "AAPL";
        ticker = ticker.trim().toUpperCase();

        int topN = Math.max(1, Math.min(extractInt(req == null ? null : req.get("topN"), 5), 20));
        List<String> seeds = switch (ticker) {
            case "AAPL" -> List.of("MSFT", "GOOGL", "AMZN", "NVDA", "META");
            case "MSFT" -> List.of("AAPL", "GOOGL", "AMZN", "NVDA", "CRM");
            case "NVDA" -> List.of("AMD", "TSM", "INTC", "AVGO", "QCOM");
            default -> List.of("AAPL", "MSFT", "GOOGL", "AMZN", "NVDA");
        };

        ArrayNode arr = objectMapper.createArrayNode();
        int count = 0;
        for (String s : seeds) {
            if (Objects.equals(s, ticker)) continue;
            arr.add(objectMapper.createObjectNode()
                    .put("ticker", s)
                    .putNull("name")
                    .putNull("sector")
                    .put("similarity", 0.0)
                    .putNull("pe")
                    .putNull("pb")
                    .putNull("roe")
                    .putNull("revenueGrowth")
                    .putNull("marketCap")
                    .put("_degradedReason", reason == null ? "unknown" : reason)
            );
            count++;
            if (count >= topN) break;
        }
        return arr;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractTickers(Object raw) {
        if (!(raw instanceof List<?> list)) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (Object v : list) {
            if (v == null) continue;
            String t = String.valueOf(v).trim().toUpperCase();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private int extractInt(Object raw, int defaultValue) {
        if (raw == null) return defaultValue;
        if (raw instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private String asString(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }
}
