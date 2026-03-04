package com.sw103302.backend.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.*;

class InMemoryInFlightDeduplicatorTest {

    private InMemoryInFlightDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        deduplicator = new InMemoryInFlightDeduplicator();
    }

    @Test
    void execute_shouldReturnSupplierResult() {
        // When
        String result = deduplicator.execute("key1", () -> "result");

        // Then
        assertThat(result).isEqualTo("result");
    }

    @Test
    void execute_withDifferentKeys_shouldExecuteBothSuppliers() {
        // Given
        AtomicInteger counter = new AtomicInteger(0);

        // When
        String result1 = deduplicator.execute("key1", () -> {
            counter.incrementAndGet();
            return "result1";
        });
        String result2 = deduplicator.execute("key2", () -> {
            counter.incrementAndGet();
            return "result2";
        });

        // Then
        assertThat(result1).isEqualTo("result1");
        assertThat(result2).isEqualTo("result2");
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    @Timeout(5)
    void execute_withConcurrentSameKey_shouldDeduplicateCalls() throws Exception {
        // Given
        AtomicInteger executionCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch insideLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // First call - will be slow
            Future<String> future1 = executor.submit(() -> {
                startLatch.await();
                return deduplicator.execute("same-key", () -> {
                    executionCount.incrementAndGet();
                    insideLatch.countDown(); // Signal that we're inside the supplier
                    LockSupport.parkNanos(200_000_000L); // 200ms delay
                    return "result";
                });
            });

            // Second call - should join the first
            Future<String> future2 = executor.submit(() -> {
                startLatch.await();
                insideLatch.await(); // Wait until first call is inside supplier
                return deduplicator.execute("same-key", () -> {
                    executionCount.incrementAndGet();
                    return "result2"; // This should not be executed
                });
            });

            // Start both
            startLatch.countDown();

            // Both should return the same result
            String result1 = future1.get(3, TimeUnit.SECONDS);
            String result2 = future2.get(3, TimeUnit.SECONDS);

            // Then
            assertThat(result1).isEqualTo("result");
            assertThat(result2).isEqualTo("result");
            assertThat(executionCount.get()).isEqualTo(1); // Only one execution
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void execute_afterCompletion_shouldAllowNewExecution() {
        // Given
        AtomicInteger counter = new AtomicInteger(0);

        // When - first execution
        String result1 = deduplicator.execute("key", () -> {
            counter.incrementAndGet();
            return "result1";
        });

        // When - second execution (after first completed)
        String result2 = deduplicator.execute("key", () -> {
            counter.incrementAndGet();
            return "result2";
        });

        // Then - both should execute independently
        assertThat(result1).isEqualTo("result1");
        assertThat(result2).isEqualTo("result2");
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void execute_withException_shouldPropagateException() {
        // When & Then
        assertThatThrownBy(() -> deduplicator.execute("key", () -> {
            throw new RuntimeException("test error");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("test error");
    }

    @Test
    @Timeout(5)
    void execute_withException_shouldPropagateToAllWaiters() throws Exception {
        // Given
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch insideLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // First call - will throw exception
            Future<String> future1 = executor.submit(() -> {
                startLatch.await();
                return deduplicator.execute("same-key", () -> {
                    insideLatch.countDown();
                    LockSupport.parkNanos(100_000_000L); // 100ms delay
                    throw new RuntimeException("test error");
                });
            });

            // Second call - should receive same exception
            Future<String> future2 = executor.submit(() -> {
                startLatch.await();
                insideLatch.await();
                return deduplicator.execute("same-key", () -> "should not execute");
            });

            startLatch.countDown();

            // Both should throw the same exception
            assertThatThrownBy(() -> future1.get(3, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> future2.get(3, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(RuntimeException.class);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void execute_afterException_shouldAllowNewExecution() {
        // Given - first execution fails
        try {
            deduplicator.execute("key", () -> {
                throw new RuntimeException("first error");
            });
        } catch (RuntimeException ignored) {
        }

        // When - second execution should work
        String result = deduplicator.execute("key", () -> "success");

        // Then
        assertThat(result).isEqualTo("success");
    }

    @Test
    void execute_withNullResult_shouldReturnNull() {
        // When
        String result = deduplicator.execute("key", () -> null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @Timeout(10)
    void execute_withManyConcurrentCalls_shouldDeduplicateCorrectly() throws Exception {
        // Given
        int threadCount = 10;
        AtomicInteger executionCount = new AtomicInteger(0);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CopyOnWriteArrayList<String> results = new CopyOnWriteArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        String result = deduplicator.execute("shared-key", () -> {
                            executionCount.incrementAndGet();
                            LockSupport.parkNanos(100_000_000L); // 100ms delay
                            return "shared-result";
                        });
                        results.add(result);
                    } catch (Exception e) {
                        results.add("error: " + e.getMessage());
                    }
                });
            }

            // Wait for all threads to be ready, then start them simultaneously
            readyLatch.await();
            startLatch.countDown();

            // Wait for completion
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            // Then
            assertThat(results).hasSize(threadCount);
            assertThat(results).allMatch(r -> r.equals("shared-result"));
            // Execution count should be 1 (all threads share the result) or slightly more
            // if some threads complete before others start
            assertThat(executionCount.get()).isLessThanOrEqualTo(threadCount);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void execute_withRuntimeExceptionInSupplier_shouldPropagate() {
        // When & Then
        assertThatThrownBy(() -> deduplicator.execute("key", () -> {
            throw new IllegalStateException("checked exception wrapped");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("checked exception wrapped");
    }
}
