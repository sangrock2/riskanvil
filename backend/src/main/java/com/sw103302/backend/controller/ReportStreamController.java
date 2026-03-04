package com.sw103302.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.SseEmitterRegistry;
import com.sw103302.backend.dto.InsightRequest;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.service.MarketCacheService;
import com.sw103302.backend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/market")
@Tag(name = "Report Streaming", description = "Server-Sent Events for real-time report generation")
public class ReportStreamController {
    private final UserRepository userRepository;
    private final MarketCacheService marketCacheService;
    private final Executor sseExecutor;
    private final ObjectMapper om;
    private final SseEmitterRegistry sseRegistry;

    public ReportStreamController(UserRepository userRepository, MarketCacheService marketCacheService, @Qualifier("sseExecutor") Executor sseExecutor, ObjectMapper om, SseEmitterRegistry sseRegistry) {
        this.userRepository = userRepository;
        this.marketCacheService = marketCacheService;
        this.sseExecutor = sseExecutor;
        this.om = om;
        this.sseRegistry = sseRegistry;
    }

    @PostMapping(value = "/report/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream AI report", description = "Generate AI report via Server-Sent Events for real-time streaming")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SSE stream established"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<SseEmitter> reportStream(
            @Parameter(description = "Use test data") @RequestParam(defaultValue = "false") boolean test,
            @Parameter(description = "Force refresh cache") @RequestParam(defaultValue = "false") boolean refresh,
            @Parameter(description = "Include web search data") @RequestParam(defaultValue = "true") boolean web,
            @RequestBody InsightRequest req) {
        String email = SecurityUtil.currentEmail();
        if (email == null) throw new IllegalStateException("unauthenticated");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found"));

        // 10분 타임아웃(원하면 늘려도 됨)
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        sseRegistry.register(emitter);

        // SSE buffering 방지용 헤더(로컬에서도 도움)
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        headers.add(HttpHeaders.CONNECTION, "keep-alive");
        headers.add("X-Accel-Buffering", "no"); // (nginx 있을 때 특히 중요)

        sseExecutor.execute(() -> {
            try {
                // ✅ 1) 연결 즉시 1개 보내서 브라우저에서 Response가 바로 보이게(flush)
                emitter.send(SseEmitter.event().name("open").data("connected"));

                // ✅ 2) 여기서는 currentUser() 쓰면 안 됨 -> getReportForUser 사용
                String json = marketCacheService.getReportForUser(user, req, test, refresh, web);
                String reportText = extractReportText(json);

                // ✅ SSE로 잘라서 전송 (delta)
                int chunkSize = 600; // 너무 크면 UI 갱신이 굼뜸
                for (int i = 0; i < reportText.length(); i += chunkSize) {
                    String chunk = reportText.substring(i, Math.min(reportText.length(), i + chunkSize));
                    emitter.send(SseEmitter.event().name("delta").data(chunk));
                }

                // ✅ 끝
                emitter.send(SseEmitter.event().name("done").data("ok"));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage() == null ? "internal_error" : e.getMessage()));
                } catch (Exception ignore) {}
                emitter.completeWithError(e);
            }
        });

        return ResponseEntity.ok().headers(headers).body(emitter);
    }

    private String extractReportText(String json) {
        try {
            JsonNode n = om.readTree(json);
            JsonNode r = n.get("report");
            if (r != null && r.isTextual()) return r.asText("");
        } catch (Exception ignored) {}
        // 혹시 JSON이 아니라면 그대로 텍스트로
        return json == null ? "" : json;
    }
}
