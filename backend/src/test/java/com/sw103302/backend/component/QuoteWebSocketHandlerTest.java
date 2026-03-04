package com.sw103302.backend.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteWebSocketHandlerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private WebSocketSession sessionA;

    @Mock
    private WebSocketSession sessionB;

    private QuoteWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new QuoteWebSocketHandler(eventPublisher, new ObjectMapper());
        when(sessionA.getId()).thenReturn("session-a");
        when(sessionB.getId()).thenReturn("session-b");
        when(sessionA.isOpen()).thenReturn(true);
        when(sessionB.isOpen()).thenReturn(true);
    }

    @Test
    void broadcastQuote_shouldSendOnlyToSubscribedSessions() throws Exception {
        handler.afterConnectionEstablished(sessionA);
        handler.afterConnectionEstablished(sessionB);

        handler.handleTextMessage(sessionA, new TextMessage("{\"action\":\"subscribe\",\"ticker\":\"AAPL\"}"));
        handler.handleTextMessage(sessionB, new TextMessage("{\"action\":\"subscribe\",\"ticker\":\"MSFT\"}"));

        handler.broadcastQuote("AAPL", "{\"ticker\":\"AAPL\",\"price\":180.0}");

        verify(sessionA, times(1)).sendMessage(any(TextMessage.class));
        verify(sessionB, never()).sendMessage(any(TextMessage.class));
        verify(eventPublisher).publishEvent(QuoteSubscriptionEvent.subscribe("AAPL"));
        verify(eventPublisher).publishEvent(QuoteSubscriptionEvent.subscribe("MSFT"));
    }

    @Test
    void connectionClose_shouldUnsubscribeOnlyWhenLastSubscriberLeaves() throws Exception {
        handler.afterConnectionEstablished(sessionA);
        handler.afterConnectionEstablished(sessionB);

        handler.handleTextMessage(sessionA, new TextMessage("{\"action\":\"subscribe\",\"ticker\":\"AAPL\"}"));
        handler.handleTextMessage(sessionB, new TextMessage("{\"action\":\"subscribe\",\"ticker\":\"AAPL\"}"));
        verify(eventPublisher, times(1)).publishEvent(QuoteSubscriptionEvent.subscribe("AAPL"));

        clearInvocations(eventPublisher);

        handler.afterConnectionClosed(sessionA, CloseStatus.NORMAL);
        verify(eventPublisher, never()).publishEvent(QuoteSubscriptionEvent.unsubscribe("AAPL"));
        assertThat(handler.getSubscribedTickersSnapshot()).contains("AAPL");

        handler.afterConnectionClosed(sessionB, CloseStatus.NORMAL);
        verify(eventPublisher, times(1)).publishEvent(QuoteSubscriptionEvent.unsubscribe("AAPL"));
        assertThat(handler.getSubscribedTickersSnapshot()).doesNotContain("AAPL");
    }

    @Test
    void pingMessage_shouldReplyWithPong() throws Exception {
        handler.afterConnectionEstablished(sessionA);

        handler.handleTextMessage(sessionA, new TextMessage("{\"action\":\"ping\"}"));

        verify(sessionA, times(1)).sendMessage(any(TextMessage.class));
    }
}
