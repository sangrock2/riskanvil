package com.sw103302.backend.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Normalizes DB_URL values that are commonly entered without JDBC prefix on cloud dashboards.
 * Example: postgresql://host:5432/db -> jdbc:postgresql://host:5432/db
 */
public class DbUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final Log log = LogFactory.getLog(DbUrlEnvironmentPostProcessor.class);
    private static final String DB_URL = "DB_URL";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = environment.getProperty(DB_URL);
        if (raw == null || raw.isBlank()) {
            return;
        }

        String normalized = normalizeDbUrl(raw);
        if (normalized.equals(raw.trim())) {
            return;
        }

        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put(DB_URL, normalized);
        environment.getPropertySources().addFirst(new MapPropertySource("dbUrlNormalizer", overrides));

        log.warn(String.format(
                "Normalized DB_URL from '%s' to '%s'. Prefer jdbc:postgresql://... in production.",
                summarize(raw),
                summarize(normalized)
        ));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    static String normalizeDbUrl(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:")) {
            return trimmed;
        }
        if (lower.startsWith("postgresql://")) {
            return "jdbc:postgresql://" + trimmed.substring("postgresql://".length());
        }
        if (lower.startsWith("postgres://")) {
            return "jdbc:postgresql://" + trimmed.substring("postgres://".length());
        }
        if (lower.startsWith("mysql://")) {
            return "jdbc:mysql://" + trimmed.substring("mysql://".length());
        }

        return trimmed;
    }

    private static String summarize(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        int queryIndex = trimmed.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
        if (withoutQuery.length() > 120) {
            return withoutQuery.substring(0, 120) + "...";
        }
        return withoutQuery;
    }
}

