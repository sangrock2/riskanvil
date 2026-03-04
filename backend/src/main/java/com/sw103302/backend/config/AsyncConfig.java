package com.sw103302.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {
    @Bean(name = "reportExecutor")
    public Executor reportExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(8);       // Increased from 2 to 8
        ex.setMaxPoolSize(32);       // Increased from 4 to 32
        ex.setQueueCapacity(200);    // Increased from 50 to 200
        ex.setThreadNamePrefix("report-");
        ex.initialize();
        return ex;
    }
}
