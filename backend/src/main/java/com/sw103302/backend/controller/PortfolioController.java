package com.sw103302.backend.controller;

import com.sw103302.backend.dto.*;
import com.sw103302.backend.service.EarningsCalendarService;
import com.sw103302.backend.service.PortfolioService;
import com.sw103302.backend.service.RiskDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
@Tag(name = "Portfolio", description = "Portfolio management APIs")
public class PortfolioController {
    private final PortfolioService portfolioService;
    private final EarningsCalendarService earningsCalendarService;
    private final RiskDashboardService riskDashboardService;

    public PortfolioController(PortfolioService portfolioService,
                               EarningsCalendarService earningsCalendarService,
                               RiskDashboardService riskDashboardService) {
        this.portfolioService = portfolioService;
        this.earningsCalendarService = earningsCalendarService;
        this.riskDashboardService = riskDashboardService;
    }

    @PostMapping
    @Operation(summary = "Create portfolio", description = "Create a new portfolio")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Portfolio created"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<PortfolioResponse> create(@Valid @RequestBody CreatePortfolioRequest req) {
        return ResponseEntity.ok(portfolioService.create(req));
    }

    @GetMapping
    @Operation(summary = "List portfolios", description = "Get all user portfolios")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Portfolios retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<PortfolioResponse>> list() {
        return ResponseEntity.ok(portfolioService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get portfolio detail", description = "Get detailed portfolio information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Portfolio retrieved"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<PortfolioDetailResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(portfolioService.detail(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update portfolio", description = "Update portfolio information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Portfolio updated"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<PortfolioResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdatePortfolioRequest req
    ) {
        return ResponseEntity.ok(portfolioService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete portfolio", description = "Delete a portfolio")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Portfolio deleted"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        portfolioService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/position")
    @Operation(summary = "Add position", description = "Add a position to portfolio")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Position added"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, Long>> addPosition(
        @PathVariable Long id,
        @Valid @RequestBody AddPositionRequest req
    ) {
        Long positionId = portfolioService.addPosition(id, req);
        return ResponseEntity.ok(Map.of("positionId", positionId));
    }

    @PutMapping("/{portfolioId}/position/{positionId}")
    @Operation(summary = "Update position", description = "Update a portfolio position")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Position updated"),
        @ApiResponse(responseCode = "404", description = "Position not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Void> updatePosition(
        @PathVariable Long portfolioId,
        @PathVariable Long positionId,
        @Valid @RequestBody UpdatePositionRequest req
    ) {
        portfolioService.updatePosition(portfolioId, positionId, req);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{portfolioId}/position/{positionId}")
    @Operation(summary = "Delete position", description = "Remove a position from portfolio")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Position deleted"),
        @ApiResponse(responseCode = "404", description = "Position not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Void> deletePosition(
        @PathVariable Long portfolioId,
        @PathVariable Long positionId
    ) {
        portfolioService.deletePosition(portfolioId, positionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/rebalance")
    @Operation(summary = "Rebalance portfolio", description = "Calculate BUY/SELL trades needed to reach target weights")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rebalance trades calculated"),
        @ApiResponse(responseCode = "400", description = "Invalid target weights"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<RebalanceResponse> rebalance(
        @PathVariable Long id,
        @Valid @RequestBody RebalanceRequest req
    ) {
        return ResponseEntity.ok(portfolioService.rebalance(id, req));
    }

    @GetMapping("/{id}/earnings-calendar")
    @Operation(summary = "Get portfolio earnings calendar", description = "Get upcoming earnings dates for all holdings in a portfolio")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Earnings calendar retrieved"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<EarningsCalendarResponse> earningsCalendar(
            @PathVariable Long id,
            @RequestParam(defaultValue = "90") int daysAhead
    ) {
        return ResponseEntity.ok(earningsCalendarService.getPortfolioCalendar(id, daysAhead));
    }

    @GetMapping("/{id}/risk-dashboard")
    @Operation(summary = "Get portfolio risk dashboard", description = "Compute portfolio-level risk metrics and concentration analysis")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Risk dashboard retrieved"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<RiskDashboardResponse> riskDashboard(
            @PathVariable Long id,
            @RequestParam(defaultValue = "252") int lookbackDays
    ) {
        return ResponseEntity.ok(riskDashboardService.getPortfolioRisk(id, lookbackDays));
    }
}
