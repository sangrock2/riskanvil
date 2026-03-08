package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "market_report_history",
        indexes = {
                @Index(name = "idx_hist_cache_created", columnList = "cache_id, created_at")
        }
)
@Getter
public class MarketReportHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cache_id")
    private MarketCache cache;

    @Lob
    @Column(name = "report_text", nullable = false)
    private String reportText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected MarketReportHistory() {}

    public MarketReportHistory(MarketCache cache, String reportText) {
        this.cache = cache;
        this.reportText = reportText;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
