package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "api_usage_log",
        indexes = {
                @Index(name = "idx_usage_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_usage_request_id", columnList = "request_id")
        }
)
@Getter
public class ApiUsageLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 32)
    private String endpoint; // INSIGHTS / REPORT

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

    @Column(nullable = false)
    private boolean cached;

    @Column(nullable = false)
    private boolean refresh;

    @Column(nullable = false)
    private boolean web;

    @Column(name = "alpha_calls")
    private Integer alphaCalls;

    @Column(name = "openai_calls")
    private Integer openaiCalls;

    @Column(name = "openai_model", length = 64)
    private String openaiModel;

    @Column(name = "openai_tokens_in")
    private Integer openaiTokensIn;

    @Column(name = "openai_tokens_out")
    private Integer openaiTokensOut;

    @Column(name = "error_text", length = 512)
    private String errorText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "duration_ms")
    private Long durationMs;

    protected ApiUsageLog() {}

    public ApiUsageLog(User user) {
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    public void setKey(String endpoint, String ticker, String market, boolean testMode, int days, int newsLimit) {
        this.endpoint = endpoint;
        this.ticker = ticker;
        this.market = market;
        this.testMode = testMode;
        this.days = days;
        this.newsLimit = newsLimit;
    }

    public void setFlags(boolean cached, boolean refresh, boolean web) {
        this.cached = cached;
        this.refresh = refresh;
        this.web = web;
    }

    public void setMeta(Integer alphaCalls, Integer openaiCalls, String openaiModel, Integer openaiTokensIn, Integer openaiTokensOut) {
        this.alphaCalls = alphaCalls;
        this.openaiCalls = openaiCalls;
        this.openaiModel = openaiModel;
        this.openaiTokensIn = openaiTokensIn;
        this.openaiTokensOut = openaiTokensOut;
    }

    public void setTrace(String requestId, Integer httpStatus, Long durationMs) {
        this.requestId = requestId;
        this.httpStatus = httpStatus;
        this.durationMs = durationMs;
    }

    public void setErrorText(String errorText) {
        this.errorText = errorText;
    }
}
