package com.sw103302.backend.controller;

import com.sw103302.backend.dto.AnalysisRequest;
import com.sw103302.backend.dto.AnalysisResponse;
import com.sw103302.backend.dto.AnalysisRunSummary;
import com.sw103302.backend.dto.PageResponse;
import com.sw103302.backend.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analysis")
@Tag(name = "Analysis", description = "AI-powered stock analysis APIs")
public class AnalysisController {
    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    @Operation(summary = "Run stock analysis", description = "Perform AI analysis on a given stock ticker")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Analysis completed",
                    content = @Content(schema = @Schema(implementation = AnalysisResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public AnalysisResponse analyze(@Valid @RequestBody AnalysisRequest req) {
        return analysisService.analyzeAndSave(req);
    }

    @GetMapping("/history")
    @Operation(summary = "Get analysis history", description = "Retrieve paginated analysis history for the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public PageResponse<AnalysisRunSummary> history(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort order (e.g., createdAt,desc)") @RequestParam(defaultValue = "createdAt,desc") String sort,
            @Parameter(description = "Filter by ticker symbol") @RequestParam(required = false) String ticker,
            @Parameter(description = "Filter by market (US/KR)") @RequestParam(required = false) String market,
            @Parameter(description = "Filter by action type") @RequestParam(required = false) String action,
            @Parameter(description = "Filter from date (YYYY-MM-DD)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Filter to date (YYYY-MM-DD)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return analysisService.myHistoryPage(page, size, sort, ticker, market, action, from, to);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get analysis detail", description = "Retrieve detailed analysis result by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Analysis detail retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Analysis not found")
    })
    public Object detail(@Parameter(description = "Analysis run ID") @PathVariable Long id) {
        return analysisService.myRunDetail(id);
    }

    /*
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> detail(@PathVariable Long id) {
        String json = analysisService.myRunDetail(id);
        return ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }
    @GetMapping("/{id}")
    public JsonNode detail(@PathVariable Long id) {
        return analysisService.myRunDetail(id);
    }

     */
}
