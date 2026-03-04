package com.sw103302.backend.util;

import com.sw103302.backend.entity.BacktestRun;
import org.springframework.data.jpa.domain.Specification;

public class BacktestRunSpecs {
    public static Specification<BacktestRun> userEmail(String email) {
        return (root, query, cb) -> cb.equal(root.get("user").get("email"), email);
    }

    public static Specification<BacktestRun> tickerEq(String ticker) {
        return (root, query, cb) -> {
            if (ticker == null || ticker.isBlank()) return cb.conjunction();
            return cb.equal(root.get("ticker"), ticker.trim());
        };
    }

    public static Specification<BacktestRun> marketEq(String market) {
        return (root, query, cb) -> {
            if (market == null || market.isBlank()) return cb.conjunction();
            return cb.equal(root.get("market"), market.trim());
        };
    }

    public static Specification<BacktestRun> strategyEq(String strategy) {
        return (root, query, cb) -> {
            if (strategy == null || strategy.isBlank()) return cb.conjunction();
            return cb.equal(root.get("strategy"), strategy.trim());
        };
    }
}
