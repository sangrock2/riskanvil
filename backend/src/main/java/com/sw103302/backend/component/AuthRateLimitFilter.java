package com.sw103302.backend.component;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 인증/고비용 엔드포인트 IP 기반 Rate Limiting 필터
 * - /api/auth/check-email(POST): 60초당 30회
 * - /api/auth/**(기타): 60초당 10회
 * - /api/analysis(POST): 60초당 20회
 * - /api/market/report(POST): 60초당 8회
 * - /api/chatbot/chat(POST): 60초당 20회
 * - /api/backtest(POST): 60초당 12회
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final int DEFAULT_MAX_TRACKED_COUNTERS = 4_096;
    private final boolean enabled;
    private final boolean trustXForwardedFor;
    private final boolean trustXRealIp;
    private final int maxTrackedCounters;

    private static final long WINDOW_MS = 60_000L; // 60초
    private static final List<RateLimitRule> RULES = List.of(
            new RateLimitRule("check_email", "/api/auth/check-email", Set.of("POST"), 30),
            new RateLimitRule("auth", "/api/auth/", null, 10),
            new RateLimitRule("analysis", "/api/analysis", Set.of("POST"), 20),
            new RateLimitRule("report", "/api/market/report", Set.of("POST"), 8),
            new RateLimitRule("chatbot", "/api/chatbot/chat", Set.of("POST"), 20),
            new RateLimitRule("backtest", "/api/backtest", Set.of("POST"), 12)
    );

    // 규칙 + IP별 (요청 수, 윈도우 시작 시각) 추적
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimitFilter(
            @Value("${security.rate-limit.enabled:true}") boolean enabled,
            @Value("${security.rate-limit.trust-x-forwarded-for:false}") boolean trustXForwardedFor,
            @Value("${security.rate-limit.trust-x-real-ip:true}") boolean trustXRealIp,
            @Value("${security.rate-limit.max-tracked-counters:4096}") int maxTrackedCounters
    ) {
        this.enabled = enabled;
        this.trustXForwardedFor = trustXForwardedFor;
        this.trustXRealIp = trustXRealIp;
        this.maxTrackedCounters = Math.max(128, maxTrackedCounters);
    }

    AuthRateLimitFilter(boolean trustXForwardedFor, boolean trustXRealIp) {
        this(true, trustXForwardedFor, trustXRealIp, DEFAULT_MAX_TRACKED_COUNTERS);
    }

    AuthRateLimitFilter(boolean trustXForwardedFor, boolean trustXRealIp, int maxTrackedCounters) {
        this(true, trustXForwardedFor, trustXRealIp, maxTrackedCounters);
    }

    AuthRateLimitFilter(boolean enabled, boolean trustXForwardedFor, boolean trustXRealIp) {
        this(enabled, trustXForwardedFor, trustXRealIp, DEFAULT_MAX_TRACKED_COUNTERS);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || resolveRule(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        RateLimitRule rule = resolveRule(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        cleanupExpiredCounters(now);
        String ip = resolveClientIp(request);
        String counterKey = resolveCounterKey(rule.name, ip, now);
        WindowCounter counter = counters.computeIfAbsent(counterKey, k -> new WindowCounter(now));

        if (!counter.tryAcquire(rule.maxRequests, now)) {
            log.warn("Rate limit exceeded. rule={} ip={} path={}",
                    rule.name, ip, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\",\"rule\":\"" + rule.name + "\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String resolveCounterKey(String ruleName, String ip, long now) {
        String requestedKey = ruleName + ":" + ip;
        if (counters.containsKey(requestedKey)) {
            return requestedKey;
        }
        if (counters.size() < maxTrackedCounters) {
            return requestedKey;
        }

        cleanupExpiredCounters(now);
        if (counters.size() < maxTrackedCounters) {
            return requestedKey;
        }
        return ruleName + ":overflow";
    }

    private RateLimitRule resolveRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        for (RateLimitRule rule : RULES) {
            if (!path.startsWith(rule.pathPrefix)) {
                continue;
            }
            if (rule.methods == null || rule.methods.contains(method)) {
                return rule;
            }
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (trustXRealIp) {
            String realIp = normalizeIpHeader(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }
        }

        if (trustXForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                String normalized = normalizeIpHeader(first);
                if (normalized != null) {
                    return normalized;
                }
            }
        }

        return request.getRemoteAddr();
    }

    private String normalizeIpHeader(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.trim();
        if ("unknown".equalsIgnoreCase(value)) {
            return null;
        }
        if (!(value.matches("^[0-9.]+$") || value.contains(":"))) {
            return null;
        }

        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void cleanupExpiredCounters(long now) {
        counters.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    int trackedCounterCount() {
        return counters.size();
    }

    private static class WindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong windowStart;

        WindowCounter(long startedAt) {
            this.windowStart = new AtomicLong(startedAt);
        }

        boolean tryAcquire(int maxRequests, long now) {
            long start = windowStart.get();

            // 윈도우가 만료되면 초기화
            if (now - start >= WINDOW_MS) {
                if (windowStart.compareAndSet(start, now)) {
                    count.set(0);
                }
            }

            return count.incrementAndGet() <= maxRequests;
        }

        boolean isExpired(long now) {
            return now - windowStart.get() >= WINDOW_MS;
        }
    }

    private record RateLimitRule(
            String name,
            String pathPrefix,
            Set<String> methods,
            int maxRequests
    ) {
    }
}
