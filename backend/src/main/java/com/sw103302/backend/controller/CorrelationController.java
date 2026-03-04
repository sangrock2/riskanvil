package com.sw103302.backend.controller;

import com.sw103302.backend.dto.CorrelationRequest;
import com.sw103302.backend.dto.CorrelationResponse;
import com.sw103302.backend.service.CorrelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/correlation")
@Tag(name = "Correlation", description = "Correlation analysis APIs")
public class CorrelationController {
    private final CorrelationService correlationService;

    public CorrelationController(CorrelationService correlationService) {
        this.correlationService = correlationService;
    }

    @PostMapping
    @Operation(summary = "Analyze correlation", description = "Analyze correlation between stocks")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Analysis complete"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<CorrelationResponse> analyze(@Valid @RequestBody CorrelationRequest req) {
        return ResponseEntity.ok(correlationService.analyze(req));
    }
}
