package com.sw103302.backend.controller;

import com.sw103302.backend.dto.*;
import com.sw103302.backend.service.ScreenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/screener")
@Tag(name = "Screener", description = "Stock screener APIs")
public class ScreenerController {
    private final ScreenerService screenerService;

    public ScreenerController(ScreenerService screenerService) {
        this.screenerService = screenerService;
    }

    @PostMapping
    @Operation(summary = "Screen stocks", description = "Run stock screener with filters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Screening complete"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<ScreenerResult>> screen(@Valid @RequestBody ScreenerRequest req) {
        return ResponseEntity.ok(screenerService.screen(req));
    }

    @PostMapping("/preset")
    @Operation(summary = "Save screener preset", description = "Save screening filters as preset")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Preset saved"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Void> savePreset(@Valid @RequestBody SaveScreenerPresetRequest req) {
        screenerService.savePreset(req);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/preset")
    @Operation(summary = "List user presets", description = "Get user's saved screener presets")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Presets retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<ScreenerPresetResponse>> listPresets() {
        return ResponseEntity.ok(screenerService.listPresets());
    }

    @GetMapping("/preset/public")
    @Operation(summary = "List public presets", description = "Get public screener presets")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Presets retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<ScreenerPresetResponse>> listPublicPresets() {
        return ResponseEntity.ok(screenerService.listPublicPresets());
    }
}
