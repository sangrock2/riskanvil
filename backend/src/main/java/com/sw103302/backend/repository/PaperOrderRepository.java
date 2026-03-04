package com.sw103302.backend.repository;

import com.sw103302.backend.entity.PaperOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaperOrderRepository extends JpaRepository<PaperOrder, Long> {
    Page<PaperOrder> findByAccount_IdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM PaperOrder o WHERE o.account.id = :accountId")
    void deleteAllByAccountId(@Param("accountId") Long accountId);
}
