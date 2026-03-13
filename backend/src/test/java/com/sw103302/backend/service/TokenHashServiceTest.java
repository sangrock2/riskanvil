package com.sw103302.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenHashServiceTest {

    @Test
    void hash_withPepper_shouldBeDeterministic() {
        TokenHashService service = new TokenHashService("pepper-123");
        ReflectionTestUtils.setField(service, "environment", new MockEnvironment().withProperty("spring.profiles.active", "test"));
        service.init();

        String hash1 = service.hash("refresh-token-value");
        String hash2 = service.hash("refresh-token-value");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    void init_withBlankPepperInProdProfile_shouldFailFast() {
        TokenHashService service = new TokenHashService("   ");
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "postgres");
        ReflectionTestUtils.setField(service, "environment", env);

        assertThatThrownBy(service::init)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("REFRESH_TOKEN_PEPPER is empty");
    }
}
