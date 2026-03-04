package com.sw103302.backend.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.dto.AiAnalyzeRequest;
import com.sw103302.backend.dto.AiBacktestRequest;
import com.sw103302.backend.dto.InsightRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class AiClient {
    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private final WebClient aiWebClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Duration timeoutDefault;
    private final Duration timeoutReport;
    private final int retryAttempts;
    private final int retryAttemptsReport;
    private final Duration retryBackoff;
    private final Duration retryMaxBackoff;

    public AiClient(
            WebClient aiWebClient,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${ai.client.timeout.default-seconds:30}") long timeoutDefaultSeconds,
            @Value("${ai.client.timeout.report-seconds:180}") long timeoutReportSeconds,
            @Value("${ai.client.retry.attempts:2}") int retryAttempts,
            @Value("${ai.client.retry.report-attempts:0}") int retryAttemptsReport,
            @Value("${ai.client.retry.backoff-millis:200}") long retryBackoffMillis,
            @Value("${ai.client.retry.max-backoff-millis:2000}") long retryMaxBackoffMillis
    ) {
        this.aiWebClient = aiWebClient;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.timeoutDefault = Duration.ofSeconds(timeoutDefaultSeconds);
        this.timeoutReport = Duration.ofSeconds(timeoutReportSeconds);
        this.retryAttempts = retryAttempts;
        this.retryAttemptsReport = retryAttemptsReport;
        this.retryBackoff = Duration.ofMillis(retryBackoffMillis);
        this.retryMaxBackoff = Duration.ofMillis(retryMaxBackoffMillis);
    }

    // -------- public APIs --------

    public String analyze(AiAnalyzeRequest req) {
        return postJson("/analyze", req, timeoutDefault, "analyze");
    }

    public String backtest(AiBacktestRequest req) {
        return postJson("/backtest", req, timeoutDefault, "backtest");
    }

    @Cacheable(
            cacheNames = "ai_insights",
            key = "T(com.sw103302.backend.util.AiCacheKey).insights(#req, #test)",
            unless = "#result == null || #result.isBlank()"
    )
    public String insights(InsightRequest req, boolean test) {
        return postJson("/insights?test=" + test, req, timeoutDefault, "insights");
    }

    @Cacheable(
            cacheNames = "ai_report",
            key = "T(com.sw103302.backend.util.AiCacheKey).report(#req, #test, #web)",
            unless = "#result == null || #result.isBlank()"
    )
    public String report(InsightRequest req, boolean test, boolean web) {
        return postJson("/report?test=" + test + "&web=" + web, req, timeoutReport, "report");
    }

    @Cacheable(cacheNames = "ai_symbol_search",
            key = "T(com.sw103302.backend.util.AiCacheKey).symbolSearch(#keywords, #market, #test)",
            unless = "#result == null || #result.isBlank()"
    )
    public String symbolSearch(String keywords, String market, boolean test) {
        return getText("/symbol_search",
                uri -> uri.queryParam("keywords", keywords)
                        .queryParam("market", market)
                        .queryParam("test", test),
                timeoutDefault,
                "symbol_search");
    }

    @Cacheable(cacheNames = "ai_quote",
            key = "T(com.sw103302.backend.util.AiCacheKey).quote(#ticker, #market)",
            unless = "#result == null || #result.isBlank()"
    )
    public String quote(String ticker, String market) {
        return getText("/quote",
                uri -> uri.queryParam("ticker", ticker).queryParam("market", market),
                timeoutDefault,
                "quote");
    }

    @Cacheable(cacheNames = "ai_prices",
            key = "T(com.sw103302.backend.util.AiCacheKey).prices(#ticker, #market, #days)",
            unless = "#result == null || #result.isBlank()"
    )
    public String prices(String ticker, String market, int days) {
        return getText("/prices",
                uri -> uri.queryParam("ticker", ticker)
                        .queryParam("market", market)
                        .queryParam("days", days),
                timeoutDefault,
                "prices");
    }

    @Cacheable(cacheNames = "ai_ohlc",
            key = "T(com.sw103302.backend.util.AiCacheKey).ohlc(#ticker, #market, #days)",
            unless = "#result == null || #result.isBlank()"
    )
    public String ohlc(String ticker, String market, int days) {
        return getText("/ohlc",
                uri -> uri.queryParam("ticker", ticker)
                        .queryParam("market", market)
                        .queryParam("days", days),
                timeoutDefault,
                "ohlc");
    }

    @Cacheable(cacheNames = "ai_fundamentals",
            key = "T(com.sw103302.backend.util.AiCacheKey).fundamentals(#ticker, #market)",
            unless = "#result == null || #result.isBlank()"
    )
    public String fundamentals(String ticker, String market) {
        return getText("/fundamentals",
                uri -> uri.queryParam("ticker", ticker).queryParam("market", market),
                timeoutDefault,
                "fundamentals");
    }

    @Cacheable(cacheNames = "ai_news",
            key = "T(com.sw103302.backend.util.AiCacheKey).news(#ticker, #market, #limit)",
            unless = "#result == null || #result.isBlank()"
    )
    public String news(String ticker, String market, int limit) {
        return getText("/news",
                uri -> uri.queryParam("ticker", ticker)
                        .queryParam("market", market)
                        .queryParam("limit", limit),
                timeoutDefault,
                "news");
    }

    /**
     * Generic POST endpoint for new features (screener, correlation, chatbot, etc.)
     */
    public String post(String path, Object body) {
        return postJson(path, body, timeoutDefault, extractOpName(path));
    }

    /**
     * Generic POST endpoint with custom timeout
     */
    public String post(String path, Object body, Duration timeout) {
        return postJson(path, body, timeout, extractOpName(path));
    }

    private String extractOpName(String path) {
        // Extract operation name from path for metrics
        // e.g., "/prices/batch" -> "prices_batch", "/screener" -> "screener"
        return path.replaceAll("^/", "").replaceAll("/", "_");
    }

    // -------- core helpers (stability + metrics) --------

    private String postJson(String path, Object body, Duration timeout, String op) {
        Timer.Sample sample = Timer.start(meterRegistry);

        String statusTag = "UNKNOWN";
        String outcomeTag = "error";

        try {
            String result = aiWebClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(resp ->
                            resp.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(raw -> {
                                        if (resp.statusCode().isError()) {
                                            return Mono.error(new AiClientException(resp.statusCode(), raw));
                                        }
                                        return Mono.just(raw);
                                    })
                    )
                    .timeout(timeout)
                    .retryWhen(retrySpec(op))
                    .block();

            statusTag = "200";
            outcomeTag = "success";
            incrementCounter(op, "POST", outcomeTag, statusTag);

            return (result == null) ? "" : result;

        } catch (Exception e) {
            // 상태 태그 만들기
            statusTag = statusFromException(e);
            outcomeTag = "error";
            incrementCounter(op, "POST", outcomeTag, statusTag);
            throw e;
        } finally {
            sample.stop(timer(op, "POST", outcomeTag, statusTag));
        }
    }

    private String getText(String path, java.util.function.Function<org.springframework.web.util.UriBuilder, org.springframework.web.util.UriBuilder> query, Duration timeout, String op) {
        Timer.Sample sample = Timer.start(meterRegistry);

        String statusTag = "UNKNOWN";
        String outcomeTag = "error";

        try {
            String result = aiWebClient.get()
                    .uri(uriBuilder -> query.apply(uriBuilder.path(path)).build())
                    .exchangeToMono(resp ->
                            resp.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(raw -> {
                                        if (resp.statusCode().isError()) {
                                            return Mono.error(new AiClientException(resp.statusCode(), raw));
                                        }
                                        return Mono.just(raw);
                                    })
                    )
                    .timeout(timeout)
                    .retryWhen(retrySpec(op))
                    .block();

            statusTag = "200";
            outcomeTag = "success";
            incrementCounter(op, "GET", outcomeTag, statusTag);

            return (result == null) ? "" : result;

        } catch (Exception e) {
            statusTag = statusFromException(e);
            outcomeTag = "error";
            incrementCounter(op, "GET", outcomeTag, statusTag);
            throw e;
        } finally {
            sample.stop(timer(op, "GET", outcomeTag, statusTag));
        }
    }


    // 재시도 정책
    private Retry retrySpec(String op) {
        int retries = "report".equals(op) ? retryAttemptsReport : retryAttempts;

        return Retry.backoff(retries, retryBackoff)
                .maxBackoff(retryMaxBackoff)
                .filter(this::isRetryable)
                .doBeforeRetry(signal -> log.warn("Retrying AI call. op={} attempt={} reason={}",
                        op, signal.totalRetries() + 1, signal.failure() == null ? "unknown" : signal.failure().getClass().getSimpleName()))
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    private boolean isRetryable(Throwable t) {
        if (t instanceof TimeoutException) return true;
        if (t instanceof WebClientRequestException) return true; // connect/reset 등
        if (t instanceof AiClientException ace) {
            int s = ace.getStatus();
            return s >= 500 && s <= 599;
        }
        return false;
    }

    // -------- metrics helpers --------

    private Timer timer(String op, String method, String outcome, String status) {
        return Timer.builder("ai_client_latency")
                .description("Latency of calls to AI server")
                .tag("op", op)
                .tag("method", method)
                .tag("outcome", outcome) // success|error
                .tag("status", status)   // 200|4xx|5xx|TIMEOUT|IO_ERROR...
                .register(meterRegistry);
    }

    private void incrementCounter(String op, String method, String outcome, String status) {
        Counter.builder("ai_client_requests_total")
                .description("Total number of calls to AI server")
                .tag("op", op)
                .tag("method", method)
                .tag("outcome", outcome)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    private String statusFromException(Throwable e) {
        // AiClientException이면 HTTP 상태코드 기반
        if (e instanceof AiClientException ace) {
            int s = ace.getStatus();
            if (s >= 400 && s <= 499) return "4xx";
            if (s >= 500 && s <= 599) return "5xx";
            return String.valueOf(s);
        }

        // timeout / io 계열 태깅
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof TimeoutException) return "TIMEOUT";
            if (cur instanceof WebClientRequestException) return "IO_ERROR";
            cur = cur.getCause();
        }
        return "EXCEPTION";
    }
}
