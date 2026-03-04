package com.sw103302.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * AI 서비스 기반 포트폴리오 최적화 및 유사 종목 추천 엔드포인트
 * - POST /api/portfolio/efficient-frontier → AI /portfolio/efficient-frontier
 * - POST /api/similarity/find → AI /similarity/find
 */
@RestController
@Tag(name = "Portfolio AI", description = "AI-powered portfolio optimization and similar stocks")
public class PortfolioAiController {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public PortfolioAiController(AiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/portfolio/efficient-frontier")
    @Operation(summary = "Efficient Frontier", description = "Calculate efficient frontier using scipy optimization")
    public ResponseEntity<JsonNode> efficientFrontier(@RequestBody Map<String, Object> req) {
        try {
            // 시뮬레이션 수가 많으면 시간이 오래 걸리므로 60초 타임아웃
            String json = aiClient.post("/portfolio/efficient-frontier", req, Duration.ofSeconds(60));
            return ResponseEntity.ok(objectMapper.readTree(json));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service error: " + e.getMessage());
        }
    }

    @PostMapping("/api/similarity/find")
    @Operation(summary = "Find Similar Stocks", description = "Find similar stocks by fundamental cosine similarity")
    public ResponseEntity<JsonNode> findSimilarStocks(@RequestBody Map<String, Object> req) {
        try {
            // 100~150개 종목 병렬 조회 → 최대 30초
            String json = aiClient.post("/similarity/find", req, Duration.ofSeconds(45));
            return ResponseEntity.ok(objectMapper.readTree(json));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service error: " + e.getMessage());
        }
    }
}
