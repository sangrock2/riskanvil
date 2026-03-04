package com.sw103302.backend.component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiCallMetrics {
    private final MeterRegistry registry;

    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> ok = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> fail = new ConcurrentHashMap<>();

    public AiCallMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer timer(String op) {
        return timers.computeIfAbsent(op, k ->
                Timer.builder("ai_call_latency_seconds")
                        .description("AI call latency")
                        .tag("op", k)
                        .register(registry)
        );
    }

    public Counter ok(String op) {
        return ok.computeIfAbsent(op, k ->
                Counter.builder("ai_call_total")
                        .description("AI call total")
                        .tag("op", k)
                        .tag("result", "ok")
                        .register(registry)
        );
    }

    public Counter fail(String op) {
        return fail.computeIfAbsent(op, k ->
                Counter.builder("ai_call_total")
                        .description("AI call total")
                        .tag("op", k)
                        .tag("result", "fail")
                        .register(registry)
        );
    }
}
