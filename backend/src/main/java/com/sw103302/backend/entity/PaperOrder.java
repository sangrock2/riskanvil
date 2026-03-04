package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "paper_orders")
@Getter
public class PaperOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private PaperAccount account;

    @Column(nullable = false, length = 32)
    private String ticker;

    @Column(nullable = false, length = 4)
    private String direction;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal price;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal commission;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PaperOrder() {}

    public PaperOrder(PaperAccount account, String ticker, String direction,
                      BigDecimal quantity, BigDecimal price, BigDecimal amount, BigDecimal commission) {
        this.account = account;
        this.ticker = ticker;
        this.direction = direction;
        this.quantity = quantity;
        this.price = price;
        this.amount = amount;
        this.commission = commission;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
