package com.sw103302.backend.repository;

import com.sw103302.backend.entity.PaperPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperPositionRepository extends JpaRepository<PaperPosition, Long> {
    List<PaperPosition> findByAccount_Id(Long accountId);
    Optional<PaperPosition> findByAccount_IdAndTicker(Long accountId, String ticker);
}
