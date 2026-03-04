package com.sw103302.backend.component;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class AiHealthIndicator implements HealthIndicator {
    private final WebClient aiWebClient;

    public AiHealthIndicator(WebClient aiWebClient) {
        this.aiWebClient = aiWebClient;
    }

    @Override
    public Health health() {
        try {
            String body = aiWebClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(2))
                    .block();

            return Health.up()
                    .withDetail("ai", "UP")
                    .withDetail("body", body == null ? "" : body)
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("ai", "DOWN")
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .build();
        }
    }
}
