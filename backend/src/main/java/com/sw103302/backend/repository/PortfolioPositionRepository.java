package com.sw103302.backend.repository;

import com.sw103302.backend.entity.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {
    List<PortfolioPosition> findByPortfolio_IdOrderByCreatedAtDesc(Long portfolioId);
    List<PortfolioPosition> findByPortfolio_IdInOrderByCreatedAtDesc(List<Long> portfolioIds);
    Optional<PortfolioPosition> findByIdAndPortfolio_User_Id(Long id, Long userId);
    Optional<PortfolioPosition> findByPortfolio_IdAndTickerAndMarket(Long portfolioId, String ticker, String market);
}
