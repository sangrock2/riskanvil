package com.sw103302.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    @Test
    void simpleCacheManager_shouldIncludeAiOhlcCache() {
        CacheConfig cacheConfig = new CacheConfig();
        CacheManager cacheManager = cacheConfig.simpleCacheManager();

        assertThat(cacheManager.getCache("ai_ohlc")).isNotNull();
    }
}
