package com.sw103302.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpSecretCryptoServiceTest {

    @Test
    void encryptThenDecrypt_shouldRoundTrip() {
        TotpSecretCryptoService service = new TotpSecretCryptoService(
                "test-only-totp-encryption-key-minimum-32-bytes"
        );
        ReflectionTestUtils.setField(service, "environment", new MockEnvironment().withProperty("spring.profiles.active", "test"));
        service.init();

        String encrypted = service.encrypt("ABCDEFGHIJKLMNOPQRSTUVWX12345678");

        assertThat(encrypted).startsWith("enc:");
        assertThat(encrypted).isNotEqualTo("ABCDEFGHIJKLMNOPQRSTUVWX12345678");
        assertThat(service.decrypt(encrypted)).isEqualTo("ABCDEFGHIJKLMNOPQRSTUVWX12345678");
    }

    @Test
    void decrypt_withLegacyPlaintext_shouldReturnAsIs() {
        TotpSecretCryptoService service = new TotpSecretCryptoService(
                "test-only-totp-encryption-key-minimum-32-bytes"
        );
        ReflectionTestUtils.setField(service, "environment", new MockEnvironment().withProperty("spring.profiles.active", "test"));
        service.init();

        assertThat(service.decrypt("LEGACYPLAINTEXTSECRET1234567890"))
                .isEqualTo("LEGACYPLAINTEXTSECRET1234567890");
    }

    @Test
    void init_withDevelopmentKeyInProdProfile_shouldFailFast() {
        TotpSecretCryptoService service = new TotpSecretCryptoService(
                "dev-only-totp-encryption-key-change-me-before-prod-32bytes"
        );
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "postgres");
        ReflectionTestUtils.setField(service, "environment", env);

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Default TOTP encryption key is not allowed");
    }
}
