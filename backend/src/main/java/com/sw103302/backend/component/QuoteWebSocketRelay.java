package com.sw103302.backend.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 서비스의 WebSocket에 연결하여 실시간 시세를 프론트엔드에 릴레이
 * AI Service (ws://ai:8000/ws/quotes) → Backend → Frontend
 */
@Component
@ConditionalOnProperty(name = "quote.websocket.relay.enabled", havingValue = "true", matchIfMissing = true)
public class QuoteWebSocketRelay extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(QuoteWebSocketRelay.class);
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";
    private final ObjectMapper objectMapper;
    private final QuoteWebSocketHandler broadcastHandler;

    @Value("${ai.baseUrl:http://localhost:8000}")
    private String aiBaseUrl;

    @Value("${ai.internal.service-token}")
    private String aiInternalServiceToken;

    private WebSocketSession aiSession;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "quote-ws-relay");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running = true;
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private volatile int reconnectAttempts = 0;

    public QuoteWebSocketRelay(QuoteWebSocketHandler broadcastHandler, ObjectMapper objectMapper) {
        this.broadcastHandler = broadcastHandler;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        reconnectScheduler.shutdownNow();
        closeAiSession();
    }

    private synchronized void connectToAi() {
        if (!running) {
            return;
        }
        if (!hasActiveSubscriptions()) {
            return;
        }
        WebSocketSession current = aiSession;
        if (current != null && current.isOpen()) {
            return;
        }

        try {
            String wsUrl = aiBaseUrl.replace("http://", "ws://").replace("https://", "wss://") + "/ws/quotes";
            log.info("Connecting to AI WebSocket: {}", wsUrl);

            StandardWebSocketClient client = new StandardWebSocketClient();
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.set(INTERNAL_SERVICE_TOKEN_HEADER, aiInternalServiceToken);
            aiSession = client.execute(this, headers, URI.create(wsUrl)).get(10, TimeUnit.SECONDS);
            log.info("Connected to AI WebSocket relay");
            reconnectAttempts = 0;
            reconnectScheduled.set(false);

            // Re-sync current frontend subscriptions after reconnect.
            for (String ticker : broadcastHandler.getSubscribedTickersSnapshot()) {
                sendSubscriptionCommand("subscribe", ticker);
            }
        } catch (Exception e) {
            log.warn("Failed to connect to AI WebSocket: {}. Scheduling reconnect...", e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running) {
            return;
        }
        if (!hasActiveSubscriptions()) {
            reconnectAttempts = 0;
            reconnectScheduled.set(false);
            return;
        }
        if (!reconnectScheduled.compareAndSet(false, true)) {
            return;
        }

        long delayMs = nextReconnectDelayMillis();
        log.info("Scheduling AI WebSocket reconnect in {}ms", delayMs);
        reconnectScheduler.schedule(() -> {
            reconnectScheduled.set(false);
            if (running) {
                connectToAi();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode json = objectMapper.readTree(message.getPayload());
            String ticker = json.has("ticker") ? json.get("ticker").asText() : null;
            if (ticker != null) {
                broadcastHandler.broadcastQuote(ticker, message.getPayload());
            }
        } catch (Exception e) {
            log.warn("Failed to relay quote: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        log.warn("AI WebSocket disconnected ({}). Reconnecting...", status);
        closeAiSession();
        scheduleReconnect();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("AI WebSocket transport error: {}", exception.getMessage());
        closeAiSession();
        scheduleReconnect();
    }

    private void closeAiSession() {
        if (aiSession != null && aiSession.isOpen()) {
            try {
                aiSession.close();
            } catch (Exception ignored) {}
        }
        aiSession = null;
    }

    @EventListener
    public void handleSubscriptionEvent(QuoteSubscriptionEvent event) {
        if ("subscribe".equals(event.action())) {
            ensureConnectedAsync();
        }
        sendSubscriptionCommand(event.action(), event.ticker());
        if ("unsubscribe".equals(event.action()) && !hasActiveSubscriptions()) {
            log.info("No active quote subscriptions remain. Closing AI WebSocket relay.");
            reconnectAttempts = 0;
            closeAiSession();
            reconnectScheduled.set(false);
        }
    }

    private void sendSubscriptionCommand(String action, String ticker) {
        WebSocketSession session = aiSession;
        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(java.util.Map.of(
                    "action", action,
                    "ticker", ticker
            ));
            session.sendMessage(new TextMessage(payload));
        } catch (Exception e) {
            log.warn("Failed to forward {} command for {} to AI relay: {}", action, ticker, e.getMessage());
        }
    }

    private void ensureConnectedAsync() {
        if (!running) {
            return;
        }
        WebSocketSession current = aiSession;
        if (current != null && current.isOpen()) {
            return;
        }
        if (!reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        reconnectScheduler.execute(() -> {
            reconnectScheduled.set(false);
            if (running) {
                connectToAi();
            }
        });
    }

    private boolean hasActiveSubscriptions() {
        return !broadcastHandler.getSubscribedTickersSnapshot().isEmpty();
    }

    private long nextReconnectDelayMillis() {
        long base = Math.min((long) Math.pow(2, Math.min(reconnectAttempts, 5)) * 1000L, 30_000L);
        reconnectAttempts = Math.min(reconnectAttempts + 1, 6);
        long jitter = ThreadLocalRandom.current().nextLong(0, 750);
        return base + jitter;
    }
}
