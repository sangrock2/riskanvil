package com.sw103302.backend.component;

import org.springframework.stereotype.Component;

@Component
public class SseShutdownHook implements AutoCloseable {
    private final SseEmitterRegistry registry;

    public SseShutdownHook(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void close() {
        // Spring Context 종료 시 호출됨
        registry.closeAll("application_stopping");
    }
}
