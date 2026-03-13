package com.sw103302.backend.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.StringJoiner;

@Service
public class TokenHashService {
    private static final Logger log = LoggerFactory.getLogger(TokenHashService.class);
    private final String pepper;

    @Autowired(required = false)
    private Environment environment;

    public TokenHashService(@Value("${security.refresh-token.pepper:}") String pepper) {
        this.pepper = pepper == null ? "" : pepper;
    }

    @PostConstruct
    void init() {
        if (pepper.isBlank()) {
            if (isProdLikeEnvironment()) {
                throw new IllegalStateException(
                    "SECURITY ERROR: REFRESH_TOKEN_PEPPER is empty in active profiles [" +
                    activeProfilesSummary() + "]. Set REFRESH_TOKEN_PEPPER to a strong random value."
                );
            }

            log.warn("REFRESH_TOKEN_PEPPER is empty for active profiles [{}]. Using unpeppered refresh token hashes.", activeProfilesSummary());
        }
    }

    public String hash(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token is required");
        }

        String payload = token + ":" + pepper;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
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

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
