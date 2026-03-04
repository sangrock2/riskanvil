package com.sw103302.backend.repository;

import com.sw103302.backend.entity.PaperAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperAccountRepository extends JpaRepository<PaperAccount, Long> {
    List<PaperAccount> findByUser_Id(Long userId);
    Optional<PaperAccount> findByUser_IdAndMarket(Long userId, String market);
}
