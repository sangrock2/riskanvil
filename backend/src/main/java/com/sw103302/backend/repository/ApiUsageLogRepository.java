package com.sw103302.backend.repository;

import com.sw103302.backend.dto.UsageAgg;
import com.sw103302.backend.dto.UsageDailyAgg;
import com.sw103302.backend.dto.UsageErrorAgg;
import com.sw103302.backend.dto.UsageTickerAgg;
import com.sw103302.backend.entity.ApiUsageLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {

    @Query("""
        select new com.sw103302.backend.dto.UsageAgg(
            u.endpoint,
            count(u),
            sum(case when u.cached = true then 1 else 0 end),
            sum(case when u.refresh = true then 1 else 0 end),
            sum(case when u.web = true then 1 else 0 end),
            coalesce(sum(u.alphaCalls), 0),
            coalesce(sum(u.openaiCalls), 0)
        )
        from ApiUsageLog u
        where u.user.id = :userId
          and u.testMode = :testMode
          and u.createdAt >= :from
          and u.createdAt < :to
        group by u.endpoint
    """)
    List<UsageAgg> aggregateByEndpoint(
            @Param("userId") Long userId,
            @Param("testMode") boolean testMode,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
    select new com.sw103302.backend.dto.UsageDailyAgg(
         cast(function('date', u.createdAt) as string),
         count(u),
         coalesce(sum(case when u.cached = true then 1 else 0 end), 0L),
         coalesce(sum(case when u.refresh = true then 1 else 0 end), 0L),
         coalesce(sum(case when u.web = true then 1 else 0 end), 0L),
         coalesce(sum(u.alphaCalls), 0L),
         coalesce(sum(u.openaiCalls), 0L),
         coalesce(sum(u.openaiTokensIn), 0L),
         coalesce(sum(u.openaiTokensOut), 0L)
     )
     from ApiUsageLog u
     where u.user.id = :userId
       and u.testMode = :testMode
       and u.createdAt >= :from
       and u.createdAt < :to
     group by function('date', u.createdAt)
     order by function('date', u.createdAt) asc
""")
    List<UsageDailyAgg> aggregateDaily(
            @Param("userId") Long userId,
            @Param("testMode") boolean testMode,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // ✅ Top tickers
    @Query("""
        select new com.sw103302.backend.dto.UsageTickerAgg(
            u.ticker,
            count(u),
            sum(case when u.cached = true then 1 else 0 end),
            coalesce(sum(u.alphaCalls), 0),
            coalesce(sum(u.openaiCalls), 0)
        )
        from ApiUsageLog u
        where u.user.id = :userId
          and u.testMode = :testMode
          and u.createdAt >= :from
          and u.createdAt < :to
        group by u.ticker
        order by count(u) desc
    """)
    List<UsageTickerAgg> topTickers(
            @Param("userId") Long userId,
            @Param("testMode") boolean testMode,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    // ✅ Top errors (error_text 있는 것만)
    @Query("""
        select new com.sw103302.backend.dto.UsageErrorAgg(
            u.errorText,
            count(u)
        )
        from ApiUsageLog u
        where u.user.id = :userId
          and u.testMode = :testMode
          and u.createdAt >= :from
          and u.createdAt < :to
          and u.errorText is not null
          and u.errorText <> ''
        group by u.errorText
        order by count(u) desc
    """)
    List<UsageErrorAgg> topErrors(
            @Param("userId") Long userId,
            @Param("testMode") boolean testMode,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    // Delete old logs
    int deleteByUser_IdAndCreatedAtBefore(Long userId, LocalDateTime before);
}
