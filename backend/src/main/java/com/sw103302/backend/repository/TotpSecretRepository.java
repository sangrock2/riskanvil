package com.sw103302.backend.repository;

import com.sw103302.backend.entity.TotpSecret;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TotpSecretRepository extends JpaRepository<TotpSecret, Long> {
    Optional<TotpSecret> findByUser_Id(Long userId);
}
