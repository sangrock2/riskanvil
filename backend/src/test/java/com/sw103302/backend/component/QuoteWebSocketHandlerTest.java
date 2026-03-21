package com.sw103302.backend.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
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
    private JwtService jwtService;

    @Mock
    private Claims claims;

    @Mock
    private WebSocketSession sessionA;

    @Mock
    private WebSocketSession sessionB;

    private QuoteWebSocketHandler handler;
    private ScheduledExecutorService authTimeoutScheduler;

    @BeforeEach
    void setUp() {
        authTimeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "quote-ws-auth-timeout-test");
            thread.setDaemon(true);
            return thread;
        });
        handler = new QuoteWebSocketHandler(eventPublisher, new ObjectMapper(), jwtService, 30, authTimeoutScheduler);
        lenient().when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(60)));
    }

    @AfterEach
    void tearDown() {
        handler.shutdownAuthTimeoutScheduler();
    }

    @Test
    void broadcastQuote_shouldSendOnlyToSubscribedSessions() throws Exception {
        when(sessionA.getId()).thenReturn("session-a");
        when(sessionB.getId()).thenReturn("session-b");
        lenient().when(sessionA.isOpen()).thenReturn(true);
        when(sessionB.isOpen()).thenReturn(true);
        when(jwtService.parseClaims("valid.jwt")).thenReturn(claims);

        handler.afterConnectionEstablished(sessionA);
        handler.afterConnectionEstablished(sessionB);

        handler.handleTextMessage(sessionA, new TextMessage("{\"action\":\"auth\",\"token\":\"valid.jwt\"}"));
        handler.handleTextMessage(sessionB, new TextMessage("{\"action\":\"auth\",\"token\":\"valid.jwt\"}"));
        clearInvocations(sessionA, sessionB);
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
        when(sessionA.getId()).thenReturn("session-a");
        when(sessionB.getId()).thenReturn("session-b");
        when(sessionB.isOpen()).thenReturn(true);
        when(jwtService.parseClaims("valid.jwt")).thenReturn(claims);

        handler.afterConnectionEstablished(sessionA);
        handler.afterConnectionEstablished(sessionB);

        handler.handleTextMessage(sessionA, new TextMessage("{\"action\":\"auth\",\"token\":\"valid.jwt\"}"));
        handler.handleTextMessage(sessionB, new TextMessage("{\"action\":\"auth\",\"token\":\"valid.jwt\"}"));
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
    void firstSubscriptionEvent_shouldBePublishedAfterTickerIsTracked() throws Exception {
        when(sessionA.getId()).thenReturn("session-a");
        when(jwtService.parseClaims("valid.jwt")).thenReturn(claims);
        doAnswer(invocation -> {
            assertThat(handler.getSubscribedTickersSnapshot()).contains("AAPL");
            return null;
        }).when(eventPublisher).publishEvent(eq(QuoteSubscriptionEvent.subscribe("AAPL")));

        handler.afterConnectionEstablished(sessionA);
        handler.handleTextMessage(sessionA, new TextMessage("{\"action\":\"auth\",\"token\":\"valid.jwt\"}"));

        handler.handleTextMessage(sessionA, new TextMessage("{\"action\":\"subscribe\",\"ticker\":\"AAPL\"}"));

        verify(eventPublisher, times(1)).publishEvent(QuoteSubscriptionEvent.subscribe("AAPL"));
        assertThat(handler.getSubscribedTickersSnapshot()).contains("AAPL");
    }

    @Test
    void pingMessage_shouldReplyWithPong() throws Exception {
        when(sessionA.getId()).thenReturn("session-a");
        when(sessionA.isOpen()).thenReturn(true);
        when(jwtService.parseClaims("valid.jwt")).thenReturn(claims);

        handler.afterConnectionEstablished(sessionA);
        handler.handleTextMessage(sessionA, new TextMessage("{\"action\":\"auth\",\"token\":\"valid.jwt\"}"));

        handler.handleTextMessage(sessionA, new TextMessage("{\"action\":\"ping\"}"));

        verify(sessionA, times(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void unauthenticatedSubscribe_shouldBeRejected() throws Exception {
        when(sessionA.getId()).thenReturn("session-a");
        when(sessionA.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(sessionA);
        handler.handleTextMessage(sessionA, new TextMessage("{\"action\":\"subscribe\",\"ticker\":\"AAPL\"}"));

        verify(eventPublisher, never()).publishEvent(any());
        verify(sessionA, times(1)).close(any(CloseStatus.class));
    }

    @Test
    void idleUnauthenticatedConnection_shouldBeClosedAfterTimeout() throws Exception {
        when(sessionA.getId()).thenReturn("session-a");
        when(sessionA.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(sessionA);
        Thread.sleep(120);

        verify(sessionA, times(1)).close(any(CloseStatus.class));
        assertThat(handler.getActiveSessionCount()).isZero();
    }

    @Test
    void authenticatedSession_shouldNotBeClosedByTimeout() throws Exception {
        when(sessionA.getId()).thenReturn("session-a");
        when(sessionA.isOpen()).thenReturn(true);
        when(jwtService.parseClaims("valid.jwt")).thenReturn(claims);

        handler.afterConnectionEstablished(sessionA);
        handler.handleTextMessage(sessionA, new TextMessage("{\"action\":\"auth\",\"token\":\"valid.jwt\"}"));
        Thread.sleep(120);

        verify(sessionA, never()).close(any(CloseStatus.class));
    }

    @Test
    void authenticatedSession_shouldBeClosedWhenJwtExpires() throws Exception {
        when(sessionA.getId()).thenReturn("session-a");
        when(sessionA.isOpen()).thenReturn(true);
        when(jwtService.parseClaims("short-lived.jwt")).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusMillis(50)));

        handler.afterConnectionEstablished(sessionA);
        handler.handleTextMessage(sessionA, new TextMessage("{\"action\":\"auth\",\"token\":\"short-lived.jwt\"}"));
        Thread.sleep(160);

        verify(sessionA, times(1)).close(any(CloseStatus.class));
    }

    @Test
    void unauthenticatedConnectionsFromSameIp_shouldBeLimited() throws Exception {
        QuoteWebSocketHandler limitedHandler = new QuoteWebSocketHandler(
                eventPublisher,
                new ObjectMapper(),
                jwtService,
                1_000,
                10,
                1,
                authTimeoutScheduler
        );
        when(sessionA.getId()).thenReturn("session-a");
        when(sessionB.getId()).thenReturn("session-b");
        when(sessionB.isOpen()).thenReturn(true);
        when(sessionA.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        when(sessionB.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8081));

        limitedHandler.afterConnectionEstablished(sessionA);
        limitedHandler.afterConnectionEstablished(sessionB);

        verify(sessionB, times(1)).close(any(CloseStatus.class));
    }
}
