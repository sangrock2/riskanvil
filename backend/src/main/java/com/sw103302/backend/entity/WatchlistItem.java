package com.sw103302.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "watchlist",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_watchlist",
                columnNames = {"user_id", "ticker", "market", "test_mode"}
        )
)
@Getter
public class WatchlistItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 32)
    private String ticker;

    @Column(nullable = false, length = 8)
    private String market;

    @Column(name = "test_mode", nullable = false)
    private boolean testMode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 500)
    private String notes; // User notes

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "watchlist_item_tag",
            joinColumns = @JoinColumn(name = "watchlist_item_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<WatchlistTag> tags = new HashSet<>();

    protected WatchlistItem() {}

    public WatchlistItem(User user, String ticker, String market, boolean testMode) {
        this.user = user;
        this.ticker = ticker;
        this.market = market;
        this.testMode = testMode;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void addTag(WatchlistTag tag) {
        this.tags.add(tag);
        tag.getWatchlistItems().add(this);
    }

    public void removeTag(WatchlistTag tag) {
        this.tags.remove(tag);
        tag.getWatchlistItems().remove(this);
    }

    public void clearTags() {
        for (WatchlistTag tag : new HashSet<>(tags)) {
            removeTag(tag);
        }
    }
}
