package com.sw103302.backend.repository;

import com.sw103302.backend.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByUser_IdOrderByCreatedAtDesc(Long userId);
    Optional<Portfolio> findByIdAndUser_Id(Long id, Long userId);
    Optional<Portfolio> findByUser_IdAndName(Long userId, String name);
}
