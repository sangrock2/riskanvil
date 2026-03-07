package com.sw103302.backend.controller;

import com.sw103302.backend.component.AiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/etf")
@Validated
@Tag(name = "ETF", description = "ETF information APIs")
public class EtfController {
    private final AiClient aiClient;

    public EtfController(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    private ResponseEntity<String> json(String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @GetMapping("/info/{ticker}")
    @Operation(summary = "Get ETF info", description = "Retrieve basic ETF information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "ETF info retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<String> info(
            @Parameter(description = "ETF ticker", required = true) @PathVariable String ticker,
            @Parameter(description = "Market (US/KR)") @RequestParam(defaultValue = "US") String market
    ) {
        return json(aiClient.etfInfo(ticker, market));
    }

    @GetMapping("/holdings/{ticker}")
    @Operation(summary = "Get ETF holdings", description = "Retrieve ETF holdings and composition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "ETF holdings retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<String> holdings(
            @Parameter(description = "ETF ticker", required = true) @PathVariable String ticker,
            @Parameter(description = "Market (US/KR)") @RequestParam(defaultValue = "US") String market
    ) {
        return json(aiClient.etfHoldings(ticker, market));
    }
}
