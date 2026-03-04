package com.sw103302.backend.controller;

import com.sw103302.backend.dto.UpdateSettingsRequest;
import com.sw103302.backend.dto.UserSettingsResponse;
import com.sw103302.backend.service.UserSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings", description = "User settings management APIs")
public class UserSettingsController {
    private final UserSettingsService settingsService;

    public UserSettingsController(UserSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @Operation(summary = "Get settings", description = "Retrieve user settings")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Settings retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<UserSettingsResponse> get() {
        return ResponseEntity.ok(settingsService.get());
    }

    @PutMapping
    @Operation(summary = "Update settings", description = "Update user settings")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Settings updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<UserSettingsResponse> update(@Valid @RequestBody UpdateSettingsRequest req) {
        return ResponseEntity.ok(settingsService.update(req));
    }
}
