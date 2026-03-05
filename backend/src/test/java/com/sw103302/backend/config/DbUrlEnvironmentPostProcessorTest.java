package com.sw103302.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DbUrlEnvironmentPostProcessorTest {

    @Test
    void normalizePostgresqlProtocolUrl() {
        String raw = "postgresql://dpg-xxxx-a:5432/stock_ai";

        String normalized = DbUrlEnvironmentPostProcessor.normalizeDbUrl(raw);

        assertThat(normalized).isEqualTo("jdbc:postgresql://dpg-xxxx-a:5432/stock_ai");
    }

    @Test
    void normalizePostgresShortcutUrl() {
        String raw = "postgres://dpg-xxxx-a:5432/stock_ai?sslmode=require";

        String normalized = DbUrlEnvironmentPostProcessor.normalizeDbUrl(raw);

        assertThat(normalized).isEqualTo("jdbc:postgresql://dpg-xxxx-a:5432/stock_ai?sslmode=require");
    }

    @Test
    void keepJdbcUrlAsIs() {
        String raw = "jdbc:postgresql://dpg-xxxx-a:5432/stock_ai";

        String normalized = DbUrlEnvironmentPostProcessor.normalizeDbUrl(raw);

        assertThat(normalized).isEqualTo(raw);
    }

    @Test
    void keepUnknownSchemeAsIs() {
        String raw = "sqlite:///tmp/test.db";

        String normalized = DbUrlEnvironmentPostProcessor.normalizeDbUrl(raw);

        assertThat(normalized).isEqualTo(raw);
    }
}

