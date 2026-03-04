package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "screener_presets")
@Getter
public class ScreenerPreset {
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

    @Column(name = "filters_json", nullable = false, columnDefinition = "LONGTEXT")
    private String filtersJson;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected ScreenerPreset() {}

    public ScreenerPreset(User user, String name, String description,
                          String filtersJson, Boolean isPublic) {
        this.user = user;
        this.name = name;
        this.description = description;
        this.filtersJson = filtersJson;
        this.isPublic = isPublic != null ? isPublic : false;
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

    public void setFiltersJson(String filtersJson) {
        this.filtersJson = filtersJson;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }
}
