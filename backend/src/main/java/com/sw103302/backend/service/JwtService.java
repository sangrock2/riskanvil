package com.sw103302.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.StringJoiner;

@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expMinutes:60}")
    private long expMinutes;

    @Autowired(required = false)
    private Environment environment;

    private SecretKey key;

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "SECURITY ERROR: JWT secret is empty. Set JWT_SECRET environment variable " +
                "with a strong random secret before starting the application."
            );
        }

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

        if (usesDevelopmentSecret(secret) && isProdLikeEnvironment()) {
            throw new IllegalStateException(
                "SECURITY ERROR: Default development JWT secret is not allowed in active profiles [" +
                activeProfilesSummary() + "]. Set JWT_SECRET to a strong random secret."
            );
        }

        if (usesDevelopmentSecret(secret)) {
            log.warn("Using development JWT secret for active profiles [{}].", activeProfilesSummary());
        }

        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    private boolean isProdLikeEnvironment() {
        if (environment == null) {
            return false;
        }

        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return false;
        }

        for (String profile : activeProfiles) {
            String normalized = profile.toLowerCase(Locale.ROOT);
            if ("dev".equals(normalized) || "test".equals(normalized)) {
                return false;
            }
        }
        return true;
    }

    private String activeProfilesSummary() {
        if (environment == null || environment.getActiveProfiles().length == 0) {
            return "default";
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (String profile : environment.getActiveProfiles()) {
            joiner.add(profile);
        }
        return joiner.toString();
    }

    private boolean usesDevelopmentSecret(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("dev-only")
                || normalized.contains("change-in-production")
                || normalized.contains("local-development");
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
