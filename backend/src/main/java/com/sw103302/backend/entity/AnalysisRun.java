package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "analysis_runs", indexes = {
        @Index(name = "idx_analysis_runs_user_created", columnList = "user_id, createdAt")
})
@Getter
public class AnalysisRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "ticker", nullable = false, length = 40)
    private String ticker;

    @Column(name = "market", nullable = false, length = 10)
    private String market;

    @Lob
    @Column(name = "request_json", nullable = false)
    private String requestJson;

    @Lob
    @Column(name = "response_json", nullable = false)
    private String responseJson;

    @Column(name = "action", length = 10)
    private String action;

    @Column(name = "confidence")
    private Double confidence;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AnalysisRun() {}

    public AnalysisRun(User user, String ticker, String market, String requestJson, String responseJson, String action, Double confidence) {
        this.user = user;
        this.ticker = ticker;
        this.market = market;
        this.requestJson = requestJson;
        this.responseJson = responseJson;
        this.action = action;
        this.confidence = confidence;
    }
}
