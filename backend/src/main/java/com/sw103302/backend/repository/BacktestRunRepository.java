package com.sw103302.backend.repository;

import com.sw103302.backend.entity.BacktestRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface BacktestRunRepository extends JpaRepository<BacktestRun, Long>, JpaSpecificationExecutor<BacktestRun> {
    List<BacktestRun> findByUser_EmailOrderByCreatedAtDesc(String email, Pageable pageable);
    Optional<BacktestRun> findByIdAndUser_Email(Long id, String email);
}
