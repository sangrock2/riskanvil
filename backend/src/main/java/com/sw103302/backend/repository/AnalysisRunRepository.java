package com.sw103302.backend.repository;

import com.sw103302.backend.dto.AnalysisRunSummary;
import com.sw103302.backend.entity.AnalysisRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.Instant;
import java.util.Optional;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long>, JpaSpecificationExecutor<AnalysisRun> {
    List<AnalysisRun> findByUser_EmailOrderByCreatedAtDesc(String email, Pageable pageable);

    @Query("""
            select new com.sw103302.backend.dto.AnalysisRunSummary(
                r.id, r.ticker, r.market, r.action, r.confidence, r.createdAt
            )
            from AnalysisRun r
            where r.user.email = :email
            """)
    List<AnalysisRunSummary> findSummaryByUserEmail(
            @Param("email") String email,
            Pageable pageable
    );

    @Query(
            value = """
                    select new com.sw103302.backend.dto.AnalysisRunSummary(
                        r.id, r.ticker, r.market, r.action, r.confidence, r.createdAt
                    )
                    from AnalysisRun r
                    where r.user.email = :email
                      and (:ticker is null or upper(r.ticker) = upper(:ticker))
                      and (:market is null or upper(r.market) = upper(:market))
                      and (:action is null or upper(r.action) = upper(:action))
                      and (:fromInstant is null or r.createdAt >= :fromInstant)
                      and (:toExclusive is null or r.createdAt < :toExclusive)
                    """,
            countQuery = """
                    select count(r.id)
                    from AnalysisRun r
                    where r.user.email = :email
                      and (:ticker is null or upper(r.ticker) = upper(:ticker))
                      and (:market is null or upper(r.market) = upper(:market))
                      and (:action is null or upper(r.action) = upper(:action))
                      and (:fromInstant is null or r.createdAt >= :fromInstant)
                      and (:toExclusive is null or r.createdAt < :toExclusive)
                    """
    )
    Page<AnalysisRunSummary> findSummaryPageByFilters(
            @Param("email") String email,
            @Param("ticker") String ticker,
            @Param("market") String market,
            @Param("action") String action,
            @Param("fromInstant") Instant fromInstant,
            @Param("toExclusive") Instant toExclusive,
            Pageable pageable
    );

    Optional<AnalysisRun> findByIdAndUser_Email(Long id, String email);
}
