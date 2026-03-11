package com.sw103302.backend.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

/**
 * 실시간 시세 WebSocket 핸들러
 * - 프론트엔드 ↔ Backend 간 WebSocket 연결 관리
 * - AI 서비스에서 받은 시세를 프론트에 릴레이
 */
@Component
public class QuoteWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(QuoteWebSocketHandler.class);
    private static final int MAX_SUBSCRIPTIONS_PER_SESSION = 30;
    private static final int MAX_INBOUND_MESSAGE_CHARS = 512;
    private static final Pattern TICKER_PATTERN = Pattern.compile("^[A-Z0-9._-]{1,15}$");
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    // 연결된 세션들
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    // 세션별 구독 티커
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();
    // 티커별 구독 세션 역인덱스 (브로드캐스트 O(N) 스캔 방지)
    private final Map<String, Set<WebSocketSession>> tickerSubscribers = new ConcurrentHashMap<>();

    public QuoteWebSocketHandler(ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        sessionSubscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            if (message.getPayloadLength() > MAX_INBOUND_MESSAGE_CHARS) {
                sendError(session, "message_too_large");
                return;
            }
            JsonNode json = objectMapper.readTree(message.getPayload());
            String action = json.has("action") ? json.get("action").asText() : "";
            String ticker = json.has("ticker")
                    ? json.get("ticker").asText().toUpperCase(Locale.ROOT).trim()
                    : "";

            if ("ping".equals(action)) {
                session.sendMessage(new TextMessage("{\"type\":\"pong\",\"ts\":" + System.currentTimeMillis() + "}"));
                return;
            }

            if (ticker.isEmpty()) return;
            if (!TICKER_PATTERN.matcher(ticker).matches()) {
                sendError(session, "invalid_ticker");
                return;
            }

            Set<String> subs = sessionSubscriptions.computeIfAbsent(session.getId(), k -> ConcurrentHashMap.newKeySet());

            if ("subscribe".equals(action)) {
                if (!subs.contains(ticker) && subs.size() >= MAX_SUBSCRIPTIONS_PER_SESSION) {
                    sendError(session, "subscription_limit_exceeded");
                    return;
                }
                boolean alreadySubscribedGlobally = isTickerSubscribedByAnySession(ticker);
                boolean added = subs.add(ticker);
                if (added && !alreadySubscribedGlobally) {
                    eventPublisher.publishEvent(QuoteSubscriptionEvent.subscribe(ticker));
                }
                if (added) {
                    tickerSubscribers.computeIfAbsent(ticker, k -> ConcurrentHashMap.newKeySet()).add(session);
                    log.debug("Session {} subscribed to {}", session.getId(), ticker);
                }
            } else if ("unsubscribe".equals(action)) {
                boolean removed = subs.remove(ticker);
                if (removed) {
                    removeTickerSubscriber(ticker, session);
                    if (!isTickerSubscribedByAnySession(ticker)) {
                        eventPublisher.publishEvent(QuoteSubscriptionEvent.unsubscribe(ticker));
                    }
                    log.debug("Session {} unsubscribed from {}", session.getId(), ticker);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse WebSocket message: {}", e.getMessage());
            sendError(session, "invalid_message");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        cleanupSessionSubscriptions(session);
        log.info("WebSocket disconnected: {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error for {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
        cleanupSessionSubscriptions(session);
    }

    /**
     * AI 서비스에서 받은 시세 업데이트를 구독 중인 클라이언트에 브로드캐스트
     */
    public void broadcastQuote(String ticker, String jsonPayload) {
        Set<WebSocketSession> subscribers = tickerSubscribers.getOrDefault(ticker, Set.of());
        for (WebSocketSession session : subscribers) {
            if (!session.isOpen()) continue;
            try {
                session.sendMessage(new TextMessage(jsonPayload));
            } catch (IOException e) {
                log.warn("Failed to send to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    /**
     * 연결된 세션 수 반환
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    public Set<String> getSubscribedTickersSnapshot() {
        return Set.copyOf(tickerSubscribers.keySet());
    }

    private boolean isTickerSubscribedByAnySession(String ticker) {
        Set<WebSocketSession> subscribers = tickerSubscribers.get(ticker);
        return subscribers != null && !subscribers.isEmpty();
    }

    private void cleanupSessionSubscriptions(WebSocketSession session) {
        Set<String> removedTickers = sessionSubscriptions.remove(session.getId());
        if (removedTickers == null || removedTickers.isEmpty()) {
            return;
        }
        for (String ticker : removedTickers) {
            removeTickerSubscriber(ticker, session);
            if (!isTickerSubscribedByAnySession(ticker)) {
                eventPublisher.publishEvent(QuoteSubscriptionEvent.unsubscribe(ticker));
            }
        }
    }

    private void removeTickerSubscriber(String ticker, WebSocketSession session) {
        Set<WebSocketSession> subscribers = tickerSubscribers.get(ticker);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(session);
        if (subscribers.isEmpty()) {
            tickerSubscribers.remove(ticker, subscribers);
        }
    }

    private void sendError(WebSocketSession session, String code) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"code\":\"" + code + "\"}"));
        } catch (IOException ignored) {
        }
    }
}
