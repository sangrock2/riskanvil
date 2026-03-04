package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_settings")
@Getter
public class UserSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    @Column(name = "email_on_alerts", nullable = false)
    private boolean emailOnAlerts = true;

    @Column(name = "daily_summary_enabled", nullable = false)
    private boolean dailySummaryEnabled = false;

    @Column(length = 20)
    private String theme = "dark";

    @Column(length = 10)
    private String language = "ko";

    @Column(name = "default_market", length = 8)
    private String defaultMarket = "US";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected UserSettings() {}

    public UserSettings(User user) {
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void setTotpEnabled(boolean totpEnabled) {
        this.totpEnabled = totpEnabled;
    }

    public void setEmailOnAlerts(boolean emailOnAlerts) {
        this.emailOnAlerts = emailOnAlerts;
    }

    public void setDailySummaryEnabled(boolean dailySummaryEnabled) {
        this.dailySummaryEnabled = dailySummaryEnabled;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setDefaultMarket(String defaultMarket) {
        this.defaultMarket = defaultMarket;
    }
}
