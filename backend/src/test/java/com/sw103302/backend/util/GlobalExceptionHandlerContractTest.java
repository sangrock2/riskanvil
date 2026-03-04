package com.sw103302.backend.util;

import com.sw103302.backend.component.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldReturnStandardErrorEnvelopeWithRequestIdAndErrorCodeHeader() throws Exception {
        mockMvc.perform(get("/test/bad-request")
                        .header("X-Request-Id", "rid-test-001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "rid-test-001"))
                .andExpect(header().string("X-Error-Code", "bad_request"))
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.requestId").value("rid-test-001"))
                .andExpect(jsonPath("$.path").value("/test/bad-request"));
    }

    @RestController
    @RequestMapping("/test")
    static class ThrowingController {
        @GetMapping("/bad-request")
        public String badRequest() {
            throw new IllegalArgumentException("invalid input");
        }
    }
}

