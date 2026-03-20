package com.sw103302.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenCookieServiceTest {

    @Test
    void create_shouldProduceHttpOnlyCookie() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(
                "refreshToken",
                "/api/auth",
                true,
                "None"
        );
        service.validate();

        String cookie = service.create("token-123").toString();

        assertThat(cookie).contains("refreshToken=token-123");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("Secure");
        assertThat(cookie).contains("SameSite=None");
        assertThat(cookie).contains("Path=/api/auth");
    }

    @Test
    void validate_shouldRejectSameSiteNoneWithoutSecure() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(
                "refreshToken",
                "/api/auth",
                false,
                "None"
        );

        assertThatThrownBy(service::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SameSite=None");
    }
}
