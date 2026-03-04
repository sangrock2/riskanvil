package com.sw103302.backend.component;

import java.util.function.Supplier;

public interface InFlightDeduplicator {
    <T> T execute(String key, Supplier<T> supplier);
}
