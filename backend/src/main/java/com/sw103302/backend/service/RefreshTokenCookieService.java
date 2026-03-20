package com.sw103302.backend.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
public class RefreshTokenCookieService {
    private static final Duration REFRESH_TOKEN_MAX_AGE = Duration.ofDays(7);

    private final String cookieName;
    private final String cookiePath;
    private final boolean secure;
    private final String sameSite;

    public RefreshTokenCookieService(
            @Value("${security.refresh-token.cookie-name:refreshToken}") String cookieName,
            @Value("${security.refresh-token.cookie-path:/api/auth}") String cookiePath,
            @Value("${security.refresh-token.cookie-secure:false}") boolean secure,
            @Value("${security.refresh-token.cookie-same-site:Lax}") String sameSite
    ) {
        this.cookieName = cookieName == null ? "" : cookieName.trim();
        this.cookiePath = cookiePath == null ? "" : cookiePath.trim();
        this.secure = secure;
        this.sameSite = sameSite == null ? "Lax" : sameSite.trim();
    }

    @PostConstruct
    void validate() {
        if (cookieName.isBlank()) {
            throw new IllegalStateException("security.refresh-token.cookie-name must not be blank");
        }
        if (cookiePath.isBlank()) {
            throw new IllegalStateException("security.refresh-token.cookie-path must not be blank");
        }
        if ("none".equalsIgnoreCase(sameSite) && !secure) {
            throw new IllegalStateException("SameSite=None refresh token cookies require secure=true");
        }
    }

    public String cookieName() {
        return cookieName;
    }

    public ResponseCookie create(String refreshToken) {
        return base(refreshToken).maxAge(REFRESH_TOKEN_MAX_AGE).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(cookieName, value == null ? "" : value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(normalizeSameSite())
                .path(cookiePath);
    }

    private String normalizeSameSite() {
        String normalized = sameSite.toLowerCase(Locale.ROOT);
        if ("strict".equals(normalized)) {
            return "Strict";
        }
        if ("none".equals(normalized)) {
            return "None";
        }
        return "Lax";
    }
}
