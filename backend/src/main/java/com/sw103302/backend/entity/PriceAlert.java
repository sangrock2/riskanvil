package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_alerts")
@Getter
public class PriceAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 32)
    private String ticker;

    @Column(nullable = false, length = 8)
    private String market;

    @Column(name = "alert_type", nullable = false, length = 20)
    private String alertType;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal threshold;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @Column(name = "notification_sent")
    private Boolean notificationSent = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PriceAlert() {}

    public PriceAlert(User user, String ticker, String market,
                      String alertType, BigDecimal threshold) {
        this.user = user;
        this.ticker = ticker;
        this.market = market;
        this.alertType = alertType;
        this.threshold = threshold;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setTriggeredAt(LocalDateTime triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public void setNotificationSent(Boolean notificationSent) {
        this.notificationSent = notificationSent;
    }
}
