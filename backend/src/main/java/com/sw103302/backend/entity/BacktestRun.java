package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "backtest_runs", indexes = {
        @Index(name = "idx_backtest_runs_user_created", columnList = "user_id, created_at")
})
@Getter
public class BacktestRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name="user_id", nullable=false)
    private User user;

    @Column(name="ticker", nullable=false, length=40)
    private String ticker;

    @Column(name="market", nullable=false, length=10)
    private String market;

    @Column(name="strategy", nullable=false, length=30)
    private String strategy;

    @Lob @Column(name = "request_json", columnDefinition = "LONGTEXT", nullable = false)
    private String requestJson;

    @Lob @Column(name = "response_json", columnDefinition = "LONGTEXT", nullable = false)
    private String responseJson;

    @Column(name="total_return")
    private Double totalReturn;

    @Column(name="max_drawdown")
    private Double maxDrawdown;

    @Column(name="sharpe")
    private Double sharpe;

    @Column(name="cagr")
    private Double cagr;

    @CreationTimestamp
    @Column(name="created_at", nullable=false, updatable=false)
    private Instant createdAt;

    protected BacktestRun() {}

    public BacktestRun(User user, String ticker, String market, String strategy, String requestJson, String responseJson, Double totalReturn, Double maxDrawdown, Double sharpe, Double cagr) {
        this.user = user;
        this.ticker = ticker;
        this.market = market;
        this.strategy = strategy;
        this.requestJson = requestJson;
        this.responseJson = responseJson;
        this.totalReturn = totalReturn;
        this.maxDrawdown = maxDrawdown;
        this.sharpe = sharpe;
        this.cagr = cagr;
    }
}
