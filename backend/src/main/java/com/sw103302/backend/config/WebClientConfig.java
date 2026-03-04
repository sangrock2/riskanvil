package com.sw103302.backend.config;

import com.sw103302.backend.component.RequestIdFilter;
import io.netty.channel.ChannelOption;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;


import java.time.Duration;

@Configuration
public class WebClientConfig {
    @Bean
    public WebClient aiWebClient(@Value("${ai.baseUrl}") String baseUrl) {
        int timeoutSec = 180;

        ConnectionProvider provider = ConnectionProvider.builder("ai-pool")
                .maxConnections(100)
                .pendingAcquireMaxCount(500)
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .responseTimeout(Duration.ofSeconds(timeoutSec));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(requestIdForwardFilter())
                .build();
    }

    private ExchangeFilterFunction requestIdForwardFilter() {
        return (request, next) -> {
            // ✅ 이미 X-Request-Id가 있으면 그대로 유지
            String existing = request.headers().getFirst(RequestIdFilter.HEADER);
            if (existing != null && !existing.isBlank()) {
                return next.exchange(request);
            }

            // ✅ MDC에서 requestId 꺼내서 전파
            String rid = MDC.get(RequestIdFilter.MDC_KEY);
            if (rid == null || rid.isBlank()) {
                return next.exchange(request);
            }

            ClientRequest mutated = ClientRequest.from(request)
                    .headers(h -> h.set(RequestIdFilter.HEADER, rid))
                    .build();

            return next.exchange(mutated);
        };
    }
}
