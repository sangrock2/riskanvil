package com.sw103302.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.AiAnalyzeRequest;
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
class AnalysisFlowTest {
    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mvc;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectMapper om;

    @MockitoBean
    AiClient aiClient;

    @Test
    void register_login_analyze_history() throws Exception {
        // AI response mock - AiClient returns String, not JsonNode
        String aiResult = """
        {
          "decision": { "action": "HOLD", "confidence": 0.5 }
        }
        """;

        doReturn(aiResult).when(aiClient).analyze(any(AiAnalyzeRequest.class));

        // 1) register - use unique email to avoid collision with other tests
        String regBody = """
      {"email":"analysis-flow-test@example.com","password":"password1234"}
    """;

        String regRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn().getResponse().getContentAsString();

        String token = om.readTree(regRes).get("accessToken").asText();

        // 2) analyze
        String analyzeBody = """
      {"ticker":"AAPL","market":"US","horizonDays":252,"riskProfile":"moderate"}
    """;

        mvc.perform(post("/api/analysis")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analyzeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").exists())
                .andExpect(jsonPath("$.result.decision.action").value("HOLD"));

        // 3) history
        var historyResult = mvc.perform(get("/api/analysis/history?limit=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String historyJson = historyResult.getResponse().getContentAsString();
        System.out.println("History response: " + historyJson);

        // Check that we got at least one result with the expected ticker
        org.assertj.core.api.Assertions.assertThat(historyJson).contains("AAPL");
    }
}
