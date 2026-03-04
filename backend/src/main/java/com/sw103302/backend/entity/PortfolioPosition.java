package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_positions")
@Getter
public class PortfolioPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    @Column(nullable = false, length = 32)
    private String ticker;

    @Column(nullable = false, length = 8)
    private String market;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "entry_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "entry_date")
    private LocalDate entryDate;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected PortfolioPosition() {}

    public PortfolioPosition(Portfolio portfolio, String ticker, String market,
                             BigDecimal quantity, BigDecimal entryPrice,
                             LocalDate entryDate, String notes) {
        this.portfolio = portfolio;
        this.ticker = ticker;
        this.market = market;
        this.quantity = quantity;
        this.entryPrice = entryPrice;
        this.entryDate = entryDate;
        this.notes = notes;
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

    public void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
