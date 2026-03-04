package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dividends")
@Getter
public class Dividend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_position_id")
    private PortfolioPosition portfolioPosition;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency = "USD";

    @Column(name = "ex_date", nullable = false)
    private LocalDate exDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "declared_date")
    private LocalDate declaredDate;

    @Column(length = 20)
    private String frequency;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Dividend() {}

    public Dividend(PortfolioPosition portfolioPosition, BigDecimal amount, String currency,
                    LocalDate exDate, LocalDate paymentDate, LocalDate recordDate,
                    LocalDate declaredDate, String frequency) {
        this.portfolioPosition = portfolioPosition;
        this.amount = amount;
        this.currency = currency;
        this.exDate = exDate;
        this.paymentDate = paymentDate;
        this.recordDate = recordDate;
        this.declaredDate = declaredDate;
        this.frequency = frequency;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Setters for flexible object construction
    public void setPortfolioPosition(PortfolioPosition portfolioPosition) {
        this.portfolioPosition = portfolioPosition;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setExDate(LocalDate exDate) {
        this.exDate = exDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public void setDeclaredDate(LocalDate declaredDate) {
        this.declaredDate = declaredDate;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }
}
