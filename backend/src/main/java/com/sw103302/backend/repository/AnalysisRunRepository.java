package com.sw103302.backend.repository;

import com.sw103302.backend.entity.AnalysisRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long>, JpaSpecificationExecutor<AnalysisRun> {
    List<AnalysisRun> findByUser_EmailOrderByCreatedAtDesc(String email, Pageable pageable);

    Optional<AnalysisRun> findByIdAndUser_Email(Long id, String email);
}
