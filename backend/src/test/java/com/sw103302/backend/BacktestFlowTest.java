package com.sw103302.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.AiBacktestRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BacktestFlowTest {
    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mvc;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectMapper om;

    @MockitoBean
    AiClient aiClient;

    @Test
    void register_login_backtest_history() throws Exception {
        // AI backtest response mock
        String aiBacktestResponse = """
            {
              "summary": {
                "totalReturn": 0.25,
                "maxDrawdown": -0.12,
                "sharpe": 1.5,
                "cagr": 0.18
              },
              "trades": [
                {"date": "2023-01-15", "action": "BUY", "price": 150.0},
                {"date": "2023-06-20", "action": "SELL", "price": 180.0}
              ],
              "equity_curve": [
                {"date": "2023-01-01", "value": 1000000},
                {"date": "2023-12-31", "value": 1250000}
              ]
            }
            """;

        doReturn(aiBacktestResponse).when(aiClient).backtest(any(AiBacktestRequest.class));

        // 1) Register a new user
        String regBody = """
            {"email":"backtest@example.com","password":"password1234"}
            """;

        String regRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn().getResponse().getContentAsString();

        String token = om.readTree(regRes).get("accessToken").asText();

        // 2) Run backtest
        String backtestBody = """
            {
              "ticker": "AAPL",
              "market": "US",
              "strategy": "SMA_CROSS",
              "start": "2023-01-01",
              "end": "2023-12-31",
              "initialCapital": 1000000,
              "feeBps": 5
            }
            """;

        mvc.perform(post("/api/backtest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(backtestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").exists())
                .andExpect(jsonPath("$.result.summary.totalReturn").value(0.25))
                .andExpect(jsonPath("$.result.summary.sharpe").value(1.5));

        // 3) Get backtest history
        var historyResult = mvc.perform(get("/api/backtest/history?limit=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String historyJson = historyResult.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(historyJson).contains("AAPL");
        org.assertj.core.api.Assertions.assertThat(historyJson).contains("SMA_CROSS");
    }

    @Test
    void backtest_withoutAuth_shouldReturn4xx() throws Exception {
        String backtestBody = """
            {"ticker": "AAPL", "market": "US", "strategy": "SMA_CROSS"}
            """;

        mvc.perform(post("/api/backtest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(backtestBody))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void backtestHistory_withoutAuth_shouldReturn4xx() throws Exception {
        mvc.perform(get("/api/backtest/history?limit=10"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void backtest_withDefaults_shouldUseDefaultValues() throws Exception {
        // AI response mock
        String aiBacktestResponse = """
            {"summary": {"totalReturn": 0.1}}
            """;

        doReturn(aiBacktestResponse).when(aiClient).backtest(any(AiBacktestRequest.class));

        // Register and get token
        String regBody = """
            {"email":"backtest2@example.com","password":"password1234"}
            """;

        String regRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = om.readTree(regRes).get("accessToken").asText();

        // Backtest with minimal params (defaults should apply)
        String backtestBody = """
            {"ticker": "GOOGL", "market": "US", "strategy": "RSI_STRATEGY"}
            """;

        mvc.perform(post("/api/backtest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(backtestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").exists());
    }

    @Test
    void backtestHistoryPaged_shouldReturnPagedResults() throws Exception {
        // AI response mock
        String aiBacktestResponse = """
            {"summary": {"totalReturn": 0.15, "sharpe": 1.2}}
            """;

        doReturn(aiBacktestResponse).when(aiClient).backtest(any(AiBacktestRequest.class));

        // Register with unique email
        String regBody = """
            {"email":"backtest-paged-test@example.com","password":"password1234"}
            """;

        String regRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = om.readTree(regRes).get("accessToken").asText();

        // Run a backtest
        String backtestBody = """
            {"ticker": "MSFT", "market": "US", "strategy": "MACD_STRATEGY"}
            """;

        mvc.perform(post("/api/backtest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(backtestBody))
                .andExpect(status().isOk());

        // Get paged history - endpoint is /api/backtest/history with page params
        var pagedResult = mvc.perform(get("/api/backtest/history?page=0&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String pagedJson = pagedResult.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(pagedJson).contains("MSFT");
        org.assertj.core.api.Assertions.assertThat(pagedJson).contains("items");
        org.assertj.core.api.Assertions.assertThat(pagedJson).contains("page");
    }
}
