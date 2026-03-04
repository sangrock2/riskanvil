package com.sw103302.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        // Java time(Instant 등) 모듈 자동 등록
        return new ObjectMapper().findAndRegisterModules();
    }
}
