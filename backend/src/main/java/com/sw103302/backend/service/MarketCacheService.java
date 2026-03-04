package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sw103302.backend.component.AiCacheEvictor;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.component.InFlightDeduplicator;
import com.sw103302.backend.dto.InsightRequest;
import com.sw103302.backend.entity.MarketCache;
import com.sw103302.backend.entity.MarketReportHistory;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.MarketCacheRepository;
import com.sw103302.backend.repository.MarketReportHistoryRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.SecurityUtil;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class MarketCacheService {
    private final AiClient aiClient;
    private final ObjectMapper om;
    private final UserRepository userRepository;
    private final MarketCacheRepository marketCacheRepository;
    private final UsageService usageService;
    private final MarketReportHistoryRepository marketReportHistoryRepository;
    private final QuantAnalysisService quantAnalysisService;
    private final TransactionTemplate tx;
    private final InFlightDeduplicator dedup;
    private final AiCacheEvictor aiCacheEvictor;

    public MarketCacheService(
            AiClient aiClient, ObjectMapper om, UserRepository userRepository, MarketCacheRepository marketCacheRepository, UsageService usageService,
            MarketReportHistoryRepository marketReportHistoryRepository, QuantAnalysisService quantAnalysisService, TransactionTemplate tx,
            InFlightDeduplicator dedup, AiCacheEvictor aiCacheEvictor
    ) {
        this.aiClient = aiClient;
        this.om = om;
        this.userRepository = userRepository;
        this.marketCacheRepository = marketCacheRepository;
        this.usageService = usageService;
        this.marketReportHistoryRepository = marketReportHistoryRepository;
        this.quantAnalysisService = quantAnalysisService;
        this.tx = tx;
        this.dedup = dedup;
        this.aiCacheEvictor = aiCacheEvictor;
    }

    public String getReport(InsightRequest req, boolean test, boolean refresh, boolean web) {
        User user = currentUser();
        return getReportInternal(user, req, test, refresh, web);
    }

    public String getInsights(InsightRequest req, boolean test, boolean refresh) {
        User user = currentUser();
        return getInsightsForUser(user, req, test, refresh, true);
    }

    public String getInsightsForUser(User user, InsightRequest req, boolean test, boolean refresh, boolean logInsightsUsage) {
        if (req == null || req.ticker() == null || req.ticker().isBlank()) {
            throw new IllegalArgumentException("ticker is required");
        }

        String ticker = req.ticker().trim();
        String market = (req.market() == null || req.market().isBlank()) ? "US" : req.market().trim();
        int days = (req.days() != null && req.days() > 0) ? req.days() : 90;
        int newsLimit = (req.newsLimit() != null && req.newsLimit() > 0) ? req.newsLimit() : 20;

        String requestId = MDC.get("requestId");
        long startNs = System.nanoTime();

        boolean cached = false;
        boolean dedupJoined = false;
        int httpStatus = 200;
        String errorText = null;

        try {
            if (!refresh) {
                MarketCache cache = marketCacheRepository
                        .findByUser_IdAndTickerAndMarketAndTestModeAndDaysAndNewsLimit(user.getId(), ticker, market, test, days, newsLimit)
                        .orElse(null);

                if (cache != null && cache.getInsightsJson() != null && !cache.getInsightsJson().isBlank()) {
                    cached = true;
                    String out = withCacheMeta(cache.getInsightsJson(), true, cache.getInsightsUpdatedAt(), cache.getReportUpdatedAt());
                    return quantAnalysisService.attachQuant(out);
                }
            }

            String key = "INSIGHTS:" + user.getId() + ":" + ticker + ":" + market + ":" + test + ":" + days + ":" + newsLimit + ":refresh=" + refresh;

            return dedup.execute(key, () -> {
                InsightRequest sanitized = new InsightRequest(ticker, market, days, newsLimit, null, null, null, null);

                if (refresh) {
                    aiCacheEvictor.evictInsights(sanitized, test);
                }

                String raw = aiClient.insights(sanitized, test);

                MarketCache saved = tx.execute(status -> {
                    MarketCache cache = marketCacheRepository
                            .findByUser_IdAndTickerAndMarketAndTestModeAndDaysAndNewsLimit(user.getId(), ticker, market, test, days, newsLimit)
                            .orElse(null);

                    if (cache == null) cache = new MarketCache(user, ticker, market, test, days, newsLimit);

                    cache.setInsightsJson(raw);
                    cache.setInsightsUpdatedAt(LocalDateTime.now());
                    return marketCacheRepository.save(cache);
                });

                String out = withCacheMeta(raw, false, saved.getInsightsUpdatedAt(), saved.getReportUpdatedAt());
                return quantAnalysisService.attachQuant(out);
            });
        } catch (Exception e) {
            httpStatus = 500;
            errorText = (e.getMessage() == null ? "internal_error" : e.getMessage());
            throw e;
        } finally {
            if (logInsightsUsage) {
                long durationMs = (System.nanoTime() - startNs) / 1_000_000L;

                boolean cachedForUsage = cached || dedupJoined;

                usageService.log(
                        user,
                        "INSIGHTS",
                        ticker,
                        market,
                        test,
                        days,
                        newsLimit,
                        cachedForUsage,
                        refresh,
                        false,       // web
                        requestId,
                        httpStatus,
                        durationMs,
                        errorText
                );
            }
        }
    }

    @Transactional(readOnly = true)
    public String getReportHistory(String ticker, String market, boolean test, int days, int newsLimit, int limit) {
        User user = currentUser();

        String t = ticker.trim();
        String m = (market == null || market.isBlank()) ? "US" : market.trim();
        int d = days <= 0 ? 90 : days;
        int n = newsLimit <= 0 ? 20 : newsLimit;
        int lim = (limit <= 0) ? 20 : Math.min(limit, 50);

        MarketCache cache = marketCacheRepository
                .findByUser_IdAndTickerAndMarketAndTestModeAndDaysAndNewsLimit(user.getId(), t, m, test, d, n)
                .orElse(null);

        ObjectNode out = om.createObjectNode();
        out.put("ticker", t);
        out.put("market", m);
        out.put("testMode", test);
        out.put("days", d);
        out.put("newsLimit", n);

        ObjectNode current = out.putObject("current");
        if (cache != null && cache.getReportText() != null && !cache.getReportText().isBlank()) {
            current.put("report", cache.getReportText());
            if (cache.getReportUpdatedAt() != null) current.put("reportUpdatedAt", cache.getReportUpdatedAt().toString());
        } else {
            current.putNull("report");
            current.putNull("reportUpdatedAt");
        }

        var arr = om.createArrayNode();
        if (cache != null) {
            var list = marketReportHistoryRepository.findTop20ByCache_IdOrderByCreatedAtDesc(cache.getId());
            if (list.size() > lim) list = list.subList(0, lim);

            for (var h : list) {
                ObjectNode r = om.createObjectNode();
                r.put("id", h.getId());
                r.put("createdAt", h.getCreatedAt().toString());
                r.put("report", h.getReportText());
                arr.add(r);
            }
        }

        out.set("items", arr);
        return write(out);
    }

    public String getReportForUser(User user, InsightRequest req, boolean test, boolean refresh, boolean web) {
        return getReportInternal(user, req, test, refresh, web);
    }

    private String getReportInternal(User user, InsightRequest req, boolean test, boolean refresh, boolean web) {
        if (req == null || req.ticker() == null || req.ticker().isBlank()) {
            throw new IllegalArgumentException("ticker is required");
        }

        String ticker = req.ticker().trim();
        String market = (req.market() == null || req.market().isBlank()) ? "US" : req.market().trim();
        int days = (req.days() != null && req.days() > 0) ? req.days() : 90;
        int newsLimit = (req.newsLimit() != null && req.newsLimit() > 0) ? req.newsLimit() : 20;

        String requestId = MDC.get("requestId");
        long startNs = System.nanoTime();

        boolean cached = false;
        boolean dedupJoined = false;
        int httpStatus = 200;
        String errorText = null;

        try {
            if (!refresh) {
                MarketCache cache = marketCacheRepository
                        .findByUser_IdAndTickerAndMarketAndTestModeAndDaysAndNewsLimit(user.getId(), ticker, market, test, days, newsLimit)
                        .orElse(null);

                if (cache != null && cache.getReportText() != null && !cache.getReportText().isBlank()) {
                    cached = true;

                    String fixed = normalizeReportText(cache.getReportText());

                    // 자동 교정 저장(짧은 TX)
                    if (!Objects.equals(fixed, cache.getReportText())) {
                        tx.execute(status -> {
                            MarketCache managed = marketCacheRepository
                                    .findById(cache.getId())
                                    .orElseThrow();
                            managed.setReportText(fixed);
                            if (managed.getReportUpdatedAt() == null) managed.setReportUpdatedAt(LocalDateTime.now());
                            marketCacheRepository.save(managed);
                            return null;
                        });
                    }

                    ObjectNode out = om.createObjectNode();
                    out.put("ticker", ticker);
                    out.put("market", market);
                    out.put("report", fixed);

                    ObjectNode meta = out.putObject("_cache");
                    meta.put("cached", true);

                    if (cache.getInsightsUpdatedAt() != null) meta.put("insightsUpdatedAt", cache.getInsightsUpdatedAt().toString());
                    if (cache.getReportUpdatedAt() != null) meta.put("reportUpdatedAt", cache.getReportUpdatedAt().toString());

                    return write(out);
                }
            }

            String key = "REPORT:" + user.getId() + ":" + ticker + ":" + market + ":" + test + ":" + days + ":" + newsLimit + ":" + web + ":refresh=" + refresh;

            return dedup.execute(key, () -> {
                InsightRequest sanitized = new InsightRequest(ticker, market, days, newsLimit, null, null, null, null);

                if (refresh) {
                    aiCacheEvictor.evictReport(sanitized, test, web);
                }

                String raw = aiClient.report(sanitized, test, web);
                String reportText = normalizeReportText(raw);

                MarketCache saved = tx.execute(status -> {
                    MarketCache cache = marketCacheRepository
                            .findByUser_IdAndTickerAndMarketAndTestModeAndDaysAndNewsLimit(user.getId(), ticker, market, test, days, newsLimit)
                            .orElse(null);

                    if (cache == null) cache = new MarketCache(user, ticker, market, test, days, newsLimit);

                    String prev = cache.getReportText();
                    String prevFixed = (prev == null ? null : normalizeReportText(prev));

                    if (prevFixed != null && !prevFixed.isBlank() && reportText != null && !reportText.isBlank()  && !prevFixed.equals(reportText)) {
                        marketReportHistoryRepository.save(new MarketReportHistory(cache, prevFixed));
                    }

                    cache.setReportText(reportText);
                    cache.setReportUpdatedAt(LocalDateTime.now());

                    return marketCacheRepository.save(cache);
                });

                String out = withCacheMeta(raw, false, saved.getInsightsUpdatedAt(), saved.getReportUpdatedAt());
                return quantAnalysisService.attachQuant(out);
            });
        } catch (Exception e) {
            httpStatus = 500;
            errorText = (e.getMessage() == null ? "internal_error" : e.getMessage());
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;

            boolean cachedForUsage = cached || dedupJoined;

            usageService.log(
                    user,
                    "REPORT",
                    ticker,
                    market,
                    test,
                    days,
                    newsLimit,
                    cachedForUsage,
                    refresh,
                    web,
                    requestId,
                    httpStatus,
                    durationMs,
                    errorText
            );
        }
    }

    private String withCacheMeta(String rawJson, boolean cached, LocalDateTime insightsUpdatedAt, LocalDateTime reportUpdatedAt) {
        try {
            JsonNode root = om.readTree(rawJson);
            if (!root.isObject()) {
                // object가 아니면 wrapper로 감싸기
                ObjectNode wrap = om.createObjectNode();
                wrap.set("data", root);
                ObjectNode meta = wrap.putObject("_cache");
                meta.put("cached", cached);
                if (insightsUpdatedAt != null) meta.put("insightsUpdatedAt", insightsUpdatedAt.toString());
                if (reportUpdatedAt != null) meta.put("reportUpdatedAt", reportUpdatedAt.toString());
                return write(wrap);
            }

            ObjectNode obj = (ObjectNode) root;
            ObjectNode meta = obj.putObject("_cache");
            meta.put("cached", cached);
            if (insightsUpdatedAt != null) meta.put("insightsUpdatedAt", insightsUpdatedAt.toString());
            if (reportUpdatedAt != null) meta.put("reportUpdatedAt", reportUpdatedAt.toString());
            return write(obj);
        } catch (Exception e) {
            // JSON 파싱이 실패해도 최소한 메타 wrapper로 반환
            ObjectNode wrap = om.createObjectNode();
            wrap.put("raw", rawJson);
            ObjectNode meta = wrap.putObject("_cache");
            meta.put("cached", cached);
            if (insightsUpdatedAt != null) meta.put("insightsUpdatedAt", insightsUpdatedAt.toString());
            if (reportUpdatedAt != null) meta.put("reportUpdatedAt", reportUpdatedAt.toString());
            return write(wrap);
        }
    }

    private String normalizeReportText(String rawOrCached) {
        if (rawOrCached == null) return null;

        String s = rawOrCached.trim();
        if (s.isBlank()) return s;

        // 1) JSON 시도: {"text":"..."} 형태 or "\"{...}\"" 형태(중첩) 모두 처리
        try {
            JsonNode node = om.readTree(s);

            // (a) 만약 JSON 문자열("...")로 감싸진 형태면 한 번 더 풀기
            if (node.isTextual()) {
                String inner = node.asText();
                if (inner != null && !inner.isBlank()) {
                    node = om.readTree(inner);
                }
            }

            // (b) 객체면 text 필드 우선
            if (node != null && node.isObject()) {
                JsonNode t = node.get("text");
                if (t != null && t.isTextual()) {
                    String text = t.asText();
                    return (text == null) ? "" : text.trim();
                }
            }
        } catch (Exception ignore) {
            // JSON이 아니면 아래로
        }

        // 2) 그냥 텍스트로 간주: "\\n" 같은 잔여 이스케이프를 사람이 읽는 개행으로 교정
        //    (이미 정상 텍스트면 영향 거의 없음)
        return s.replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .trim();
    }

    private String extractReportText(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("{")) {
            try {
                JsonNode n = om.readTree(s);
                // AI가 {report:"..."} 로 주는 케이스
                JsonNode r = n.get("report");
                if (r != null && r.isTextual()) return r.asText();
                // 다른 키를 쓴 케이스
                JsonNode c = n.get("content");
                if (c != null && c.isTextual()) return c.asText();
            } catch (Exception ignored) {}
        }
        return raw; // plain text
    }

    private String write(JsonNode node) {
        try {
            return om.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("json serialization failed: " + e.getMessage(), e);
        }
    }

    private User currentUser() {
        String email = SecurityUtil.currentEmail();
        if (email == null) throw new IllegalStateException("unauthenticated");
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalStateException("user not found"));
    }
}
