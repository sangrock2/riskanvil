package com.sw103302.backend.repository;

import com.sw103302.backend.entity.BackupCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BackupCodeRepository extends JpaRepository<BackupCode, Long> {
    List<BackupCode> findByUser_IdAndUsed(Long userId, boolean used);
}
