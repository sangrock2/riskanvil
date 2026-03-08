package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "market_cache",
    uniqueConstraints = @UniqueConstraint(
            name = "uk_market_cache_key",
            columnNames = {"user_id", "ticker", "market", "test_mode", "days", "news_limit"}
    )
)
@Getter
public class MarketCache {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 32)
    private String ticker;

    @Column(nullable = false, length = 8)
    private String market;

    @Column(name = "test_mode", nullable = false)
    private boolean testMode;

    @Column(nullable = false)
    private int days;

    @Column(name = "news_limit", nullable = false)
    private int newsLimit;

    @Lob
    @Column(name = "insights_json")
    private String insightsJson;

    @Column(name = "insights_updated_at")
    private LocalDateTime insightsUpdatedAt;

    @Lob
    @Column(name = "report_text")
    private String reportText;

    @Column(name = "report_updated_at")
    private LocalDateTime reportUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    protected MarketCache() {}

    public MarketCache(User user, String ticker, String market, boolean testMode, int days, int newsLimit) {
        this.user = user;
        this.ticker = ticker;
        this.market = market;
        this.testMode = testMode;
        this.days = days;
        this.newsLimit = newsLimit;
    }

    public boolean isTestMode() { return testMode; }

    public void setInsightsJson(String insightsJson) { this.insightsJson = insightsJson; }
    public void setInsightsUpdatedAt(LocalDateTime insightsUpdatedAt) { this.insightsUpdatedAt = insightsUpdatedAt; }
    public void setReportText(String reportText) { this.reportText = reportText; }
    public void setReportUpdatedAt(LocalDateTime reportUpdatedAt) { this.reportUpdatedAt = reportUpdatedAt; }

}
