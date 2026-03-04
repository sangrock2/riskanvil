package com.sw103302.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {
    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    @Primary
    @ConditionalOnBean(RedisConnectionFactory.class)
    public CacheManager redisCacheManager(RedisConnectionFactory cf) {
        log.info("Using Redis cache manager");
        // 값이 "String(JSON)" 위주라서 String serializer로 통일 (DB에 저장하는 raw JSON과 동일하게 유지)
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();

        // 자주 호출되는 API 위주 TTL
        perCache.put("ai_quote", base.entryTtl(Duration.ofSeconds(30)));
        perCache.put("ai_prices", base.entryTtl(Duration.ofMinutes(5)));
        perCache.put("ai_ohlc", base.entryTtl(Duration.ofMinutes(5)));
        perCache.put("ai_fundamentals", base.entryTtl(Duration.ofHours(6)));
        perCache.put("ai_news", base.entryTtl(Duration.ofMinutes(10)));
        perCache.put("ai_symbol_search", base.entryTtl(Duration.ofHours(24)));

        // (선택) AI insights/report도 Redis에서 짧게 캐시 (다중 사용자/스케일아웃 대비)
        perCache.put("ai_insights", base.entryTtl(Duration.ofMinutes(10)));
        perCache.put("ai_report", base.entryTtl(Duration.ofMinutes(30)));

        // Portfolio prices - 1 minute cache for real-time data
        perCache.put("prices", base.entryTtl(Duration.ofMinutes(1)));
        perCache.put("price", base.entryTtl(Duration.ofMinutes(1)));

        return RedisCacheManager.builder(cf)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager simpleCacheManager() {
        log.warn("Redis not available, using simple in-memory cache manager");
        // Fallback to simple in-memory cache when Redis is not available
        return new ConcurrentMapCacheManager(
                "ai_quote", "ai_prices", "ai_ohlc", "ai_fundamentals", "ai_news", "ai_symbol_search",
                "ai_insights", "ai_report", "prices", "price"
        );
    }
}
