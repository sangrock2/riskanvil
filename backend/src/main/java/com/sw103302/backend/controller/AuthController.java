package com.sw103302.backend.controller;

import com.sw103302.backend.dto.AuthResponse;
import com.sw103302.backend.dto.EmailAvailabilityResponse;
import com.sw103302.backend.dto.LoginRequest;
import com.sw103302.backend.dto.RefreshRequest;
import com.sw103302.backend.dto.RegisterRequest;
import com.sw103302.backend.dto.VerifyTwoFactorLoginRequest;
import com.sw103302.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Validated
@Tag(name = "Authentication", description = "User authentication and registration APIs")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/check-email")
    @Operation(summary = "Check email availability", description = "Check whether the email can be used for registration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email availability checked",
                    content = @Content(schema = @Schema(implementation = EmailAvailabilityResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid email format")
    })
    public ResponseEntity<EmailAvailabilityResponse> checkEmailAvailability(
            @RequestParam
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            @Size(max = 255, message = "Email cannot exceed 255 characters")
            String email
    ) {
        return ResponseEntity.ok(auth.checkEmailAvailability(email));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Create a new user account with email and password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or email already exists")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(auth.register(req));
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and return JWT token. If 2FA is enabled, returns requires2FA=true with pendingToken instead of JWT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful or 2FA required",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(auth.login(req));
    }

    @PostMapping("/verify-2fa")
    @Operation(summary = "Verify 2FA code", description = "Complete login by verifying TOTP or backup code. Use pendingToken from /login response.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "2FA verified, JWT issued",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token / wrong 2FA code")
    })
    public ResponseEntity<AuthResponse> verify2FA(@Valid @RequestBody VerifyTwoFactorLoginRequest req) {
        return ResponseEntity.ok(auth.verifyTwoFactorLogin(req));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Get a new access token using refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or expired refresh token")
    })
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(auth.refreshAccessToken(req.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Revoke refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
        return ResponseEntity.ok().build();
    }
}
