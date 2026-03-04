package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.MonteCarloRequest;
import com.sw103302.backend.dto.MonteCarloResponse;
import org.springframework.stereotype.Service;

@Service
public class MonteCarloService {
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public MonteCarloService(AiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public MonteCarloResponse simulate(MonteCarloRequest req) {
        try {
            String jsonResponse = aiClient.post("/monte-carlo", req);
            return objectMapper.readValue(jsonResponse, MonteCarloResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to run Monte Carlo simulation: " + e.getMessage(), e);
        }
    }
}
