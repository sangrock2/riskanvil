package com.sw103302.backend.controller;

import com.sw103302.backend.dto.DividendCalendarResponse;
import com.sw103302.backend.dto.DividendHistoryResponse;
import com.sw103302.backend.service.DividendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dividend")
@Tag(name = "Dividend", description = "Dividend tracking and calendar")
public class DividendController {

    private final DividendService dividendService;

    public DividendController(DividendService dividendService) {
        this.dividendService = dividendService;
    }

    @PostMapping("/position/{positionId}/fetch")
    @Operation(summary = "Fetch and store dividend data for a position")
    public ResponseEntity<Void> fetchDividends(@PathVariable Long positionId) {
        dividendService.fetchAndStoreDividends(positionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/position/{positionId}/history")
    @Operation(summary = "Get dividend history for a position")
    public ResponseEntity<DividendHistoryResponse> getDividendHistory(@PathVariable Long positionId) {
        return ResponseEntity.ok(dividendService.getDividendHistory(positionId));
    }

    @GetMapping("/portfolio/{portfolioId}/calendar")
    @Operation(summary = "Get dividend calendar for entire portfolio")
    public ResponseEntity<DividendCalendarResponse> getPortfolioCalendar(@PathVariable Long portfolioId) {
        return ResponseEntity.ok(dividendService.getPortfolioDividendCalendar(portfolioId));
    }
}
