package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "portfolios")
@Getter
public class Portfolio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "target_return", precision = 5, scale = 2)
    private BigDecimal targetReturn;

    @Column(name = "risk_profile", length = 20)
    private String riskProfile;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PortfolioPosition> positions = new ArrayList<>();

    protected Portfolio() {}

    public Portfolio(User user, String name, String description, BigDecimal targetReturn, String riskProfile) {
        this.user = user;
        this.name = name;
        this.description = description;
        this.targetReturn = targetReturn;
        this.riskProfile = riskProfile;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTargetReturn(BigDecimal targetReturn) {
        this.targetReturn = targetReturn;
    }

    public void setRiskProfile(String riskProfile) {
        this.riskProfile = riskProfile;
    }
}
