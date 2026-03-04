package com.sw103302.backend.repository;

import com.sw103302.backend.entity.WatchlistItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findByUser_IdAndTestModeOrderByCreatedAtDesc(Long userId, boolean testMode);

    @EntityGraph(attributePaths = {"tags", "user"})
    @Query("SELECT w FROM WatchlistItem w WHERE w.user.id = :userId AND w.testMode = :testMode ORDER BY w.createdAt DESC")
    List<WatchlistItem> findByUserIdWithTags(@Param("userId") Long userId, @Param("testMode") boolean testMode);

    Optional<WatchlistItem> findByUser_IdAndTickerAndMarketAndTestMode(
            Long userId, String ticker, String market, boolean testMode
    );

    void deleteByUser_IdAndTickerAndMarketAndTestMode(
            Long userId, String ticker, String market, boolean testMode
    );
}
