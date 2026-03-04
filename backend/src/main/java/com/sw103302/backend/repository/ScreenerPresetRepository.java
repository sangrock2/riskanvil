package com.sw103302.backend.repository;

import com.sw103302.backend.entity.ScreenerPreset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScreenerPresetRepository extends JpaRepository<ScreenerPreset, Long> {
    List<ScreenerPreset> findByUser_IdOrderByCreatedAtDesc(Long userId);
    List<ScreenerPreset> findByIsPublicTrueOrderByCreatedAtDesc();
    Optional<ScreenerPreset> findByUser_IdAndName(Long userId, String name);
}
