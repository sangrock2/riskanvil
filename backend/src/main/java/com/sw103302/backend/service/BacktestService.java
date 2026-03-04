package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.*;
import com.sw103302.backend.entity.BacktestRun;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.BacktestRunRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.BacktestRunSpecs;
import com.sw103302.backend.util.SecurityUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;

import java.util.List;


/**
 * AI 백테스트 실행/저장 및 사용자별 백테스트 이력 조회 서비스.
 * 백테스트 응답에서 성능 요약 지표를 추출해 별도 컬럼으로 보관한다.
 */
@Service
public class BacktestService {
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final BacktestRunRepository backtestRunRepository;

    public BacktestService(AiClient aiClient, ObjectMapper objectMapper, UserRepository userRepository, BacktestRunRepository backtestRunRepository) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.backtestRunRepository = backtestRunRepository;
    }

    /**
     * 백테스트를 실행하고 결과를 DB에 저장한 뒤 실행 ID와 결과를 반환한다.
     */
    @Transactional
    public BacktestResponse runAndSave(BacktestRequest req) {
        String email = SecurityUtil.requireCurrentEmail();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found"));

        AiBacktestRequest aiReq = new AiBacktestRequest(
                req.ticker().trim(),
                req.market(),
                req.strategy(),
                req.start(),
                req.end(),
                req.initialCapital() != null ? req.initialCapital() : 1_000_000.0,
                req.feeBps() != null ? req.feeBps() : 5.0,
                600
        );

        String resJson = aiClient.backtest(aiReq);
        String reqJson;

        try {
            reqJson = objectMapper.writeValueAsString(aiReq);      // 요청은 항상 작아서 안전
        } catch (Exception e) {
            throw new IllegalStateException("json serialization failed (request): " + e.getMessage(), e);
        }

        Object resultObj;
        try {
            resultObj = objectMapper.readValue(resJson, Object.class);
        } catch (Exception e) {
            throw new IllegalStateException("stored json parse failed: " + e.getMessage(), e);
        }

        Double totalReturn = null, maxDrawdown = null, sharpe = null, cagr = null;
        try {
            var root = objectMapper.readTree(resJson);
            var summary = root.path("summary");
            if (summary.path("totalReturn").isNumber()) totalReturn = summary.get("totalReturn").asDouble();
            if (summary.path("maxDrawdown").isNumber()) maxDrawdown = summary.get("maxDrawdown").asDouble();
            if (summary.path("sharpe").isNumber()) sharpe = summary.get("sharpe").asDouble();
            if (summary.path("cagr").isNumber()) cagr = summary.get("cagr").asDouble();
        } catch (Exception ignore) { /* 요약 저장 실패해도 전체 저장은 가능하게 */ }

        BacktestRun saved = backtestRunRepository.save(
                new BacktestRun(user, aiReq.ticker(), aiReq.market(), aiReq.strategy(),
                        reqJson, resJson, totalReturn, maxDrawdown, sharpe, cagr)
        );

        return new BacktestResponse(saved.getId(), resultObj);
    }

    /**
     * 현재 로그인 사용자의 최근 백테스트 이력을 제한 건수만큼 조회한다.
     */
    @Transactional(readOnly = true)
    public List<BacktestRunSummary> myHistory(int limit) {
        String email = SecurityUtil.requireCurrentEmail();

        var list = backtestRunRepository.findByUser_EmailOrderByCreatedAtDesc(email, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
        return list.stream()
                .map(r -> new BacktestRunSummary(
                        r.getId(), r.getTicker(), r.getMarket(), r.getStrategy(),
                        r.getTotalReturn(), r.getMaxDrawdown(), r.getSharpe(), r.getCagr(), r.getCreatedAt()
                ))
                .toList();
    }

    /**
     * 조건 필터/정렬을 적용한 백테스트 이력 페이징 조회.
     */
    @Transactional(readOnly = true)
    public PageResponse<BacktestRunSummary> myBacktestHistoryPage(int page, int size, String sort, String ticker, String market, String strategy) {
        String email = SecurityUtil.requireCurrentEmail();

        Pageable pageable = toPageable(page, size, sort);

        var spec = Specification
                .where(BacktestRunSpecs.userEmail(email))
                .and(BacktestRunSpecs.tickerEq(ticker))
                .and(BacktestRunSpecs.marketEq(market))
                .and(BacktestRunSpecs.strategyEq(strategy));

        var result = backtestRunRepository.findAll(spec, pageable);

        var items = result.getContent().stream()
                .map(r -> new BacktestRunSummary(
                        r.getId(), r.getTicker(), r.getMarket(), r.getStrategy(),
                        r.getTotalReturn(), r.getMaxDrawdown(), r.getSharpe(), r.getCagr(),
                        r.getCreatedAt()
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
     * 단건 백테스트 결과 JSON을 반환한다.
     * 요청 사용자의 소유 데이터인지 추가로 확인한다.
     */
    @Transactional(readOnly = true)
    public String myDetail(Long id) {
        String email = SecurityUtil.requireCurrentEmail();

        BacktestRun run = backtestRunRepository.findByIdAndUser_Email(id, email)
                .orElseThrow(() -> new SecurityException("Backtest run not found or access denied"));

        // Additional ownership verification (defense in depth)
        if (run.getUser() != null && !email.equals(run.getUser().getEmail())) {
            throw new SecurityException("Access denied: user does not own this backtest run");
        }

        return run.getResponseJson();
    }

    /**
     * 안전한 정렬 필드 화이트리스트를 적용해 Pageable을 생성한다.
     */
    private Pageable toPageable(int page, int size, String sort) {
        int p = Math.max(0, page);
        int s = Math.max(1, Math.min(size, 100));

        // Whitelist of allowed sort fields to prevent SQL injection
        final var ALLOWED_SORT_FIELDS = List.of(
            "id", "ticker", "market", "strategy", "totalReturn", "maxDrawdown", "sharpe", "cagr", "createdAt"
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
