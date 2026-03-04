package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "paper_positions")
@Getter
public class PaperPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private PaperAccount account;

    @Column(nullable = false, length = 32)
    private String ticker;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "avg_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal avgPrice;

    @Column(name = "total_cost", nullable = false, precision = 20, scale = 4)
    private BigDecimal totalCost;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected PaperPosition() {}

    public PaperPosition(PaperAccount account, String ticker, BigDecimal quantity, BigDecimal avgPrice, BigDecimal totalCost) {
        this.account = account;
        this.ticker = ticker;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
        this.totalCost = totalCost;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }
}
