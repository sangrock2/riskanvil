package com.sw103302.backend.controller;

import com.sw103302.backend.dto.TagCreateRequest;
import com.sw103302.backend.dto.TagResponse;
import com.sw103302.backend.dto.TagUpdateRequest;
import com.sw103302.backend.service.WatchlistTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist/tags")
@Tag(name = "Watchlist Tags", description = "Tag management for watchlist")
public class WatchlistTagController {
    private final WatchlistTagService tagService;

    public WatchlistTagController(WatchlistTagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    @Operation(summary = "List all tags", description = "Get all tags for current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tags retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<TagResponse>> list() {
        return ResponseEntity.ok(tagService.list());
    }

    @PostMapping
    @Operation(summary = "Create tag", description = "Create a new tag")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tag created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagCreateRequest req) {
        return ResponseEntity.ok(tagService.create(req));
    }

    @PutMapping("/{tagId}")
    @Operation(summary = "Update tag", description = "Update an existing tag")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tag updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Tag not found")
    })
    public ResponseEntity<TagResponse> update(
            @Parameter(description = "Tag ID") @PathVariable Long tagId,
            @Valid @RequestBody TagUpdateRequest req
    ) {
        return ResponseEntity.ok(tagService.update(tagId, req));
    }

    @DeleteMapping("/{tagId}")
    @Operation(summary = "Delete tag", description = "Delete a tag")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tag deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Tag not found")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Tag ID") @PathVariable Long tagId
    ) {
        tagService.delete(tagId);
        return ResponseEntity.ok().build();
    }
}
