package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "paper_accounts")
@Getter
public class PaperAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 4)
    private String market;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal balance;

    @Column(name = "initial_balance", nullable = false, precision = 20, scale = 4)
    private BigDecimal initialBalance;

    @Column(nullable = false, length = 4)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected PaperAccount() {}

    public PaperAccount(User user, String market, BigDecimal initialBalance, String currency) {
        this.user = user;
        this.market = market;
        this.balance = initialBalance;
        this.initialBalance = initialBalance;
        this.currency = currency;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public void resetBalance() {
        this.balance = this.initialBalance;
    }
}
