package com.sw103302.backend.repository;

import com.sw103302.backend.entity.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {
    List<PriceAlert> findByUser_IdAndEnabledOrderByCreatedAtDesc(Long userId, boolean enabled);
    List<PriceAlert> findByTickerAndMarketAndEnabled(String ticker, String market, boolean enabled);
}
