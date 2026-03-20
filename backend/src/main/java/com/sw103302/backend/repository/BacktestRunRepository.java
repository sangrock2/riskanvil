package com.sw103302.backend.repository;

import com.sw103302.backend.dto.BacktestRunSummary;
import com.sw103302.backend.entity.BacktestRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BacktestRunRepository extends JpaRepository<BacktestRun, Long>, JpaSpecificationExecutor<BacktestRun> {
    List<BacktestRun> findByUser_EmailOrderByCreatedAtDesc(String email, Pageable pageable);

    @Query("""
            select new com.sw103302.backend.dto.BacktestRunSummary(
                r.id, r.ticker, r.market, r.strategy, r.totalReturn, r.maxDrawdown, r.sharpe, r.cagr, r.createdAt
            )
            from BacktestRun r
            where r.user.email = :email
            """)
    List<BacktestRunSummary> findSummaryByUserEmail(
            @Param("email") String email,
            Pageable pageable
    );

    @Query(
            value = """
                    select new com.sw103302.backend.dto.BacktestRunSummary(
                        r.id, r.ticker, r.market, r.strategy, r.totalReturn, r.maxDrawdown, r.sharpe, r.cagr, r.createdAt
                    )
                    from BacktestRun r
                    where r.user.email = :email
                      and (:ticker is null or upper(r.ticker) = upper(:ticker))
                      and (:market is null or upper(r.market) = upper(:market))
                      and (:strategy is null or upper(r.strategy) = upper(:strategy))
                    """,
            countQuery = """
                    select count(r.id)
                    from BacktestRun r
                    where r.user.email = :email
                      and (:ticker is null or upper(r.ticker) = upper(:ticker))
                      and (:market is null or upper(r.market) = upper(:market))
                      and (:strategy is null or upper(r.strategy) = upper(:strategy))
                    """
    )
    Page<BacktestRunSummary> findSummaryPageByFilters(
            @Param("email") String email,
            @Param("ticker") String ticker,
            @Param("market") String market,
            @Param("strategy") String strategy,
            Pageable pageable
    );

    Optional<BacktestRun> findByIdAndUser_Email(Long id, String email);
}
