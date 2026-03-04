package com.sw103302.backend.controller;

import com.sw103302.backend.dto.BacktestRequest;
import com.sw103302.backend.dto.BacktestResponse;
import com.sw103302.backend.dto.BacktestRunSummary;
import com.sw103302.backend.dto.PageResponse;
import com.sw103302.backend.service.BacktestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backtest")
@Tag(name = "Backtest", description = "Trading strategy backtesting APIs")
public class BacktestController {
    private final BacktestService service;

    public BacktestController(BacktestService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Run backtest", description = "Execute a trading strategy backtest on historical data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Backtest completed",
                    content = @Content(schema = @Schema(implementation = BacktestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public BacktestResponse run(@Valid @RequestBody BacktestRequest req) {
        return service.runAndSave(req);
    }

    @GetMapping("/history")
    @Operation(summary = "Get backtest history", description = "Retrieve paginated backtest history for the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public PageResponse<BacktestRunSummary> history(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort order (e.g., createdAt,desc)") @RequestParam(defaultValue = "createdAt,desc") String sort,
            @Parameter(description = "Filter by ticker symbol") @RequestParam(required = false) String ticker,
            @Parameter(description = "Filter by market (US/KR)") @RequestParam(required = false) String market,
            @Parameter(description = "Filter by strategy name") @RequestParam(required = false) String strategy
    ) {
        return service.myBacktestHistoryPage(page, size, sort, ticker, market, strategy);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get backtest detail", description = "Retrieve detailed backtest result by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Backtest detail retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Backtest not found")
    })
    public Object detail(@Parameter(description = "Backtest run ID") @PathVariable Long id) {
        return service.myDetail(id);
    }
}
