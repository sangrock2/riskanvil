package com.sw103302.backend.config;

import com.sw103302.backend.component.QuoteWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final QuoteWebSocketHandler quoteWebSocketHandler;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(QuoteWebSocketHandler quoteWebSocketHandler,
                           @Value("${app.cors.allowed-origin-patterns:http://localhost:3000,http://localhost:5173,http://127.0.0.1:5173}")
                           String allowedOriginPatternsCsv) {
        this.quoteWebSocketHandler = quoteWebSocketHandler;
        this.allowedOriginPatterns = Arrays.stream(allowedOriginPatternsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(quoteWebSocketHandler, "/ws/quotes")
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }
}
