package com.sw103302.backend.repository;

import com.sw103302.backend.entity.MarketReportHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketReportHistoryRepository extends JpaRepository<MarketReportHistory, Long> {
    List<MarketReportHistory> findTop20ByCache_IdOrderByCreatedAtDesc(Long cacheId);
}
