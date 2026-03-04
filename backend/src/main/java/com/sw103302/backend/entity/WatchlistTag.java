package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "watchlist_tag",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_watchlist_tag",
                columnNames = {"user_id", "name"}
        )
)
@Getter
public class WatchlistTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 7)
    private String color; // Hex color like #3b82f6

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    private Set<WatchlistItem> watchlistItems = new HashSet<>();

    protected WatchlistTag() {}

    public WatchlistTag(User user, String name, String color) {
        this.user = user;
        this.name = name;
        this.color = color;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
