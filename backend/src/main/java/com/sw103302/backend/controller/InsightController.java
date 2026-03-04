package com.sw103302.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InsightController {
    private final AiClient aiClient;
    private final ObjectMapper om;

    public InsightController(AiClient aiClient, ObjectMapper om) {
        this.aiClient = aiClient;
        this.om = om;
    }

    /*
    @GetMapping("/insights")
    public Object insights(@RequestParam String ticker,
                           @RequestParam(defaultValue = "US") String market,
                           @RequestParam(defaultValue = "90") int days,
                           @RequestParam(defaultValue = "20") int newsLimit) throws Exception {
        String json = aiClient.insights(ticker, market, days, newsLimit);
        return om.readValue(json, Object.class);
    }
     */
}
