package com.sw103302.backend.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiResilienceConfig {
    @Bean
    public CircuitBreaker aiCircuitBreaker() {
        CircuitBreakerConfig cfg = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(8))
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        return CircuitBreaker.of("ai", cfg);
    }

    @Bean
    public Retry aiRetry() {
        RetryConfig cfg = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(350))
                .build();

        return Retry.of("ai", cfg);
    }
}
