package com.sw103302.backend.component;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthMetricsRecorderTest {

    @Test
    void shouldRecordFlowAndStageMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuthMetricsRecorder recorder = new AuthMetricsRecorder(registry);

        recorder.recordFlow("login", "success", 125);
        recorder.recordStage("login", "lookup_user", 15);

        assertThat(registry.get("auth_flow_total")
                .tag("operation", "login")
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1.0);

        assertThat(registry.get("auth_flow_latency")
                .tag("operation", "login")
                .tag("outcome", "success")
                .timer()
                .count()).isEqualTo(1L);

        assertThat(registry.get("auth_flow_stage_latency")
                .tag("operation", "login")
                .tag("stage", "lookup_user")
                .timer()
                .count()).isEqualTo(1L);
    }
}
