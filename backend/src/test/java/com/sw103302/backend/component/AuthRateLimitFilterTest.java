package com.sw103302.backend.component;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private AuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthRateLimitFilter();
    }

    @Test
    void shouldSkipNonTargetPath() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/watchlist");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRateLimitAuthPathAfterThreshold() throws ServletException, IOException {
        for (int i = 1; i <= 11; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, new MockFilterChain());

            if (i <= 10) {
                assertThat(res.getStatus()).isEqualTo(200);
            } else {
                assertThat(res.getStatus()).isEqualTo(429);
                assertThat(res.getContentAsString()).contains("\"rule\":\"auth\"");
            }
        }
    }

    @Test
    void shouldRateLimitCheckEmailWithOwnRule() throws ServletException, IOException {
        for (int i = 1; i <= 31; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/check-email");
            req.setRemoteAddr("10.0.0.11");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, new MockFilterChain());

            if (i <= 30) {
                assertThat(res.getStatus()).isEqualTo(200);
            } else {
                assertThat(res.getStatus()).isEqualTo(429);
                assertThat(res.getContentAsString()).contains("\"rule\":\"check_email\"");
            }
        }
    }

    @Test
    void checkEmailRuleShouldNotConsumeAuthBucket() throws ServletException, IOException {
        for (int i = 1; i <= 30; i++) {
            MockHttpServletRequest checkReq = new MockHttpServletRequest("POST", "/api/auth/check-email");
            checkReq.setRemoteAddr("10.0.0.12");
            MockHttpServletResponse checkRes = new MockHttpServletResponse();
            filter.doFilter(checkReq, checkRes, new MockFilterChain());
            assertThat(checkRes.getStatus()).isEqualTo(200);
        }

        for (int i = 1; i <= 11; i++) {
            MockHttpServletRequest loginReq = new MockHttpServletRequest("POST", "/api/auth/login");
            loginReq.setRemoteAddr("10.0.0.12");
            MockHttpServletResponse loginRes = new MockHttpServletResponse();
            filter.doFilter(loginReq, loginRes, new MockFilterChain());

            if (i <= 10) {
                assertThat(loginRes.getStatus()).isEqualTo(200);
            } else {
                assertThat(loginRes.getStatus()).isEqualTo(429);
                assertThat(loginRes.getContentAsString()).contains("\"rule\":\"auth\"");
            }
        }
    }

    @Test
    void shouldRateLimitHighCostReportEndpoint() throws ServletException, IOException {
        for (int i = 1; i <= 9; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/market/report");
            req.setRemoteAddr("10.0.0.10");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, new MockFilterChain());

            if (i <= 8) {
                assertThat(res.getStatus()).isEqualTo(200);
            } else {
                assertThat(res.getStatus()).isEqualTo(429);
                assertThat(res.getHeader("Retry-After")).isEqualTo("60");
                assertThat(res.getContentAsString()).contains("\"rule\":\"report\"");
            }
        }
    }
}
