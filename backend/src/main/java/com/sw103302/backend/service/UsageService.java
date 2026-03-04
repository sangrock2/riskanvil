package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sw103302.backend.dto.UsageAgg;
import com.sw103302.backend.dto.UsageDailyAgg;
import com.sw103302.backend.dto.UsageErrorAgg;
import com.sw103302.backend.dto.UsageTickerAgg;
import com.sw103302.backend.entity.ApiUsageLog;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.ApiUsageLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
public class UsageService {
    private final ApiUsageLogRepository repo;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ObjectMapper om;

    public UsageService(ApiUsageLogRepository repo, ObjectProvider<MeterRegistry> meterRegistryProvider, ObjectMapper om) {
        this.repo = repo;
        this.meterRegistryProvider = meterRegistryProvider;
        this.om = om;
    }

    @Async("reportExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String endpoint, String ticker, String market, boolean testMode, int days, int newsLimit, boolean cached, boolean refresh, boolean web, String errorText) {
        String requestId = safeTrim(MDC.get("requestId"), 64);
        Integer httpStatus = (errorText == null || errorText.isBlank()) ? 200 : 500;

        // durationMs는 호출부에서 측정하지 않으면 null로 저장(나중에 finally 패턴으로 넣으면 됨)
        log(user, endpoint, ticker, market, testMode, days, newsLimit, cached, refresh, web, requestId, httpStatus, null, errorText);
    }

    @Async("reportExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String endpoint, String ticker, String market, boolean testMode, int days, int newsLimit, boolean cached, boolean refresh, boolean web, String requestId, Integer httpStatus, Long durationMs, String errorText) {
        ApiUsageLog log = new ApiUsageLog(user);

        log.setKey(endpoint, ticker, market, testMode, days, newsLimit);
        log.setFlags(cached, refresh, web);
        log.setTrace(safeTrim(requestId, 64), httpStatus, durationMs);

        if (errorText != null && !errorText.isBlank()) {
            log.setErrorText(safeTrim(errorText, 512));
        }

        repo.save(log);

        MeterRegistry reg = meterRegistryProvider.getIfAvailable();
        if (reg != null) {
            if (durationMs != null) {
                Timer.builder("api.latency.ms")
                        .tag("endpoint", endpoint)
                        .tag("cached", String.valueOf(cached))
                        .tag("refresh", String.valueOf(refresh))
                        .tag("web", String.valueOf(web))
                        .tag("testMode", String.valueOf(testMode))
                        .tag("market", market == null ? "NA" : market)
                        .register(reg)
                        .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            }

            reg.counter("api.calls",
                    "endpoint", endpoint,
                    "httpStatus", String.valueOf(httpStatus),
                    "cached", String.valueOf(cached)
            ).increment();
        }
    }

    public String summaryJson(User user, boolean testMode) {
        // Today range
        LocalDate today = LocalDate.now();
        LocalDateTime fromToday = today.atStartOfDay();
        LocalDateTime toToday = today.plusDays(1).atStartOfDay();

        // Month range
        YearMonth ym = YearMonth.from(today);
        LocalDateTime fromMonth = ym.atDay(1).atStartOfDay();
        LocalDateTime toMonth = ym.plusMonths(1).atDay(1).atStartOfDay();

        List<UsageAgg> todayAgg = repo.aggregateByEndpoint(user.getId(), testMode, fromToday, toToday);
        List<UsageAgg> monthAgg = repo.aggregateByEndpoint(user.getId(), testMode, fromMonth, toMonth);

        ObjectNode out = om.createObjectNode();
        out.put("testMode", testMode);

        out.set("today", toNode(todayAgg));
        out.set("month", toNode(monthAgg));

        return write(out);
    }

    private ObjectNode toNode(List<UsageAgg> list) {
        ObjectNode node = om.createObjectNode();

        long total = 0;
        long cached = 0;
        long refresh = 0;
        long web = 0;

        for (UsageAgg a : list) {
            total += a.total();
            cached += a.cached();
            refresh += a.refresh();
            web += a.web();
        }

        node.put("totalCalls", total);
        node.put("cachedCalls", cached);
        node.put("refreshCalls", refresh);
        node.put("webOnCalls", web);

        double hitRate = (total <= 0) ? 0.0 : ((double) cached / (double) total);
        node.put("cacheHitRate", hitRate);

        // endpoint별 상세
        var arr = om.createArrayNode();
        for (UsageAgg a : list) {
            ObjectNode r = om.createObjectNode();
            r.put("endpoint", a.endpoint());
            r.put("total", a.total());
            r.put("cached", a.cached());
            r.put("refresh", a.refresh());
            r.put("web", a.web());
            r.put("alphaCalls", a.alphaCalls());
            r.put("openaiCalls", a.openaiCalls());
            arr.add(r);
        }
        node.set("byEndpoint", arr);

        return node;
    }

    public String dashboardJson(User user, boolean testMode, int days) {
        if (days <= 0) days = 30;
        if (days > 365) days = 365;

        LocalDate today = LocalDate.now();
        LocalDate fromD = today.minusDays(days - 1L);
        LocalDateTime from = fromD.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        // 기존 summary의 today/month 그대로 재사용
        String summaryRaw = summaryJson(user, testMode);

        ObjectNode out;
        try {
            out = (ObjectNode) om.readTree(summaryRaw);
        } catch (Exception e) {
            out = om.createObjectNode();
            out.put("testMode", testMode);
        }

        ObjectNode range = out.putObject("range");
        range.put("from", fromD.toString());
        range.put("to", today.toString());
        range.put("days", days);

        // daily raw (데이터 있는 날만 옴)
        List<UsageDailyAgg> rawDaily = repo.aggregateDaily(user.getId(), testMode, from, to);

        // date -> agg map
        java.util.Map<String, UsageDailyAgg> map = new java.util.HashMap<>();
        for (UsageDailyAgg d : rawDaily) map.put(d.date(), d);

        // range 전체 날짜를 0 채움
        ArrayNode dailyArr = om.createArrayNode();

        long sumTokensIn = 0L;
        long sumTokensOut = 0L;

        for (int i = 0; i < days; i++) {
            String day = fromD.plusDays(i).toString();
            UsageDailyAgg d = map.get(day);

            long total = (d == null) ? 0L : d.total();
            long cached = (d == null) ? 0L : d.cached();
            long refresh = (d == null) ? 0L : d.refresh();
            long web = (d == null) ? 0L : d.web();
            long alphaCalls = (d == null) ? 0L : d.alphaCalls();
            long openaiCalls = (d == null) ? 0L : d.openaiCalls();
            long tokensIn = (d == null) ? 0L : d.openaiTokensIn();
            long tokensOut = (d == null) ? 0L : d.openaiTokensOut();

            ObjectNode n = om.createObjectNode();
            n.put("date", day);
            n.put("total", total);
            n.put("cached", cached);
            n.put("refresh", refresh);
            n.put("web", web);
            n.put("alphaCalls", alphaCalls);
            n.put("openaiCalls", openaiCalls);
            n.put("tokensIn", tokensIn);
            n.put("tokensOut", tokensOut);
            dailyArr.add(n);

            sumTokensIn += tokensIn;
            sumTokensOut += tokensOut;
        }

        out.set("daily", dailyArr);

        // top tickers
        List<UsageTickerAgg> topTickers = repo.topTickers(
                user.getId(), testMode, from, to, PageRequest.of(0, 10)
        );
        ArrayNode tickArr = om.createArrayNode();
        for (UsageTickerAgg t : topTickers) {
            ObjectNode n = om.createObjectNode();
            n.put("ticker", t.ticker());
            n.put("totalCalls", t.totalCalls());
            n.put("cachedCalls", t.cachedCalls());
            n.put("alphaCalls", t.alphaCalls());
            n.put("openaiCalls", t.openaiCalls());
            tickArr.add(n);
        }
        out.set("topTickers", tickArr);

        // top errors
        List<UsageErrorAgg> topErrors = repo.topErrors(
                user.getId(), testMode, from, to, PageRequest.of(0, 10)
        );
        ArrayNode errArr = om.createArrayNode();
        for (UsageErrorAgg e : topErrors) {
            ObjectNode n = om.createObjectNode();
            n.put("errorText", e.errorText());
            n.put("count", e.count());
            errArr.add(n);
        }
        out.set("topErrors", errArr);

        // totals (+ 비용 추정은 설정값 있으면 계산)
        ObjectNode totals = out.putObject("totals");
        totals.put("tokensIn", sumTokensIn);
        totals.put("tokensOut", sumTokensOut);

        // 비용 추정(선택): 설정으로 단가 넣었을 때만 계산
        double inUsdPer1K = 0.0;
        double outUsdPer1K = 0.0;
        // 필요하면 @Value로 받는 방식 추천 (아래 참고)
        // totals.put("estimatedCostUsd", ...);

        double est = estimateCostUsd(sumTokensIn, sumTokensOut, inUsdPer1K, outUsdPer1K);
        if (est > 0) totals.put("estimatedCostUsd", est);

        return write(out);
    }

    private double estimateCostUsd(long tokensIn, long tokensOut, double inUsdPer1K, double outUsdPer1K) {
        if (tokensIn <= 0 && tokensOut <= 0) return 0.0;
        if (inUsdPer1K <= 0 && outUsdPer1K <= 0) return 0.0;

        double costIn = ((double) tokensIn / 1000.0) * inUsdPer1K;
        double costOut = ((double) tokensOut / 1000.0) * outUsdPer1K;

        // 소수점 6자리 정도면 충분
        return Math.round((costIn + costOut) * 1_000_000.0) / 1_000_000.0;
    }

    private String write(ObjectNode node) {
        try {
            return om.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("json serialization failed: " + e.getMessage(), e);
        }
    }

    private String trim512(String s) {
        if (s == null) return null;
        s = s.strip();
        return s.length() <= 512 ? s : s.substring(0, 512);
    }

    private String safeTrim(String s, int max) {
        if (s == null) return null;
        s = s.strip();
        return (s.length() <= max) ? s : s.substring(0, max);
    }

    @Transactional
    public int deleteOldLogs(User user, int olderThanDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        return repo.deleteByUser_IdAndCreatedAtBefore(user.getId(), cutoff);
    }
}
