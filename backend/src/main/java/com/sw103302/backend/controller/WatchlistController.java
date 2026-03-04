package com.sw103302.backend.controller;

import com.sw103302.backend.dto.WatchlistAddRequest;
import com.sw103302.backend.dto.WatchlistItemResponse;
import com.sw103302.backend.dto.WatchlistUpdateTagsRequest;
import com.sw103302.backend.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watchlist")
@Tag(name = "Watchlist", description = "User watchlist management APIs")
public class WatchlistController {
    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    @Operation(summary = "Get watchlist", description = "Retrieve user's watchlist with current prices")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Watchlist retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<WatchlistItemResponse>> list(
            @Parameter(description = "Use test data") @RequestParam(defaultValue = "false") boolean test
    ) {
        return ResponseEntity.ok(watchlistService.list(test));
    }

    @PostMapping
    @Operation(summary = "Add to watchlist", description = "Add a stock to user's watchlist")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock added successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Void> add(
            @Valid @RequestBody WatchlistAddRequest req,
            @Parameter(description = "Use test data") @RequestParam(defaultValue = "false") boolean test
    ) {
        watchlistService.add(req, test);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    @Operation(summary = "Remove from watchlist", description = "Remove a stock from user's watchlist")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock removed successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Stock not found in watchlist")
    })
    public ResponseEntity<Void> remove(
            @Parameter(description = "Ticker symbol", required = true) @RequestParam String ticker,
            @Parameter(description = "Market (US/KR)") @RequestParam(defaultValue = "US") String market,
            @Parameter(description = "Use test data") @RequestParam(defaultValue = "false") boolean test
    ) {
        watchlistService.remove(ticker, market, test);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{itemId}/tags")
    @Operation(summary = "Update item tags", description = "Update tags for a watchlist item")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tags updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Item not found")
    })
    public ResponseEntity<Void> updateTags(
            @Parameter(description = "Watchlist item ID") @PathVariable Long itemId,
            @RequestBody WatchlistUpdateTagsRequest req
    ) {
        watchlistService.updateTags(itemId, req);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{itemId}/notes")
    @Operation(summary = "Update item notes", description = "Update notes for a watchlist item")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notes updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Item not found")
    })
    public ResponseEntity<Void> updateNotes(
            @Parameter(description = "Watchlist item ID") @PathVariable Long itemId,
            @RequestBody Map<String, String> body
    ) {
        String notes = body.getOrDefault("notes", "");
        watchlistService.updateNotes(itemId, notes);
        return ResponseEntity.ok().build();
    }
}
