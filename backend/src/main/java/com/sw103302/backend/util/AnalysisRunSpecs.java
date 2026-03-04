package com.sw103302.backend.util;

import com.sw103302.backend.entity.AnalysisRun;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class AnalysisRunSpecs {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    public static Specification<AnalysisRun> userEmail(String email) {
        return (root, query, cb) -> cb.equal(root.get("user").get("email"), email);
    }

    public static Specification<AnalysisRun> tickerEq(String ticker) {
        return (root, query, cb) -> {
            if (ticker == null || ticker.isBlank()) return cb.conjunction();
            return cb.equal(root.get("ticker"), ticker.trim());
        };
    }

    public static Specification<AnalysisRun> marketEq(String market) {
        return (root, query, cb) -> {
            if (market == null || market.isBlank()) return cb.conjunction();
            return cb.equal(root.get("market"), market.trim());
        };
    }

    public static Specification<AnalysisRun> actionEq(String action) {
        return (root, query, cb) -> {
            if (action == null || action.isBlank()) return cb.conjunction();
            return cb.equal(root.get("action"), action.trim());
        };
    }

    public static Specification<AnalysisRun> createdBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return cb.conjunction();

            var createdAt = root.get("createdAt").as(Instant.class);; // AnalysisRun 필드명이 createdAt 맞다는 가정

            Instant fromI = (from != null) ? from.atStartOfDay(ZONE).toInstant() : null;
            Instant toI = (to != null) ? to.plusDays(1).atStartOfDay(ZONE).toInstant() : null; // to 포함: null;

            if (fromI != null && toI != null) {
                return cb.between(createdAt, fromI, toI);
            } else if (fromI != null) {
                return cb.greaterThanOrEqualTo(createdAt, fromI);
            } else {
                return cb.lessThan(createdAt, toI);
            }
        };
    }
}
