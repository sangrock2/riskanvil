package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.CorrelationRequest;
import com.sw103302.backend.dto.CorrelationResponse;
import org.springframework.stereotype.Service;

@Service
public class CorrelationService {
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public CorrelationService(AiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public CorrelationResponse analyze(CorrelationRequest req) {
        try {
            String jsonResponse = aiClient.post("/correlation", req);
            return objectMapper.readValue(jsonResponse, CorrelationResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze correlation: " + e.getMessage(), e);
        }
    }
}
