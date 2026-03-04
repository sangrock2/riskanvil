package com.sw103302.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Set required properties via reflection (simulating @Value injection)
        ReflectionTestUtils.setField(jwtService, "secret", "test-secret-change-me-test-secret-change-me-123456");
        ReflectionTestUtils.setField(jwtService, "expMinutes", 60L);
        // Trigger @PostConstruct manually
        ReflectionTestUtils.invokeMethod(jwtService, "init");
    }

    @Test
    void createAccessToken_shouldReturnValidJwt() {
        String email = "user@example.com";
        String role = "ROLE_USER";

        String token = jwtService.createAccessToken(email, role);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    void parseClaims_shouldReturnCorrectSubjectAndRole() {
        String email = "user@example.com";
        String role = "ROLE_USER";

        String token = jwtService.createAccessToken(email, role);
        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo(email);
        assertThat(claims.get("role", String.class)).isEqualTo(role);
    }

    @Test
    void parseClaims_shouldContainIssuedAtAndExpiration() {
        String token = jwtService.createAccessToken("user@example.com", "ROLE_USER");
        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void parseClaims_withInvalidToken_shouldThrowException() {
        String invalidToken = "invalid.token.here";

        assertThatThrownBy(() -> jwtService.parseClaims(invalidToken))
                .isInstanceOf(Exception.class);
    }

    @Test
    void parseClaims_withTamperedToken_shouldThrowSignatureException() {
        String token = jwtService.createAccessToken("user@example.com", "ROLE_USER");
        // Tamper with the token by changing a character in the signature
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> jwtService.parseClaims(tamperedToken))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void parseClaims_withExpiredToken_shouldThrowExpiredJwtException() {
        // Create a service with very short expiration
        JwtService shortExpService = new JwtService();
        ReflectionTestUtils.setField(shortExpService, "secret", "test-secret-change-me-test-secret-change-me-123456");
        ReflectionTestUtils.setField(shortExpService, "expMinutes", 0L); // 0 minutes = expired immediately
        ReflectionTestUtils.invokeMethod(shortExpService, "init");

        String token = shortExpService.createAccessToken("user@example.com", "ROLE_USER");

        // Token should be expired (or very close to it)
        assertThatThrownBy(() -> shortExpService.parseClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void createAccessToken_withDifferentRoles_shouldCreateDistinctTokens() {
        String email = "user@example.com";

        String userToken = jwtService.createAccessToken(email, "ROLE_USER");
        String adminToken = jwtService.createAccessToken(email, "ROLE_ADMIN");

        assertThat(userToken).isNotEqualTo(adminToken);

        Claims userClaims = jwtService.parseClaims(userToken);
        Claims adminClaims = jwtService.parseClaims(adminToken);

        assertThat(userClaims.get("role")).isEqualTo("ROLE_USER");
        assertThat(adminClaims.get("role")).isEqualTo("ROLE_ADMIN");
    }
}
