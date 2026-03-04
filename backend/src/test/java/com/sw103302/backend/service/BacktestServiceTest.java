package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.BacktestRequest;
import com.sw103302.backend.dto.BacktestResponse;
import com.sw103302.backend.dto.BacktestRunSummary;
import com.sw103302.backend.entity.BacktestRun;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.BacktestRunRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.SecurityUtil;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BacktestServiceTest {

    @Mock
    private AiClient aiClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BacktestRunRepository backtestRunRepository;

    private ObjectMapper objectMapper;
    private BacktestService backtestService;
    private MockedStatic<SecurityUtil> securityUtilMock;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        backtestService = new BacktestService(aiClient, objectMapper, userRepository, backtestRunRepository);
        securityUtilMock = mockStatic(SecurityUtil.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilMock.close();
    }

    @Test
    void runAndSave_withValidRequest_shouldReturnBacktestResponse() {
        // Given
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");
        BacktestRequest request = new BacktestRequest("AAPL", "US", "SMA_CROSS", "2023-01-01", "2023-12-31", 1000000.0, 5.0);
        String aiResponse = """
            {
              "summary": {
                "totalReturn": 0.15,
                "maxDrawdown": -0.08,
                "sharpe": 1.2,
                "cagr": 0.12
              },
              "trades": []
            }
            """;

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(aiClient.backtest(any())).thenReturn(aiResponse);
        when(backtestRunRepository.save(any(BacktestRun.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        BacktestResponse response = backtestService.runAndSave(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.result()).isNotNull();

        ArgumentCaptor<BacktestRun> runCaptor = ArgumentCaptor.forClass(BacktestRun.class);
        verify(backtestRunRepository).save(runCaptor.capture());
        BacktestRun savedRun = runCaptor.getValue();
        assertThat(savedRun.getTicker()).isEqualTo("AAPL");
        assertThat(savedRun.getMarket()).isEqualTo("US");
        assertThat(savedRun.getStrategy()).isEqualTo("SMA_CROSS");
        assertThat(savedRun.getTotalReturn()).isEqualTo(0.15);
        assertThat(savedRun.getMaxDrawdown()).isEqualTo(-0.08);
        assertThat(savedRun.getSharpe()).isEqualTo(1.2);
        assertThat(savedRun.getCagr()).isEqualTo(0.12);
    }

    @Test
    void runAndSave_withUnauthenticatedUser_shouldThrowException() {
        // Given
        BacktestRequest request = new BacktestRequest("AAPL", "US", "SMA_CROSS", null, null, null, null);
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(null);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenThrow(new IllegalStateException("User is not authenticated"));

        // When & Then
        assertThatThrownBy(() -> backtestService.runAndSave(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authenticated");
    }

    @Test
    void runAndSave_withNonExistentUser_shouldThrowException() {
        // Given
        String email = "nonexistent@example.com";
        BacktestRequest request = new BacktestRequest("AAPL", "US", "SMA_CROSS", null, null, null, null);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> backtestService.runAndSave(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("user not found");
    }

    @Test
    void runAndSave_withDefaultValues_shouldUseDefaults() {
        // Given
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");
        BacktestRequest request = new BacktestRequest("AAPL", "US", "SMA_CROSS", null, null, null, null);
        String aiResponse = """
            {"summary": {"totalReturn": 0.1}}
            """;

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(aiClient.backtest(any())).thenReturn(aiResponse);
        when(backtestRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        backtestService.runAndSave(request);

        // Then
        verify(aiClient).backtest(argThat(aiReq ->
                aiReq.initialCapital() == 1_000_000.0 && aiReq.feeBps() == 5.0
        ));
    }

    @Test
    void runAndSave_withInvalidJson_shouldThrowException() {
        // Given
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");
        BacktestRequest request = new BacktestRequest("AAPL", "US", "SMA_CROSS", null, null, null, null);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(aiClient.backtest(any())).thenReturn("invalid json {{{");

        // When & Then
        assertThatThrownBy(() -> backtestService.runAndSave(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stored json parse failed");
    }

    @Test
    void runAndSave_withMissingSummaryFields_shouldSaveWithNulls() {
        // Given
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");
        BacktestRequest request = new BacktestRequest("AAPL", "US", "SMA_CROSS", null, null, null, null);
        String aiResponse = """
            {"summary": {}}
            """;

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(aiClient.backtest(any())).thenReturn(aiResponse);
        when(backtestRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        backtestService.runAndSave(request);

        // Then - should not throw, saves with null metrics
        ArgumentCaptor<BacktestRun> runCaptor = ArgumentCaptor.forClass(BacktestRun.class);
        verify(backtestRunRepository).save(runCaptor.capture());
        BacktestRun savedRun = runCaptor.getValue();
        assertThat(savedRun.getTotalReturn()).isNull();
        assertThat(savedRun.getMaxDrawdown()).isNull();
    }

    @Test
    void myHistory_shouldReturnBacktestHistory() {
        // Given
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");
        BacktestRun run1 = new BacktestRun(user, "AAPL", "US", "SMA_CROSS", "{}", "{}", 0.15, -0.08, 1.2, 0.12);
        BacktestRun run2 = new BacktestRun(user, "GOOGL", "US", "RSI", "{}", "{}", 0.10, -0.05, 0.9, 0.08);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(backtestRunRepository.findByUser_EmailOrderByCreatedAtDesc(eq(email), any(Pageable.class)))
                .thenReturn(List.of(run1, run2));

        // When
        List<BacktestRunSummary> history = backtestService.myHistory(10);

        // Then
        assertThat(history).hasSize(2);
        assertThat(history.get(0).ticker()).isEqualTo("AAPL");
        assertThat(history.get(0).strategy()).isEqualTo("SMA_CROSS");
        assertThat(history.get(0).totalReturn()).isEqualTo(0.15);
        assertThat(history.get(1).ticker()).isEqualTo("GOOGL");
    }

    @Test
    void myHistory_withUnauthenticatedUser_shouldThrowException() {
        // Given
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(null);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenThrow(new IllegalStateException("User is not authenticated"));

        // When & Then
        assertThatThrownBy(() -> backtestService.myHistory(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authenticated");
    }

    @Test
    void myBacktestHistoryPage_shouldReturnPaginatedResults() {
        // Given
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");
        BacktestRun run = new BacktestRun(user, "AAPL", "US", "SMA_CROSS", "{}", "{}", 0.15, -0.08, 1.2, 0.12);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(backtestRunRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(run), PageRequest.of(0, 10), 1));

        // When - pass actual filter values to avoid null Specification issue
        var response = backtestService.myBacktestHistoryPage(0, 10, "createdAt,desc", "AAPL", "US", "SMA_CROSS");

        // Then
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).ticker()).isEqualTo("AAPL");
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void myDetail_shouldReturnResponseJson() {
        // Given
        String email = "user@example.com";
        User user = new User(email, "hash", "ROLE_USER");
        String responseJson = """
            {"summary": {"totalReturn": 0.15}}
            """;
        BacktestRun run = new BacktestRun(user, "AAPL", "US", "SMA_CROSS", "{}", responseJson, 0.15, null, null, null);

        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(backtestRunRepository.findByIdAndUser_Email(1L, email)).thenReturn(Optional.of(run));

        // When
        String result = backtestService.myDetail(1L);

        // Then
        assertThat(result).isEqualTo(responseJson);
    }

    @Test
    void myDetail_withNonExistentRun_shouldThrowException() {
        // Given
        String email = "user@example.com";
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(backtestRunRepository.findByIdAndUser_Email(999L, email)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> backtestService.myDetail(999L))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void myHistory_shouldClampLimitToValidRange() {
        // Given
        String email = "user@example.com";
        securityUtilMock.when(SecurityUtil::currentEmail).thenReturn(email);
        securityUtilMock.when(SecurityUtil::requireCurrentEmail).thenReturn(email);
        when(backtestRunRepository.findByUser_EmailOrderByCreatedAtDesc(anyString(), any(Pageable.class)))
                .thenReturn(List.of());

        // When - request with limit > 100
        backtestService.myHistory(500);

        // Then - should be clamped to 100
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(backtestRunRepository).findByUser_EmailOrderByCreatedAtDesc(eq(email), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }
}
