package com.sw103302.backend.component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class CacheMetricsRecorder {
    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public CacheMetricsRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String cacheName, String operation, String outcome) {
        String key = cacheName + "|" + operation + "|" + outcome;
        counters.computeIfAbsent(key, unused ->
                Counter.builder("app_cache_events_total")
                        .description("Application cache events by cache name, operation, and outcome")
                        .tag("cache_name", cacheName)
                        .tag("operation", operation)
                        .tag("outcome", outcome)
                        .register(registry)
        ).increment();
    }
}
