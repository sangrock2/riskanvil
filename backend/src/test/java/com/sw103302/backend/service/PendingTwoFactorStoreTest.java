package com.sw103302.backend.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PendingTwoFactorStoreTest {

    @Test
    void storeAndGet_shouldUseInMemoryFallbackWhenRedisIsUnavailable() {
        PendingTwoFactorStore store = new PendingTwoFactorStore(
                null,
                Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC)
        );

        store.store("pending-123", "user@example.com", Duration.ofMinutes(5));

        assertThat(store.get("pending-123")).isEqualTo("user@example.com");

        store.delete("pending-123");

        assertThat(store.get("pending-123")).isNull();
    }
}
