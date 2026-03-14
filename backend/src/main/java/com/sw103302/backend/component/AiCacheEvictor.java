package com.sw103302.backend.component;

import com.sw103302.backend.dto.InsightRequest;
import com.sw103302.backend.util.AiCacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
public class AiCacheEvictor {
    private static final Logger log = LoggerFactory.getLogger(AiCacheEvictor.class);
    private final CacheManager cacheManager;
    private final CacheMetricsRecorder cacheMetricsRecorder;

    public AiCacheEvictor(CacheManager cacheManager, CacheMetricsRecorder cacheMetricsRecorder) {
        this.cacheManager = cacheManager;
        this.cacheMetricsRecorder = cacheMetricsRecorder;
    }

    public void evictInsights(InsightRequest req, boolean test) {
        evict("ai_insights", AiCacheKey.insights(req, test));
    }

    public void evictReport(InsightRequest req, boolean test, boolean web) {
        evict("ai_report", AiCacheKey.report(req, test, web));
    }

    private void evict(String cacheName, String key) {
        try {
            Cache c = cacheManager.getCache(cacheName);
            if (c != null) {
                c.evict(key);
                cacheMetricsRecorder.record(cacheName, "evict", "success");
            } else {
                cacheMetricsRecorder.record(cacheName, "evict", "cache_missing");
            }
        } catch (RuntimeException e) {
            // Cache eviction failure must not break the API flow.
            cacheMetricsRecorder.record(cacheName, "evict", "error");
            log.warn("Cache evict failed (cache={}, key={}): {}", cacheName, key, e.toString());
        }
    }
}
