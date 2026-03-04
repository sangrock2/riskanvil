package com.sw103302.backend.component;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 인증/고비용 엔드포인트 IP 기반 Rate Limiting 필터
 * - /api/auth/**: 60초당 10회
 * - /api/analysis(POST): 60초당 20회
 * - /api/market/report(POST): 60초당 8회
 * - /api/chatbot/chat(POST): 60초당 20회
 * - /api/backtest(POST): 60초당 12회
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private static final long WINDOW_MS = 60_000L; // 60초
    private static final List<RateLimitRule> RULES = List.of(
            new RateLimitRule("auth", "/api/auth/", null, 10),
            new RateLimitRule("analysis", "/api/analysis", Set.of("POST"), 20),
            new RateLimitRule("report", "/api/market/report", Set.of("POST"), 8),
            new RateLimitRule("chatbot", "/api/chatbot/chat", Set.of("POST"), 20),
            new RateLimitRule("backtest", "/api/backtest", Set.of("POST"), 12)
    );

    // 규칙 + IP별 (요청 수, 윈도우 시작 시각) 추적
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return resolveRule(request) == null;
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

        String ip = resolveClientIp(request);
        String counterKey = rule.name + ":" + ip;
        WindowCounter counter = counters.computeIfAbsent(counterKey, k -> new WindowCounter());

        if (!counter.tryAcquire(rule.maxRequests)) {
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
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static class WindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());

        boolean tryAcquire(int maxRequests) {
            long now = System.currentTimeMillis();
            long start = windowStart.get();

            // 윈도우가 만료되면 초기화
            if (now - start >= WINDOW_MS) {
                if (windowStart.compareAndSet(start, now)) {
                    count.set(0);
                }
            }

            return count.incrementAndGet() <= maxRequests;
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
