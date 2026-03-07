package com.sw103302.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheErrorHandlingConfig {
    private static final Logger log = LoggerFactory.getLogger(CacheErrorHandlingConfig.class);

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                warn("get", exception, cache, key);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                warn("put", exception, cache, key);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                warn("evict", exception, cache, key);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                warn("clear", exception, cache, null);
            }

            private void warn(String op, RuntimeException exception, Cache cache, Object key) {
                String cacheName = cache == null ? "unknown" : cache.getName();
                String keyText = key == null ? "-" : String.valueOf(key);
                log.warn("Cache {} failed (cache={}, key={}): {}", op, cacheName, keyText, exception.toString());
            }
        };
    }
}
