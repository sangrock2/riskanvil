package com.sw103302.backend.repository;

import com.sw103302.backend.entity.WatchlistTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistTagRepository extends JpaRepository<WatchlistTag, Long> {
    List<WatchlistTag> findByUser_IdOrderByNameAsc(Long userId);
    Optional<WatchlistTag> findByUser_IdAndName(Long userId, String name);
    void deleteByUser_IdAndId(Long userId, Long tagId);
}
