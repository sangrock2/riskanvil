package com.sw103302.backend.controller;

import com.sw103302.backend.dto.AuthResponse;
import com.sw103302.backend.dto.EmailAvailabilityRequest;
import com.sw103302.backend.dto.EmailAvailabilityResponse;
import com.sw103302.backend.dto.LoginRequest;
import com.sw103302.backend.dto.RefreshRequest;
import com.sw103302.backend.dto.RegisterRequest;
import com.sw103302.backend.dto.VerifyTwoFactorLoginRequest;
import com.sw103302.backend.service.AuthService;
import com.sw103302.backend.service.RefreshTokenCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/api/auth")
@Validated
@Tag(name = "Authentication", description = "User authentication and registration APIs")
public class AuthController {
    private final AuthService auth;
    private final RefreshTokenCookieService refreshTokenCookieService;

    public AuthController(AuthService auth, RefreshTokenCookieService refreshTokenCookieService) {
        this.auth = auth;
        this.refreshTokenCookieService = refreshTokenCookieService;
    }

    @PostMapping("/check-email")
    @Operation(summary = "Check email availability", description = "Check whether the email can be used for registration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email availability checked",
                    content = @Content(schema = @Schema(implementation = EmailAvailabilityResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid email format")
    })
    public ResponseEntity<EmailAvailabilityResponse> checkEmailAvailability(
            @Valid @RequestBody EmailAvailabilityRequest req
    ) {
        return ResponseEntity.ok(auth.checkEmailAvailability(req.email()));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Create a new user account with email and password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or email already exists")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return withRefreshCookie(auth.register(req));
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and return JWT token. If 2FA is enabled, returns requires2FA=true with pendingToken instead of JWT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful or 2FA required",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return withRefreshCookie(auth.login(req));
    }

    @PostMapping("/verify-2fa")
    @Operation(summary = "Verify 2FA code", description = "Complete login by verifying TOTP or backup code. Use pendingToken from /login response.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "2FA verified, JWT issued",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token / wrong 2FA code")
    })
    public ResponseEntity<AuthResponse> verify2FA(@Valid @RequestBody VerifyTwoFactorLoginRequest req) {
        return withRefreshCookie(auth.verifyTwoFactorLogin(req));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Get a new access token using the HttpOnly refresh-token cookie or a legacy request body token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or expired refresh token")
    })
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody(required = false) RefreshRequest req,
            HttpServletRequest request
    ) {
        return withRefreshCookie(auth.refreshAccessToken(resolveRefreshToken(req, request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Revoke refresh token and clear the HttpOnly refresh-token cookie")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshRequest req,
            HttpServletRequest request
    ) {
        auth.logout(resolveRefreshToken(req, request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthResponse response) {
        if (response == null || Boolean.TRUE.equals(response.requires2FA())) {
            return ResponseEntity.ok(response);
        }

        if (response.refreshToken() == null || response.refreshToken().isBlank()) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.create(response.refreshToken()).toString())
                .body(AuthResponse.accessOnly(response.accessToken()));
    }

    private String resolveRefreshToken(RefreshRequest req, HttpServletRequest request) {
        if (req != null && req.refreshToken() != null && !req.refreshToken().isBlank()) {
            return req.refreshToken();
        }

        var cookie = WebUtils.getCookie(request, refreshTokenCookieService.cookieName());
        return cookie == null ? "" : cookie.getValue();
    }
}
