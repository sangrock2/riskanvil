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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {
    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mvc;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectMapper om;

    @MockitoBean
    AiClient aiClient;

    @Test
    void checkEmail_withAvailableEmail_shouldReturnAvailableTrue() throws Exception {
        String body = """
            {"email":"check-available@example.com"}
            """;

        mvc.perform(post("/api/auth/check-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void checkEmail_withRegisteredEmail_shouldReturnAvailableFalse() throws Exception {
        String regBody = """
            {"email":"check-taken@example.com","password":"password1234"}
            """;

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk());

        String checkBody = """
            {"email":"CHECK-TAKEN@example.com"}
            """;

        mvc.perform(post("/api/auth/check-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void checkEmail_withInvalidPayload_shouldFail() throws Exception {
        String body = """
            {"email":"not-an-email"}
            """;

        mvc.perform(post("/api/auth/check-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturnAccessToken() throws Exception {
        String regBody = """
            {"email":"auth1@example.com","password":"password1234"}
            """;

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void register_withDuplicateEmail_shouldFail() throws Exception {
        // First registration
        String regBody = """
            {"email":"duplicate@example.com","password":"password1234"}
            """;

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk());

        // Second registration with same email should fail
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void register_withInvalidEmail_shouldFail() throws Exception {
        String regBody = """
            {"email":"not-an-email","password":"password1234"}
            """;

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withShortPassword_shouldFail() throws Exception {
        String regBody = """
            {"email":"short@example.com","password":"short"}
            """;

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withEmptyEmail_shouldFail() throws Exception {
        String regBody = """
            {"email":"","password":"password1234"}
            """;

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withValidCredentials_shouldReturnToken() throws Exception {
        // Register first
        String regBody = """
            {"email":"login@example.com","password":"password1234"}
            """;

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk());

        // Login
        String loginBody = """
            {"email":"login@example.com","password":"password1234"}
            """;

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void login_withInvalidEmail_shouldFail() throws Exception {
        String loginBody = """
            {"email":"nonexistent@example.com","password":"password1234"}
            """;

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void login_withWrongPassword_shouldFail() throws Exception {
        // Register first
        String regBody = """
            {"email":"wrongpw@example.com","password":"correctpassword"}
            """;

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk());

        // Login with wrong password
        String loginBody = """
            {"email":"wrongpw@example.com","password":"wrongpassword"}
            """;

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void protectedEndpoint_withValidToken_shouldSucceed() throws Exception {
        // Register and get token
        String regBody = """
            {"email":"protected@example.com","password":"password1234"}
            """;

        String regRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = om.readTree(regRes).get("accessToken").asText();

        // Access protected endpoint
        mvc.perform(get("/api/analysis/history?limit=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withoutToken_shouldReturnUnauthorizedOrForbidden() throws Exception {
        mvc.perform(get("/api/analysis/history?limit=10"))
                .andExpect(status().is4xxClientError()); // Either 401 or 403
    }

    @Test
    void protectedEndpoint_withInvalidToken_shouldReturnUnauthorizedOrForbidden() throws Exception {
        mvc.perform(get("/api/analysis/history?limit=10")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().is4xxClientError()); // Either 401 or 403
    }

    @Test
    void protectedEndpoint_withMalformedAuthHeader_shouldReturnUnauthorizedOrForbidden() throws Exception {
        // Missing "Bearer " prefix
        mvc.perform(get("/api/analysis/history?limit=10")
                        .header("Authorization", "some-token"))
                .andExpect(status().is4xxClientError()); // Either 401 or 403
    }

    @Test
    void loginThenAccessProtected_shouldWork() throws Exception {
        // Register
        String regBody = """
            {"email":"fullflow@example.com","password":"password1234"}
            """;

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk());

        // Login
        String loginBody = """
            {"email":"fullflow@example.com","password":"password1234"}
            """;

        String loginRes = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = om.readTree(loginRes).get("accessToken").asText();

        // Access protected endpoint with login token
        mvc.perform(get("/api/analysis/history?limit=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
