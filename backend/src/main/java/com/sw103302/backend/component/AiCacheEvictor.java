package com.sw103302.backend.component;

import com.sw103302.backend.dto.InsightRequest;
import com.sw103302.backend.util.AiCacheKey;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
public class AiCacheEvictor {
    private final CacheManager cacheManager;

    public AiCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictInsights(InsightRequest req, boolean test) {
        evict("ai_insights", AiCacheKey.insights(req, test));
    }

    public void evictReport(InsightRequest req, boolean test, boolean web) {
        evict("ai_report", AiCacheKey.report(req, test, web));
    }

    private void evict(String cacheName, String key) {
        Cache c = cacheManager.getCache(cacheName);
        if (c != null) c.evict(key);
    }
}
