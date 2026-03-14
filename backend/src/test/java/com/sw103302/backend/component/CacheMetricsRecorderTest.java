package com.sw103302.backend.component;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheMetricsRecorderTest {

    @Test
    void shouldRecordCacheEvents() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CacheMetricsRecorder recorder = new CacheMetricsRecorder(registry);

        recorder.record("market_insights_db", "lookup", "hit");
        recorder.record("market_insights_db", "lookup", "hit");

        assertThat(registry.get("app_cache_events_total")
                .tag("cache_name", "market_insights_db")
                .tag("operation", "lookup")
                .tag("outcome", "hit")
                .counter()
                .count()).isEqualTo(2.0);
    }
}
