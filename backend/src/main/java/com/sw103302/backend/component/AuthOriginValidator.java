package com.sw103302.backend.component;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AuthOriginValidator {
    private final CorsConfiguration corsConfiguration;

    public AuthOriginValidator(
            @Value("${app.cors.allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000}")
            String allowedOriginPatternsCsv
    ) {
        List<String> patterns = Arrays.stream(allowedOriginPatternsCsv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toList());

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(patterns);
        this.corsConfiguration = config;
    }

    public boolean isAllowed(HttpServletRequest request) {
        String origin = normalize(request.getHeader(HttpHeaders.ORIGIN));
        if (origin != null) {
            return corsConfiguration.checkOrigin(origin) != null;
        }

        String referer = normalize(request.getHeader(HttpHeaders.REFERER));
        if (referer == null) {
            return false;
        }

        try {
            URI uri = URI.create(referer);
            String scheme = normalize(uri.getScheme());
            String host = normalize(uri.getHost());
            if (scheme == null || host == null) {
                return false;
            }

            String authority = uri.getPort() > 0 ? host + ":" + uri.getPort() : host;
            return corsConfiguration.checkOrigin(scheme + "://" + authority) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
