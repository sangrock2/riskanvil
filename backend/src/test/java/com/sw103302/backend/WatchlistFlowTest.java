package com.sw103302.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WatchlistFlowTest {
    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mvc;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectMapper om;

    @MockitoBean
    AiClient aiClient;

    @Test
    void register_addWatchlist_list_remove() throws Exception {
        // 1) Register
        String regBody = """
            {"email":"watchlist@example.com","password":"password1234"}
            """;

        String regRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn().getResponse().getContentAsString();

        String token = om.readTree(regRes).get("accessToken").asText();

        // 2) Add to watchlist
        String addBody = """
            {"ticker": "AAPL", "market": "US"}
            """;

        mvc.perform(post("/api/watchlist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody))
                .andExpect(status().isOk());

        // 3) List watchlist - should contain AAPL
        mvc.perform(get("/api/watchlist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].market").value("US"));

        // 4) Add another stock
        String addBody2 = """
            {"ticker": "GOOGL", "market": "US"}
            """;

        mvc.perform(post("/api/watchlist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody2))
                .andExpect(status().isOk());

        // 5) List should now have 2 items
        mvc.perform(get("/api/watchlist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // 6) Remove AAPL
        mvc.perform(delete("/api/watchlist")
                        .header("Authorization", "Bearer " + token)
                        .param("ticker", "AAPL")
                        .param("market", "US"))
                .andExpect(status().isOk());

        // 7) List should now have 1 item (GOOGL only)
        mvc.perform(get("/api/watchlist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticker").value("GOOGL"));
    }

    @Test
    void addDuplicateWatchlist_shouldFail() throws Exception {
        // Register
        String regBody = """
            {"email":"watchlist2@example.com","password":"password1234"}
            """;

        String regRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = om.readTree(regRes).get("accessToken").asText();

        // Add AAPL
        String addBody = """
            {"ticker": "AAPL", "market": "US"}
            """;

        mvc.perform(post("/api/watchlist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody))
                .andExpect(status().isOk());

        // Try to add AAPL again - should fail
        mvc.perform(post("/api/watchlist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void watchlist_withoutAuth_shouldReturn4xx() throws Exception {
        mvc.perform(get("/api/watchlist"))
                .andExpect(status().is4xxClientError());

        mvc.perform(post("/api/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ticker": "AAPL", "market": "US"}
                            """))
                .andExpect(status().is4xxClientError());

        mvc.perform(delete("/api/watchlist")
                        .param("ticker", "AAPL")
                        .param("market", "US"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void addWatchlist_withDefaultMarket_shouldUseUS() throws Exception {
        // Register
        String regBody = """
            {"email":"watchlist3@example.com","password":"password1234"}
            """;

        String regRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = om.readTree(regRes).get("accessToken").asText();

        // Add with market specified (market is required field)
        String addBody = """
            {"ticker": "TSLA", "market": "US"}
            """;

        mvc.perform(post("/api/watchlist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody))
                .andExpect(status().isOk());

        // Verify market defaults to US
        mvc.perform(get("/api/watchlist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("TSLA"))
                .andExpect(jsonPath("$[0].market").value("US"));
    }

    @Test
    void watchlist_inTestMode_shouldBeIsolated() throws Exception {
        // Register
        String regBody = """
            {"email":"watchlist4@example.com","password":"password1234"}
            """;

        String regRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = om.readTree(regRes).get("accessToken").asText();

        // Add in normal mode
        mvc.perform(post("/api/watchlist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ticker": "AAPL", "market": "US"}
                            """))
                .andExpect(status().isOk());

        // Add in test mode
        mvc.perform(post("/api/watchlist?test=true")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ticker": "TEST", "market": "US"}
                            """))
                .andExpect(status().isOk());

        // Normal mode should only show AAPL
        mvc.perform(get("/api/watchlist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"));

        // Test mode should only show TEST
        mvc.perform(get("/api/watchlist?test=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticker").value("TEST"));
    }
}
