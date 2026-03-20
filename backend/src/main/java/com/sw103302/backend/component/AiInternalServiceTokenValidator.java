package com.sw103302.backend.component;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.StringJoiner;

@Component
public class AiInternalServiceTokenValidator {
    private static final Logger log = LoggerFactory.getLogger(AiInternalServiceTokenValidator.class);

    private final String internalServiceToken;
    private final Environment environment;

    public AiInternalServiceTokenValidator(
            @Value("${ai.internal.service-token:}") String internalServiceToken,
            Environment environment
    ) {
        this.internalServiceToken = internalServiceToken == null ? "" : internalServiceToken.trim();
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (internalServiceToken.isBlank()) {
            throw new IllegalStateException(
                    "SECURITY ERROR: AI_INTERNAL_SERVICE_TOKEN is required before startup."
            );
        }
        if (usesDevelopmentToken() && isProdLikeEnvironment()) {
            throw new IllegalStateException(
                    "SECURITY ERROR: Default AI internal token is not allowed in active profiles [" +
                            activeProfilesSummary() + "]. Set AI_INTERNAL_SERVICE_TOKEN to a strong random value."
            );
        }
        if (usesDevelopmentToken()) {
            log.warn("Using development AI internal token for active profiles [{}].", activeProfilesSummary());
        }
    }

    private boolean usesDevelopmentToken() {
        String normalized = internalServiceToken.toLowerCase(Locale.ROOT);
        return normalized.contains("dev-only") || normalized.contains("change-me");
    }

    private boolean isProdLikeEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return false;
        }

        for (String profile : activeProfiles) {
            String normalized = profile.toLowerCase(Locale.ROOT);
            if ("dev".equals(normalized) || "test".equals(normalized)) {
                return false;
            }
        }
        return true;
    }

    private String activeProfilesSummary() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return "default";
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (String profile : activeProfiles) {
            joiner.add(profile);
        }
        return joiner.toString();
    }
}
