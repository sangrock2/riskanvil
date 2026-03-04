package com.sw103302.backend.component;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class InMemoryInFlightDeduplicator implements InFlightDeduplicator {
    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(String key, Supplier<T> supplier) {
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> existing = inFlight.putIfAbsent(key, mine);

        if (existing != null) {
            try {
                return (T) existing.join();
            } catch (CompletionException ce) {
                Throwable cause = (ce.getCause() != null) ? ce.getCause() : ce;
                if (cause instanceof RuntimeException re) throw re;
                throw new RuntimeException(cause);
            }
        }

        try {
            T out = supplier.get();
            mine.complete(out);
            return out;
        } catch (Exception e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }
}
