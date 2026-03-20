package com.sw103302.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class PendingTwoFactorStore {
    private static final Logger log = LoggerFactory.getLogger(PendingTwoFactorStore.class);
    private static final String REDIS_KEY_PREFIX = "2fa:pending:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final Map<String, PendingEntry> fallbackStore = new ConcurrentHashMap<>();
    private final AtomicBoolean fallbackWarningLogged = new AtomicBoolean(false);

    @Autowired
    public PendingTwoFactorStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this(redisTemplateProvider.getIfAvailable(), Clock.systemUTC());
    }

    PendingTwoFactorStore(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    public void store(String pendingToken, String email, Duration ttl) {
        requireText(pendingToken, "pendingToken");
        requireText(email, "email");
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }

        if (!storeInRedis(pendingToken, email, ttl)) {
            fallbackStore.put(pendingToken, new PendingEntry(email, clock.instant().plus(ttl)));
        }
        cleanupExpiredFallbackEntries();
    }

    public String get(String pendingToken) {
        requireText(pendingToken, "pendingToken");
        cleanupExpiredFallbackEntries();

        String redisValue = readFromRedis(pendingToken);
        if (redisValue != null && !redisValue.isBlank()) {
            return redisValue;
        }

        PendingEntry entry = fallbackStore.get(pendingToken);
        if (entry == null) {
            return null;
        }
        if (!entry.isActive(clock.instant())) {
            fallbackStore.remove(pendingToken);
            return null;
        }
        return entry.email();
    }

    public void delete(String pendingToken) {
        if (pendingToken == null || pendingToken.isBlank()) {
            return;
        }

        deleteFromRedis(pendingToken);
        fallbackStore.remove(pendingToken);
        cleanupExpiredFallbackEntries();
    }

    private boolean storeInRedis(String pendingToken, String email, Duration ttl) {
        if (redisTemplate == null) {
            return false;
        }

        try {
            redisTemplate.opsForValue().set(redisKey(pendingToken), email, ttl);
            return true;
        } catch (RuntimeException e) {
            logRedisFallback("store", e);
            return false;
        }
    }

    private String readFromRedis(String pendingToken) {
        if (redisTemplate == null) {
            return null;
        }

        try {
            return redisTemplate.opsForValue().get(redisKey(pendingToken));
        } catch (RuntimeException e) {
            logRedisFallback("read", e);
            return null;
        }
    }

    private void deleteFromRedis(String pendingToken) {
        if (redisTemplate == null) {
            return;
        }

        try {
            redisTemplate.delete(redisKey(pendingToken));
        } catch (RuntimeException e) {
            logRedisFallback("delete", e);
        }
    }

    private void cleanupExpiredFallbackEntries() {
        Instant now = clock.instant();
        fallbackStore.entrySet().removeIf(entry -> !entry.getValue().isActive(now));
    }

    private void logRedisFallback(String action, RuntimeException e) {
        if (fallbackWarningLogged.compareAndSet(false, true)) {
            log.warn(
                    "Redis unavailable while handling pending 2FA tokens ({}). Falling back to in-memory store on this instance.",
                    action,
                    e
            );
            return;
        }
        log.debug("Redis unavailable while handling pending 2FA tokens ({}). Using in-memory fallback.", action, e);
    }

    private String redisKey(String pendingToken) {
        return REDIS_KEY_PREFIX + pendingToken;
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private record PendingEntry(String email, Instant expiresAt) {
        boolean isActive(Instant now) {
            return expiresAt.isAfter(now);
        }
    }
}
