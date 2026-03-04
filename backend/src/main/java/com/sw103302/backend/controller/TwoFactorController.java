package com.sw103302.backend.controller;

import com.sw103302.backend.dto.DisableTotpRequest;
import com.sw103302.backend.dto.SetupTotpResponse;
import com.sw103302.backend.dto.VerifyTotpRequest;
import com.sw103302.backend.service.TwoFactorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/2fa")
@Tag(name = "Two-Factor Authentication", description = "2FA management APIs")
public class TwoFactorController {
    private final TwoFactorService twoFactorService;

    public TwoFactorController(TwoFactorService twoFactorService) {
        this.twoFactorService = twoFactorService;
    }

    @PostMapping("/setup")
    @Operation(summary = "Setup TOTP", description = "Initialize 2FA setup")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Setup initiated"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<SetupTotpResponse> setup() {
        return ResponseEntity.ok(twoFactorService.setupTotp());
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify and enable TOTP", description = "Verify TOTP code and enable 2FA")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "2FA enabled"),
        @ApiResponse(responseCode = "400", description = "Invalid code"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, Boolean>> verify(@Valid @RequestBody VerifyTotpRequest req) {
        boolean valid = twoFactorService.verifyAndEnable(req);
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    @PostMapping("/disable")
    @Operation(summary = "Disable TOTP", description = "Disable 2FA")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "2FA disabled"),
        @ApiResponse(responseCode = "400", description = "Invalid credentials"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Void> disable(@Valid @RequestBody DisableTotpRequest req) {
        twoFactorService.disable(req);
        return ResponseEntity.ok().build();
    }
}
