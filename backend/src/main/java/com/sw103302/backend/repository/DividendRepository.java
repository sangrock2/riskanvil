package com.sw103302.backend.repository;

import com.sw103302.backend.entity.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DividendRepository extends JpaRepository<Dividend, Long> {
    List<Dividend> findByPortfolioPosition_IdOrderByExDateDesc(Long positionId);
    List<Dividend> findByPortfolioPosition_Portfolio_IdOrderByExDateDesc(Long portfolioId);
    void deleteByPortfolioPosition_Id(Long positionId);
    void deleteByPortfolioPosition_Portfolio_Id(Long portfolioId);
}
