package com.sw103302.backend.repository;

import com.sw103302.backend.entity.MarketCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MarketCacheRepository extends JpaRepository<MarketCache, Long> {

    Optional<MarketCache> findByUser_IdAndTickerAndMarketAndTestModeAndDaysAndNewsLimit(
            Long userId, String ticker, String market, boolean testMode, int days, int newsLimit
    );

    List<MarketCache> findTop200ByUser_IdAndMarketAndTestModeAndInsightsJsonIsNotNullOrderByInsightsUpdatedAtDesc(
            Long userId, String market, boolean testMode
    );

    @Query("SELECT mc FROM MarketCache mc WHERE mc.user.id = :userId AND mc.ticker IN :tickers AND mc.market = :market AND mc.testMode = :testMode AND mc.days = :days AND mc.newsLimit = :newsLimit")
    List<MarketCache> findByUser_IdAndTickerInAndMarketAndTestMode(
            @Param("userId") Long userId,
            @Param("tickers") Set<String> tickers,
            @Param("market") String market,
            @Param("testMode") boolean testMode,
            @Param("days") int days,
            @Param("newsLimit") int newsLimit
    );
}
