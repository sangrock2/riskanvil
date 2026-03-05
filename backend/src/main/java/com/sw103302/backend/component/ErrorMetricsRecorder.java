package com.sw103302.backend.component;

import com.sw103302.backend.util.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ErrorMetricsRecorder {
    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public ErrorMetricsRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(HttpStatus status, ErrorCode code) {
        String statusTag = String.valueOf(status.value());
        String codeTag = code.value();
        String key = statusTag + "|" + codeTag;

        Counter counter = counters.computeIfAbsent(key, unused ->
                Counter.builder("app_error_total")
                        .description("Total API error responses by status and error code")
                        .tag("status", statusTag)
                        .tag("error_code", codeTag)
                        .register(registry)
        );
        counter.increment();
    }
}

