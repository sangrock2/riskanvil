package com.sw103302.backend.controller;

import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.InsightRequest;
import com.sw103302.backend.service.MarketCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/market")
@Validated
@Tag(name = "Market", description = "Market data and insights APIs")
public class MarketController {
    private final AiClient aiClient;
    private final MarketCacheService marketCacheService;

    public MarketController(AiClient aiClient, MarketCacheService marketCacheService) {
        this.aiClient = aiClient;
        this.marketCacheService = marketCacheService;
    }

    private ResponseEntity<String> json(String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @GetMapping("/search")
    @Operation(summary = "Search symbols", description = "Search for stock symbols by keywords")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<String> search(
            @Parameter(description = "Search keywords", required = true) @RequestParam String keywords,
            @Parameter(description = "Market (US/KR)") @RequestParam(defaultValue = "US") String market,
            @Parameter(description = "Use test data") @RequestParam(defaultValue = "false") boolean test) {
        return json(aiClient.symbolSearch(keywords, market, test));
    }

    @PostMapping("/insights")
    @Operation(summary = "Get market insights", description = "Retrieve comprehensive market insights for a stock")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Insights retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<String> insights(
            @Valid @RequestBody InsightRequest req,
            @Parameter(description = "Use test data") @RequestParam(defaultValue = "false") boolean test,
            @Parameter(description = "Force refresh cache") @RequestParam(defaultValue = "false") boolean refresh) {
        return json(marketCacheService.getInsights(req, test, refresh));
    }

    @GetMapping("/quote")
    @Operation(summary = "Get stock quote", description = "Retrieve current quote for a stock")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Quote retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<String> quote(
            @Parameter(description = "Ticker symbol", required = true) @RequestParam String ticker,
            @Parameter(description = "Market (US/KR)") @RequestParam(defaultValue = "US") String market) {
        return json(aiClient.quote(ticker, market));
    }

    @GetMapping("/prices")
    @Operation(summary = "Get historical prices", description = "Retrieve historical price data for a stock")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Price data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<String> prices(
            @Parameter(description = "Ticker symbol", required = true) @RequestParam String ticker,
            @Parameter(description = "Market (US/KR)") @RequestParam(defaultValue = "US") String market,
            @Parameter(description = "Number of days of history") @RequestParam(defaultValue = "90") int days) {
        return json(aiClient.prices(ticker, market, days));
    }

    @GetMapping("/ohlc")
    @Operation(summary = "Get OHLC data", description = "Retrieve OHLC (Open, High, Low, Close) and volume data for a stock")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OHLC data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<String> ohlc(
            @Parameter(description = "Ticker symbol", required = true) @RequestParam String ticker,
            @Parameter(description = "Market (US/KR)") @RequestParam(defaultValue = "US") String market,
            @Parameter(description = "Number of days of history") @RequestParam(defaultValue = "90") int days) {
        return json(aiClient.ohlc(ticker, market, days));
    }

    @PostMapping("/report")
    @Operation(summary = "Generate AI report", description = "Generate AI-powered analysis report for a stock")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<String> report(
            @Valid @RequestBody InsightRequest req,
            @Parameter(description = "Use test data") @RequestParam(defaultValue = "false") boolean test,
            @Parameter(description = "Force refresh cache") @RequestParam(defaultValue = "false") boolean refresh,
            @Parameter(description = "Include web search data") @RequestParam(defaultValue = "true") boolean web
    ) {
        return json(marketCacheService.getReport(req, test, refresh, web));
    }

    @GetMapping("/report-history")
    @Operation(summary = "Get report history", description = "Retrieve historical reports for a stock")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report history retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<String> reportHistory(
            @Parameter(description = "Ticker symbol", required = true) @RequestParam String ticker,
            @Parameter(description = "Market (US/KR)") @RequestParam(defaultValue = "US") String market,
            @Parameter(description = "Use test data") @RequestParam(defaultValue = "false") boolean test,
            @Parameter(description = "Number of days of history") @RequestParam(defaultValue = "90") int days,
            @Parameter(description = "News article limit") @RequestParam(defaultValue = "20") int newsLimit,
            @Parameter(description = "Report limit") @RequestParam(defaultValue = "20") int limit
    ) {
        return json(marketCacheService.getReportHistory(ticker, market, test, days, newsLimit, limit));
    }
}
