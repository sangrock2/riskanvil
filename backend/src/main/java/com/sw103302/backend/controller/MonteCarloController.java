package com.sw103302.backend.controller;

import com.sw103302.backend.dto.MonteCarloRequest;
import com.sw103302.backend.dto.MonteCarloResponse;
import com.sw103302.backend.service.MonteCarloService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monte-carlo")
@Tag(name = "Monte Carlo", description = "Monte Carlo simulation APIs")
public class MonteCarloController {
    private final MonteCarloService monteCarloService;

    public MonteCarloController(MonteCarloService monteCarloService) {
        this.monteCarloService = monteCarloService;
    }

    @PostMapping
    @Operation(summary = "Run simulation", description = "Run Monte Carlo price simulation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Simulation complete"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<MonteCarloResponse> simulate(@Valid @RequestBody MonteCarloRequest req) {
        return ResponseEntity.ok(monteCarloService.simulate(req));
    }
}
