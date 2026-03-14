package com.sw103302.backend.component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class AuthMetricsRecorder {
    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> flowCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> flowTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> stageTimers = new ConcurrentHashMap<>();

    public AuthMetricsRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordFlow(String operation, String outcome, long durationMs) {
        String timerKey = operation + "|" + outcome;

        flowCounters.computeIfAbsent(timerKey, unused ->
                Counter.builder("auth_flow_total")
                        .description("Total authentication flow executions by operation and outcome")
                        .tag("operation", operation)
                        .tag("outcome", outcome)
                        .register(registry)
        ).increment();

        flowTimers.computeIfAbsent(timerKey, unused ->
                Timer.builder("auth_flow_latency")
                        .description("End-to-end authentication flow latency")
                        .tag("operation", operation)
                        .tag("outcome", outcome)
                        .register(registry)
        ).record(Math.max(durationMs, 0L), TimeUnit.MILLISECONDS);
    }

    public void recordStage(String operation, String stage, long durationMs) {
        String timerKey = operation + "|" + stage;
        stageTimers.computeIfAbsent(timerKey, unused ->
                Timer.builder("auth_flow_stage_latency")
                        .description("Authentication flow stage latency")
                        .tag("operation", operation)
                        .tag("stage", stage)
                        .register(registry)
        ).record(Math.max(durationMs, 0L), TimeUnit.MILLISECONDS);
    }
}
