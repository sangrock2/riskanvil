package com.sw103302.backend.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
public class OperationalReadinessReporter {
    private static final Logger log = LoggerFactory.getLogger(OperationalReadinessReporter.class);
    private final Environment env;

    public OperationalReadinessReporter(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String[] activeProfiles = env.getActiveProfiles();
        boolean prod = Arrays.asList(activeProfiles).contains("prod");

        String datasourceUrl = env.getProperty("spring.datasource.url", "");
        String aiBaseUrl = env.getProperty("ai.baseUrl", "");
        String cacheType = env.getProperty("spring.cache.type", "unknown");
        boolean sentryEnabled = hasText(env.getProperty("sentry.dsn"));

        log.info(
                "Operational baseline: profiles={}, datasource={}, aiBaseUrl={}, cacheType={}, sentryEnabled={}",
                Arrays.toString(activeProfiles),
                summarizeJdbcUrl(datasourceUrl),
                aiBaseUrl,
                cacheType,
                sentryEnabled
        );

        if (!prod) {
            return;
        }

        warnIfDefaultJwtSecret();
        warnIfCorsPlaceholder();
        warnIfSentryDisabled();
        warnIfDatasourceLooksInvalid(datasourceUrl);
    }

    private void warnIfDefaultJwtSecret() {
        String secret = env.getProperty("security.jwt.secret", "");
        if (secret.contains("dev-only") || secret.contains("change-in-production")) {
            log.warn("JWT_SECRET appears to be a development default. Set a strong random secret in production.");
        }
    }

    private void warnIfCorsPlaceholder() {
        String cors = env.getProperty("app.cors.allowed-origin-patterns", "");
        if (cors.contains("your-frontend-domain.com")) {
            log.warn("APP_CORS_ALLOWED_ORIGIN_PATTERNS still has placeholder domain. Update it to real frontend URL.");
        }
    }

    private void warnIfSentryDisabled() {
        if (!hasText(env.getProperty("sentry.dsn"))) {
            log.warn("SENTRY_DSN is not configured. Production error tracking/alerts will be limited.");
        }
    }

    private void warnIfDatasourceLooksInvalid(String datasourceUrl) {
        if (!hasText(datasourceUrl)) {
            log.warn("spring.datasource.url is empty. Database connection may fail.");
            return;
        }

        String lower = datasourceUrl.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("jdbc:")) {
            log.warn("spring.datasource.url does not start with jdbc:. Current value={}", summarizeJdbcUrl(datasourceUrl));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String summarizeJdbcUrl(String url) {
        if (!hasText(url)) {
            return "unset";
        }
        String trimmed = url.trim();
        int queryIndex = trimmed.indexOf('?');
        return queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
    }
}

