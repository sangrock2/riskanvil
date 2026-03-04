package com.sw103302.backend.controller;

import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.service.UsageService;
import com.sw103302.backend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/usage")
@Tag(name = "Usage", description = "API usage tracking and statistics")
public class UsageController {
    private final UsageService usageService;
    private final UserRepository userRepository;

    public UsageController(UsageService usageService, UserRepository userRepository) {
        this.usageService = usageService;
        this.userRepository = userRepository;
    }

    private ResponseEntity<String> json(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @GetMapping("/summary")
    @Operation(summary = "Get usage summary", description = "Retrieve aggregated API usage summary for the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usage summary retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<String> summary(
            @Parameter(description = "Use test data") @RequestParam(defaultValue = "false") boolean test) {
        String email = SecurityUtil.currentEmail();
        if (email == null) throw new IllegalStateException("unauthenticated");

        User user = userRepository.findByEmail(email).orElseThrow(() -> new IllegalStateException("user not found"));
        return json(usageService.summaryJson(user, test));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get usage dashboard", description = "Retrieve detailed usage dashboard with daily breakdown")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dashboard data retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<String> dashboard(
            @Parameter(description = "Use test data") @RequestParam(defaultValue = "false") boolean test,
            @Parameter(description = "Number of days to include") @RequestParam(defaultValue = "30") int days) {
        String email = SecurityUtil.currentEmail();
        if (email == null) throw new IllegalStateException("unauthenticated");

        User user = userRepository.findByEmail(email).orElseThrow(() -> new IllegalStateException("user not found"));
        return json(usageService.dashboardJson(user, test, days));
    }

    @DeleteMapping("/cleanup")
    @Operation(summary = "Delete old usage logs", description = "Delete usage logs older than specified days")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Old logs deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, Object>> cleanup(
            @Parameter(description = "Delete logs older than this many days") @RequestParam(defaultValue = "90") int olderThanDays) {
        String email = SecurityUtil.currentEmail();
        if (email == null) throw new IllegalStateException("unauthenticated");

        User user = userRepository.findByEmail(email).orElseThrow(() -> new IllegalStateException("user not found"));

        int deletedCount = usageService.deleteOldLogs(user, olderThanDays);

        return ResponseEntity.ok(Map.of(
                "deleted", deletedCount,
                "olderThanDays", olderThanDays
        ));
    }
}
