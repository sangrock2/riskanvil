package com.sw103302.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {
    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expMinutes:60}")
    private long expMinutes;

    private SecretKey key;

    @PostConstruct
    void init() {
        // Enforce minimum 256-bit (32 bytes) secret for HMAC-SHA256 security
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                String.format(
                    "SECURITY ERROR: JWT secret must be at least 32 bytes (256 bits) for HMAC-SHA256. " +
                    "Current length: %d bytes. Set JWT_SECRET environment variable with a strong secret.",
                    secretBytes.length
                )
            );
        }

        // Additional check: warn if using default development secret in production
        if (secret.contains("dev-only") || secret.contains("change-in-production")) {
            System.err.println("WARNING: Using default development JWT secret. " +
                "Set JWT_SECRET environment variable to a strong random secret for production.");
        }

        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String createAccessToken(String email, String role) {
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiration = Date.from(now.plus(expMinutes, ChronoUnit.MINUTES));

        // JJWT 예시: subject/issuedAt/expiration/claim/signWith/compact :contentReference[oaicite:3]{index=3}
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        // verifyWith + parseSignedClaims 패턴 :contentReference[oaicite:4]{index=4}
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
