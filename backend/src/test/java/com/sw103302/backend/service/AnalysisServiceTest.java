package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.component.InFlightDeduplicator;
import com.sw103302.backend.dto.AnalysisRequest;
import com.sw103302.backend.dto.AnalysisResponse;
import com.sw103302.backend.dto.AnalysisRunSummary;
import com.sw103302.backend.entity.AnalysisRun;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.AnalysisRunRepository;
import com.sw103302.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.sw103302.backend.util.SecurityUtil;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private AiClient aiClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AnalysisRunRepository analysisRunRepository;

    @Mock
    private InFlightDeduplicator deduplicator;

    private ObjectMapper objectMapper;
    private AnalysisService analysisService;
    private MockedStatic<SecurityUtil> securityUtilMock;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        analysisService = new AnalysisService(aiClient, objectMapper, userRepository, analysisRunRepository, deduplicator);
        securityUtilMock = mockStatic(SecurityUtil.class);

        lenient().when(deduplicator.execute(anyString(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            java.util.function.Supplier<AnalysisResponse> supplier = (java.util.function.Supplier<AnalysisResponse>) inv.getArgument(1);
            return supplier.get();
        });
    }

    @AfterEach
    void tearDown() {
        securityUtilMock.close();
    }

    @Test
    void analyzeAndSave_withValidRequest_shouldReturnAnalysisResponse() {
        // Given
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");
        AnalysisRequest request = new AnalysisRequest("AAPL", "US", 252, "balanced");
        String aiResponse = """
            {"decision": {"action": "BUY", "confidence": 0.85}, "analysis": "Strong buy signal"}
            """;

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(aiClient.analyze(any())).thenReturn(aiResponse);
        when(analysisRunRepository.save(any(AnalysisRun.class))).thenAnswer(inv -> {
            AnalysisRun run = inv.getArgument(0);
            // Simulate ID assignment
            return run;
        });

        // When
        AnalysisResponse response = analysisService.analyzeAndSave(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.result()).isNotNull();

        ArgumentCaptor<AnalysisRun> runCaptor = ArgumentCaptor.forClass(AnalysisRun.class);
        verify(analysisRunRepository).save(runCaptor.capture());
        AnalysisRun savedRun = runCaptor.getValue();
        assertThat(savedRun.getTicker()).isEqualTo("AAPL");
        assertThat(savedRun.getMarket()).isEqualTo("US");
        assertThat(savedRun.getAction()).isEqualTo("BUY");
        assertThat(savedRun.getConfidence()).isEqualTo(0.85);
    }

    @Test
    void analyzeAndSave_withUnauthenticatedUser_shouldThrowException() {
        // Given
        AnalysisRequest request = new AnalysisRequest("AAPL", "US", 252, "balanced");
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(null);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenThrow(new IllegalStateException("User is not authenticated"));

        // When & Then
        assertThatThrownBy(() -> analysisService.analyzeAndSave(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authenticated");
    }

    @Test
    void analyzeAndSave_withNonExistentUser_shouldThrowException() {
        // Given
        String email = "nonexistent@example.com";
        AnalysisRequest request = new AnalysisRequest("AAPL", "US", 252, "balanced");

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> analysisService.analyzeAndSave(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("user not found");
    }

    @Test
    void analyzeAndSave_withInvalidAiResponse_shouldThrowException() {
        // Given
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");
        AnalysisRequest request = new AnalysisRequest("AAPL", "US", 252, "balanced");

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(aiClient.analyze(any())).thenReturn("not valid json {{{");

        // When & Then
        assertThatThrownBy(() -> analysisService.analyzeAndSave(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI response is not valid json");
    }

    @Test
    void analyzeAndSave_withDefaultValues_shouldUseDefaults() {
        // Given
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");
        AnalysisRequest request = new AnalysisRequest("AAPL", "US", null, null); // null defaults
        String aiResponse = """
            {"decision": {"action": "HOLD", "confidence": 0.5}}
            """;

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(aiClient.analyze(any())).thenReturn(aiResponse);
        when(analysisRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        analysisService.analyzeAndSave(request);

        // Then
        verify(aiClient).analyze(argThat(aiReq ->
                aiReq.horizonDays() == 252 && "balanced".equals(aiReq.riskProfile())
        ));
    }

    @Test
    void analyzeAndSave_shouldUseInFlightDeduplication() {
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");
        AnalysisRequest request = new AnalysisRequest("AAPL", "US", 252, "balanced");
        String aiResponse = """
            {"decision": {"action": "BUY", "confidence": 0.9}}
            """;

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(aiClient.analyze(any())).thenReturn(aiResponse);
        when(analysisRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        analysisService.analyzeAndSave(request);

        verify(deduplicator).execute(startsWith("analysis:user@example.com:AAPL:US:252:balanced"), any());
    }

    @Test
    void myHistory_shouldReturnUserAnalysisHistory() {
        // Given
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");

        AnalysisRun run1 = new AnalysisRun(user, "AAPL", "US", "{}", "{}", "BUY", 0.8);
        AnalysisRun run2 = new AnalysisRun(user, "GOOGL", "US", "{}", "{}", "HOLD", 0.5);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(analysisRunRepository.findByUser_EmailOrderByCreatedAtDesc(eq(email), any(Pageable.class)))
                .thenReturn(List.of(run1, run2));

        // When
        List<AnalysisRunSummary> history = analysisService.myHistory(10);

        // Then
        assertThat(history).hasSize(2);
        assertThat(history.get(0).ticker()).isEqualTo("AAPL");
        assertThat(history.get(0).action()).isEqualTo("BUY");
        assertThat(history.get(1).ticker()).isEqualTo("GOOGL");
    }

    @Test
    void myHistory_withUnauthenticatedUser_shouldThrowException() {
        // Given
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(null);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenThrow(new IllegalStateException("User is not authenticated"));

        // When & Then
        assertThatThrownBy(() -> analysisService.myHistory(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authenticated");
    }

    @Test
    void myHistory_shouldLimitResults() {
        // Given
        String email = "user@example.com";
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(analysisRunRepository.findByUser_EmailOrderByCreatedAtDesc(anyString(), any(Pageable.class)))
                .thenReturn(List.of());

        // When
        analysisService.myHistory(5);

        // Then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(analysisRunRepository).findByUser_EmailOrderByCreatedAtDesc(eq(email), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void myHistory_shouldClampLimitToValidRange() {
        // Given
        String email = "user@example.com";
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(analysisRunRepository.findByUser_EmailOrderByCreatedAtDesc(anyString(), any(Pageable.class)))
                .thenReturn(List.of());

        // When - request with limit > 100
        analysisService.myHistory(500);

        // Then - should be clamped to 100
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(analysisRunRepository).findByUser_EmailOrderByCreatedAtDesc(eq(email), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void myHistoryPage_shouldReturnPaginatedResults() {
        // Given
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");
        AnalysisRun run = new AnalysisRun(user, "AAPL", "US", "{}", "{}", "BUY", 0.8);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(analysisRunRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(run), PageRequest.of(0, 10), 1));

        // When
        var response = analysisService.myHistoryPage(0, 10, "createdAt,desc", null, null, null, null, null);

        // Then
        assertThat(response.items()).hasSize(1);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void myRunDetail_shouldReturnResponseJson() {
        // Given
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");
        String responseJson = """
            {"decision": {"action": "BUY", "confidence": 0.85}}
            """;
        AnalysisRun run = new AnalysisRun(user, "AAPL", "US", "{}", responseJson, "BUY", 0.85);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(analysisRunRepository.findByIdAndUser_Email(1L, email)).thenReturn(Optional.of(run));

        // When
        String result = analysisService.myRunDetail(1L);

        // Then
        assertThat(result).isEqualTo(responseJson);
    }

    @Test
    void myRunDetail_withNonExistentRun_shouldThrowException() {
        // Given
        String email = "user@example.com";
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(analysisRunRepository.findByIdAndUser_Email(999L, email)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> analysisService.myRunDetail(999L))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not found");
    }
}
