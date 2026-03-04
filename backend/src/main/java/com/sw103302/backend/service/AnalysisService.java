package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.component.InFlightDeduplicator;
import com.sw103302.backend.dto.*;
import com.sw103302.backend.entity.AnalysisRun;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.AnalysisRunRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.AnalysisRunSpecs;
import com.sw103302.backend.util.SecurityUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 분석 실행/저장과 사용자별 분석 이력 조회를 담당한다.
 * <p>
 * - 동일 요청 중복 실행 방지(dedup)<br>
 * - AI 응답의 핵심 필드(action/confidence) 추출<br>
 * - 필터/정렬 기반 페이징 이력 조회
 */
@Service
public class AnalysisService {
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final InFlightDeduplicator dedup;

    public AnalysisService(AiClient aiClient, ObjectMapper objectMapper, UserRepository userRepository, AnalysisRunRepository analysisRunRepository, InFlightDeduplicator dedup) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.dedup = dedup;
    }

    /**
     * 분석 요청을 실행하고 결과를 DB에 저장한 뒤 API 응답 형태로 반환한다.
     * 동일 사용자/동일 파라미터 요청은 dedup 키로 병합한다.
     */
    @Transactional
    public AnalysisResponse analyzeAndSave(AnalysisRequest req) {
        String email = SecurityUtil.requireCurrentEmail();
        String key = dedupKey(email, req);
        return dedup.execute(key, () -> analyzeAndSaveInternal(req, email));
    }

    /**
     * 실질적인 분석 처리 로직.
     * AI 호출, JSON 파싱/검증, 실행 이력 저장을 순차 수행한다.
     */
    private AnalysisResponse analyzeAndSaveInternal(AnalysisRequest req, String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found"));

        AiAnalyzeRequest aiReq = new AiAnalyzeRequest(
                req.ticker().trim(),
                req.market(),
                req.horizonDays() != null ? req.horizonDays() : 252,
                req.riskProfile() != null ? req.riskProfile() : "balanced"
        );

        String resJson = aiClient.analyze(aiReq);

        String action = null;
        Double confidence = null;

        try {
            JsonNode root = objectMapper.readTree(resJson);

            JsonNode src = root.path("decision");

            if (src.isMissingNode() || !src.isObject()) src = root;

            JsonNode a = src.get("action");
            JsonNode c = src.get("confidence");

            if (a != null && a.isTextual()) action = a.asText();
            if (c != null && c.isNumber()) confidence = c.asDouble();
        } catch (Exception e) {
            // AI가 JSON이 아닌 값을 보내면 저장/응답 전에 바로 실패시키는 게 안전
            throw new IllegalStateException("AI response is not valid json: " + e.getMessage(), e);
        }

        final String reqJson;
        try {
            reqJson = objectMapper.writeValueAsString(aiReq);
        } catch (Exception e) {
            throw new IllegalStateException("json serialization failed (request): " + e.getMessage(), e);
        }

        AnalysisRun saved = analysisRunRepository.save(
                new AnalysisRun(user, aiReq.ticker(), aiReq.market(), reqJson, resJson, action, confidence)
        );

        try {
            Object resultObj = objectMapper.readValue(resJson, Object.class);
            return new AnalysisResponse(saved.getId(), resultObj);

            // 만약 AnalysisResponse가 String result 라면:
            // return new AnalysisResponse(saved.getId(), resJson);

        } catch (Exception e) {
            throw new IllegalStateException("json parse failed (response): " + e.getMessage(), e);
        }
    }

    /**
     * 중복 실행 방지를 위한 사용자별 요청 식별 키를 생성한다.
     */
    private String dedupKey(String email, AnalysisRequest req) {
        String ticker = req.ticker() == null ? "" : req.ticker().trim().toUpperCase();
        String market = req.market() == null ? "US" : req.market().trim().toUpperCase();
        int horizon = req.horizonDays() != null ? req.horizonDays() : 252;
        String risk = req.riskProfile() != null ? req.riskProfile().trim().toLowerCase() : "balanced";
        return "analysis:" + email + ":" + ticker + ":" + market + ":" + horizon + ":" + risk;
    }

    /**
     * 현재 로그인 사용자의 최근 분석 이력을 제한 건수만큼 조회한다.
     */
    @Transactional(readOnly = true)
    public List<AnalysisRunSummary> myHistory(int limit) {
        String email = SecurityUtil.requireCurrentEmail();

        var runs = analysisRunRepository.findByUser_EmailOrderByCreatedAtDesc(
                email, PageRequest.of(0, Math.max(1, Math.min(limit, 100)))
        );

        return runs.stream()
                .map(r -> new AnalysisRunSummary(
                        r.getId(), r.getTicker(), r.getMarket(),
                        r.getAction(), r.getConfidence(), r.getCreatedAt()
                ))
                .toList();
    }

    /**
     * 필터/정렬/기간 조건을 적용한 분석 이력 페이징 조회.
     */
    @Transactional(readOnly = true)
    public PageResponse<AnalysisRunSummary> myHistoryPage(int page, int size, String sort, String ticker, String market, String action, LocalDate from, LocalDate to) {
        String email = SecurityUtil.requireCurrentEmail();

        Pageable pageable = toPageable(page, size, sort);

        var spec = Specification
                .where(AnalysisRunSpecs.userEmail(email))
                .and(AnalysisRunSpecs.tickerEq(ticker))
                .and(AnalysisRunSpecs.marketEq(market))
                .and(AnalysisRunSpecs.actionEq(action))
                .and(AnalysisRunSpecs.createdBetween(from, to));

        var result = analysisRunRepository.findAll(spec, pageable);

        var items = result.getContent().stream()
                .map(r -> new AnalysisRunSummary(
                        r.getId(), r.getTicker(), r.getMarket(),
                        r.getAction(), r.getConfidence(), r.getCreatedAt()
                ))
                .toList();

        return new PageResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    /**
     * 단건 분석 결과 JSON 원문을 반환한다.
     * 조회 대상이 현재 사용자 소유인지 추가 검증한다.
     */
    @Transactional(readOnly = true)
    public String myRunDetail(Long id) {
        String email = SecurityUtil.requireCurrentEmail();

        AnalysisRun run = analysisRunRepository.findByIdAndUser_Email(id, email)
                .orElseThrow(() -> new SecurityException("Analysis run not found or access denied"));

        // Additional ownership verification (defense in depth)
        if (run.getUser() != null && !email.equals(run.getUser().getEmail())) {
            throw new SecurityException("Access denied: user does not own this analysis run");
        }

        return run.getResponseJson();
    }

    /**
     * 클라이언트 입력 정렬 문자열을 안전한 Pageable 객체로 변환한다.
     * 허용 목록에 없는 정렬 필드는 기본값(createdAt desc)으로 대체한다.
     */
    private Pageable toPageable(int page, int size, String sort) {
        int p = Math.max(0, page);
        int s = Math.max(1, Math.min(size, 100));

        // Whitelist of allowed sort fields to prevent SQL injection
        final var ALLOWED_SORT_FIELDS = List.of(
            "id", "ticker", "market", "action", "confidence", "createdAt"
        );

        // sort 예: "createdAt,desc" / "createdAt,asc"
        String prop = "createdAt";
        Sort.Direction dir = Sort.Direction.DESC;

        if (sort != null && !sort.isBlank()) {
            var parts = sort.split(",", 2);
            if (parts.length >= 1 && !parts[0].isBlank()) {
                String requestedProp = parts[0].trim();
                // Validate against whitelist
                if (ALLOWED_SORT_FIELDS.contains(requestedProp)) {
                    prop = requestedProp;
                } else {
                    // Log security warning and use default
                    System.err.println("SECURITY WARNING: Invalid sort field attempted: " + requestedProp);
                    // prop remains "createdAt" (default)
                }
            }
            if (parts.length == 2 && "asc".equalsIgnoreCase(parts[1].trim())) dir = Sort.Direction.ASC;
        }

        return PageRequest.of(p, s, Sort.by(dir, prop));
    }
}
