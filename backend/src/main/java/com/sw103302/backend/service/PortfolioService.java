package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.dto.*;
import com.sw103302.backend.entity.Portfolio;
import com.sw103302.backend.entity.PortfolioPosition;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.PortfolioPositionRepository;
import com.sw103302.backend.repository.PortfolioRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sw103302.backend.util.PortfolioSymbolUtil.groupUniqueTickersByMarket;
import static com.sw103302.backend.util.PortfolioSymbolUtil.symbolKey;

/**
 * 사용자 포트폴리오/포지션 CRUD와 성과 계산, 리밸런싱 추천을 담당한다.
 * 가격/종목 메타데이터는 AI 서비스로부터 배치 조회한다.
 */
@Slf4j
@Service
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final UserRepository userRepository;
    private final PriceService priceService;
    private final ObjectMapper objectMapper;

    public PortfolioService(PortfolioRepository portfolioRepository,
                            PortfolioPositionRepository positionRepository,
                            UserRepository userRepository,
                            PriceService priceService,
                            ObjectMapper objectMapper) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.userRepository = userRepository;
        this.priceService = priceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 새 포트폴리오를 생성한다. 동일 사용자 내 이름 중복은 허용하지 않는다.
     */
    @Transactional
    public PortfolioResponse create(CreatePortfolioRequest req) {
        User user = currentUser();

        if (portfolioRepository.findByUser_IdAndName(user.getId(), req.name()).isPresent()) {
            throw new IllegalArgumentException("Portfolio with this name already exists");
        }

        Portfolio portfolio = new Portfolio(user, req.name(), req.description(),
            req.targetReturn(), req.riskProfile());
        Portfolio saved = portfolioRepository.save(portfolio);

        return toResponse(saved);
    }

    /**
     * 현재 사용자의 포트폴리오 목록을 반환한다.
     * 포지션/가격을 선조회해 N+1 조회를 피한다.
     */
    @Transactional(readOnly = true)
    public List<PortfolioResponse> list() {
        User user = currentUser();
        List<Portfolio> portfolios = portfolioRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());

        if (portfolios.isEmpty()) {
            return List.of();
        }

        List<Long> portfolioIds = portfolios.stream()
            .map(Portfolio::getId)
            .collect(Collectors.toList());

        List<PortfolioPosition> allPositions = positionRepository.findByPortfolio_IdInOrderByCreatedAtDesc(portfolioIds);
        Map<String, BigDecimal> allPrices = fetchCurrentPrices(allPositions);
        Map<Long, List<PortfolioPosition>> positionsByPortfolio = allPositions.stream()
            .collect(Collectors.groupingBy(p -> p.getPortfolio().getId()));

        return portfolios.stream()
            .map(portfolio -> toResponseWithData(portfolio, positionsByPortfolio.getOrDefault(portfolio.getId(), List.of()), allPrices))
            .collect(Collectors.toList());
    }

    /**
     * 포트폴리오 상세를 조회한다.
     * 포지션, 실시간 가격, 과거 가격/섹터 정보를 결합해 응답을 구성한다.
     */
    @Transactional(readOnly = true)
    public PortfolioDetailResponse detail(Long id) {
        User user = currentUser();
        Portfolio portfolio = portfolioRepository.findByIdAndUser_Id(id, user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        List<PortfolioPosition> positions = positionRepository.findByPortfolio_IdOrderByCreatedAtDesc(id);
        Map<String, BigDecimal> currentPrices = fetchCurrentPrices(positions);

        return toDetailResponse(portfolio, positions, currentPrices);
    }

    /**
     * 포트폴리오 메타 정보(이름/설명/목표수익/리스크)를 수정한다.
     */
    @Transactional
    public PortfolioResponse update(Long id, UpdatePortfolioRequest req) {
        User user = currentUser();
        Portfolio portfolio = portfolioRepository.findByIdAndUser_Id(id, user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        if (req.name() != null) portfolio.setName(req.name());
        if (req.description() != null) portfolio.setDescription(req.description());
        if (req.targetReturn() != null) portfolio.setTargetReturn(req.targetReturn());
        if (req.riskProfile() != null) portfolio.setRiskProfile(req.riskProfile());

        Portfolio saved = portfolioRepository.save(portfolio);
        return toResponse(saved);
    }

    /**
     * 포트폴리오를 삭제한다.
     */
    @Transactional
    public void delete(Long id) {
        User user = currentUser();
        Portfolio portfolio = portfolioRepository.findByIdAndUser_Id(id, user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        portfolioRepository.delete(portfolio);
    }

    /**
     * 포트폴리오에 신규 포지션을 추가한다.
     * 동일 포트폴리오 내 동일 티커/시장 조합 중복을 차단한다.
     */
    @Transactional
    public Long addPosition(Long portfolioId, AddPositionRequest req) {
        User user = currentUser();
        Portfolio portfolio = portfolioRepository.findByIdAndUser_Id(portfolioId, user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        // Check duplicate ticker
        if (positionRepository.findByPortfolio_IdAndTickerAndMarket(
            portfolioId, req.ticker(), req.market()).isPresent()) {
            throw new IllegalArgumentException("Position already exists");
        }

        PortfolioPosition position = new PortfolioPosition(portfolio, req.ticker(),
            req.market(), req.quantity(), req.entryPrice(), req.entryDate(), req.notes());
        PortfolioPosition savedPosition = positionRepository.save(position);

        return savedPosition.getId();
    }

    /**
     * 포지션 수량/진입가/메모 등 수정 가능한 필드만 업데이트한다.
     */
    @Transactional
    public void updatePosition(Long portfolioId, Long positionId, UpdatePositionRequest req) {
        User user = currentUser();
        PortfolioPosition position = positionRepository.findByIdAndPortfolio_User_Id(positionId, user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Position not found"));

        if (!position.getPortfolio().getId().equals(portfolioId)) {
            throw new IllegalArgumentException("Position does not belong to this portfolio");
        }

        if (req.quantity() != null) position.setQuantity(req.quantity());
        if (req.entryPrice() != null) position.setEntryPrice(req.entryPrice());
        if (req.entryDate() != null) position.setEntryDate(req.entryDate());
        if (req.notes() != null) position.setNotes(req.notes());

        positionRepository.save(position);
    }

    /**
     * 포트폴리오 소속 검증 후 포지션을 삭제한다.
     */
    @Transactional
    public void deletePosition(Long portfolioId, Long positionId) {
        User user = currentUser();
        PortfolioPosition position = positionRepository.findByIdAndPortfolio_User_Id(positionId, user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Position not found"));

        if (!position.getPortfolio().getId().equals(portfolioId)) {
            throw new IllegalArgumentException("Position does not belong to this portfolio");
        }

        positionRepository.delete(position);
    }

    /**
     * Optimized version that uses pre-loaded positions and prices to avoid N+1 queries
     */
    private PortfolioResponse toResponseWithData(Portfolio portfolio,
                                                 List<PortfolioPosition> positions,
                                                 Map<String, BigDecimal> currentPrices) {
        Totals totals = calculateTotals(positions, currentPrices);

        return new PortfolioResponse(
            portfolio.getId(),
            portfolio.getName(),
            portfolio.getDescription(),
            portfolio.getTargetReturn(),
            portfolio.getRiskProfile(),
            portfolio.getCreatedAt(),
            portfolio.getUpdatedAt(),
            positions.size(),
            totals.totalValue(),
            totals.totalCost(),
            totals.totalReturn(),
            totals.totalReturnPercent()
        );
    }

    private PortfolioResponse toResponse(Portfolio portfolio) {
        List<PortfolioPosition> positions = positionRepository
            .findByPortfolio_IdOrderByCreatedAtDesc(portfolio.getId());

        Map<String, BigDecimal> currentPrices = fetchCurrentPrices(positions);
        return toResponseWithData(portfolio, positions, currentPrices);
    }

    /**
     * 상세 응답용 집계 계산.
     * - 포지션별 손익
     * - 총 수익률, 일/주/월 변화
     * - 티커/섹터/시장 비중
     */
    private PortfolioDetailResponse toDetailResponse(Portfolio portfolio,
                                                     List<PortfolioPosition> positions,
                                                     Map<String, BigDecimal> currentPrices) {
        Map<String, Map<String, Object>> stockInfo = fetchStockInfo(positions);

        List<PortfolioDetailResponse.PositionSummary> positionSummaries = positions.stream()
            .map(pos -> {
                BigDecimal currentPrice = resolvePrice(pos, currentPrices);
                BigDecimal totalCost = pos.getEntryPrice().multiply(pos.getQuantity());
                BigDecimal currentValue = currentPrice.multiply(pos.getQuantity());
                BigDecimal unrealizedGain = currentValue.subtract(totalCost);
                BigDecimal unrealizedGainPercent = percent(unrealizedGain, totalCost);

                return new PortfolioDetailResponse.PositionSummary(
                    pos.getId(),
                    pos.getTicker(),
                    pos.getMarket(),
                    pos.getQuantity(),
                    pos.getEntryPrice(),
                    pos.getEntryDate(),
                    currentPrice,
                    currentValue,
                    totalCost,
                    unrealizedGain,
                    unrealizedGainPercent,
                    pos.getNotes()
                );
            })
            .collect(Collectors.toList());

        BigDecimal totalValue = positionSummaries.stream()
            .map(PortfolioDetailResponse.PositionSummary::currentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCost = positionSummaries.stream()
            .map(PortfolioDetailResponse.PositionSummary::totalCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReturn = totalValue.subtract(totalCost);
        BigDecimal totalReturnPercent = percent(totalReturn, totalCost);

        BigDecimal previousDayValue = referenceValue(positions, stockInfo, "previousClose", currentPrices);

        BigDecimal dayChange = totalValue.subtract(previousDayValue);
        BigDecimal dayChangePercent = percent(dayChange, previousDayValue);

        Map<String, Map<String, Object>> historicalPrices = fetchHistoricalPrices(positions);
        BigDecimal weekOldValue = referenceValue(positions, historicalPrices, "weekAgoPrice", currentPrices);
        BigDecimal weekChange = totalValue.subtract(weekOldValue);

        BigDecimal monthOldValue = referenceValue(positions, historicalPrices, "monthAgoPrice", currentPrices);
        BigDecimal monthChange = totalValue.subtract(monthOldValue);

        PortfolioDetailResponse.PerformanceMetrics performance = new PortfolioDetailResponse.PerformanceMetrics(
            totalValue,
            totalCost,
            totalReturn,
            totalReturnPercent,
            dayChange,
            dayChangePercent,
            weekChange,
            monthChange
        );

        Map<String, BigDecimal> byTicker = new HashMap<>();
        Map<String, BigDecimal> byMarket = new HashMap<>();
        Map<String, BigDecimal> bySector = new HashMap<>();

        for (PortfolioDetailResponse.PositionSummary pos : positionSummaries) {
            byTicker.merge(pos.ticker(), pos.currentValue(), BigDecimal::add);
            byMarket.merge(pos.market(), pos.currentValue(), BigDecimal::add);

            Map<String, Object> info = stockInfo.get(symbolKey(pos.ticker(), pos.market()));
            if (info != null && info.get("sector") != null) {
                String sector = (String) info.get("sector");
                bySector.merge(sector, pos.currentValue(), BigDecimal::add);
            } else {
                bySector.merge("Unknown", pos.currentValue(), BigDecimal::add);
            }
        }

        PortfolioDetailResponse.AllocationBreakdown allocation = new PortfolioDetailResponse.AllocationBreakdown(
            byTicker,
            bySector,
            byMarket
        );

        return new PortfolioDetailResponse(
            portfolio.getId(),
            portfolio.getName(),
            portfolio.getDescription(),
            portfolio.getTargetReturn(),
            portfolio.getRiskProfile(),
            portfolio.getCreatedAt(),
            portfolio.getUpdatedAt(),
            positionSummaries,
            performance,
            allocation
        );
    }

    /**
     * Fetch current prices for all positions, grouped by market for efficiency
     */
    private Map<String, BigDecimal> fetchCurrentPrices(List<PortfolioPosition> positions) {
        Map<String, BigDecimal> allPrices = new HashMap<>();
        if (positions.isEmpty()) {
            return allPrices;
        }

        // 동일 티커 요청이 중복되지 않도록 시장별 unique 목록으로 그룹화한다.
        Map<String, List<String>> tickersByMarket = groupUniqueTickersByMarket(positions);
        Map<String, BigDecimal> fallbackEntryPrices = positions.stream()
            .collect(Collectors.toMap(
                pos -> symbolKey(pos),
                PortfolioPosition::getEntryPrice,
                (first, second) -> first
            ));

        for (Map.Entry<String, List<String>> entry : tickersByMarket.entrySet()) {
            String market = entry.getKey();
            List<String> tickers = entry.getValue();

            Map<String, BigDecimal> prices = Map.of();
            try {
                prices = priceService.fetchPricesBatch(tickers, market);
            } catch (Exception e) {
                log.error("Failed to fetch prices for market {}: {}", market, e.getMessage());
            }

            for (String ticker : tickers) {
                String key = symbolKey(ticker, market);
                BigDecimal price = prices.get(ticker);
                if (price != null) {
                    allPrices.put(key, price);
                    continue;
                }
                BigDecimal fallback = fallbackEntryPrices.get(key);
                if (fallback != null) {
                    allPrices.putIfAbsent(key, fallback);
                }
            }
        }

        return allPrices;
    }

    /**
     * 포트폴리오 내 종목의 섹터/전일종가 정보를 배치 조회한다.
     * 실패 시 일부 종목 정보가 비어 있어도 상세 응답 생성을 계속한다.
     */
    private Map<String, Map<String, Object>> fetchStockInfo(List<PortfolioPosition> positions) {
        Map<String, Map<String, Object>> allInfo = new HashMap<>();

        Map<String, List<String>> tickersByMarket = groupUniqueTickersByMarket(positions);
        for (Map.Entry<String, List<String>> entry : tickersByMarket.entrySet()) {
            String market = entry.getKey();
            List<String> tickers = entry.getValue();

            try {
                JsonNode response = postPriceEndpoint("/prices/info", market, tickers);
                JsonNode stockInfoNode = response.get("stockInfo");

                if (stockInfoNode != null) {
                    stockInfoNode.fields().forEachRemaining(field -> {
                        String ticker = field.getKey();
                        JsonNode info = field.getValue();

                        if (info != null && !info.isNull()) {
                            Map<String, Object> stockData = new HashMap<>();
                            stockData.put("sector", info.has("sector") ? info.get("sector").asText("Unknown") : "Unknown");
                            stockData.put("previousClose", info.has("previousClose") && !info.get("previousClose").isNull()
                                ? new BigDecimal(info.get("previousClose").asText()) : null);

                            allInfo.put(symbolKey(ticker, market), stockData);
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Failed to fetch stock info for market {}: {}", market, e.getMessage());
            }
        }

        return allInfo;
    }

    /**
     * Fetch historical prices (week-old, month-old) for all positions
     */
    private Map<String, Map<String, Object>> fetchHistoricalPrices(List<PortfolioPosition> positions) {
        Map<String, Map<String, Object>> allHistoricalData = new HashMap<>();

        Map<String, List<String>> tickersByMarket = groupUniqueTickersByMarket(positions);
        for (Map.Entry<String, List<String>> entry : tickersByMarket.entrySet()) {
            String market = entry.getKey();
            List<String> tickers = entry.getValue();

            try {
                JsonNode response = postPriceEndpoint("/prices/historical", market, tickers);
                JsonNode historicalNode = response.get("historicalPrices");

                if (historicalNode != null) {
                    historicalNode.fields().forEachRemaining(field -> {
                        String ticker = field.getKey();
                        JsonNode data = field.getValue();

                        if (data != null && !data.isNull()) {
                            Map<String, Object> histData = new HashMap<>();

                            if (data.has("weekAgoPrice") && !data.get("weekAgoPrice").isNull()) {
                                histData.put("weekAgoPrice", data.get("weekAgoPrice").asDouble());
                            }

                            if (data.has("monthAgoPrice") && !data.get("monthAgoPrice").isNull()) {
                                histData.put("monthAgoPrice", data.get("monthAgoPrice").asDouble());
                            }

                            allHistoricalData.put(symbolKey(ticker, market), histData);
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Failed to fetch historical prices for market {}: {}", market, e.getMessage());
            }
        }

        return allHistoricalData;
    }

    private Totals calculateTotals(List<PortfolioPosition> positions, Map<String, BigDecimal> currentPrices) {
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;

        for (PortfolioPosition pos : positions) {
            BigDecimal cost = pos.getEntryPrice().multiply(pos.getQuantity());
            BigDecimal value = resolvePrice(pos, currentPrices).multiply(pos.getQuantity());
            totalCost = totalCost.add(cost);
            totalValue = totalValue.add(value);
        }

        BigDecimal totalReturn = totalValue.subtract(totalCost);
        return new Totals(totalValue, totalCost, totalReturn, percent(totalReturn, totalCost));
    }

    private BigDecimal resolvePrice(PortfolioPosition pos, Map<String, BigDecimal> currentPrices) {
        return currentPrices.getOrDefault(symbolKey(pos), pos.getEntryPrice());
    }

    private BigDecimal percent(BigDecimal value, BigDecimal base) {
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return value.divide(base, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
    }

    private BigDecimal referenceValue(List<PortfolioPosition> positions,
                                      Map<String, Map<String, Object>> source,
                                      String key,
                                      Map<String, BigDecimal> currentPrices) {
        BigDecimal total = BigDecimal.ZERO;
        for (PortfolioPosition pos : positions) {
            Map<String, Object> row = source.get(symbolKey(pos));
            BigDecimal refPrice = row == null ? null : toBigDecimal(row.get(key));
            BigDecimal price = refPrice != null ? refPrice : resolvePrice(pos, currentPrices);
            total = total.add(price.multiply(pos.getQuantity()));
        }
        return total;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode postPriceEndpoint(String endpoint, String market, List<String> tickers) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("tickers", tickers);
        request.put("market", market);
        String jsonResponse = priceService.getAiClient().post(endpoint, request);
        return objectMapper.readTree(jsonResponse);
    }

    /**
     * 포트폴리오 리밸런싱 추천
     * 현재 포지션의 시가 비중 vs 목표 비중을 비교하여 BUY/SELL 거래 목록 반환
     */
    @Transactional(readOnly = true)
    public RebalanceResponse rebalance(Long portfolioId, RebalanceRequest req) {
        User user = currentUser();
        portfolioRepository.findByIdAndUser_Id(portfolioId, user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        List<PortfolioPosition> positions = positionRepository.findByPortfolio_IdOrderByCreatedAtDesc(portfolioId);
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("Portfolio has no positions");
        }

        Map<String, BigDecimal> currentPrices = fetchCurrentPrices(positions);

        BigDecimal totalValue = positions.stream()
            .map(p -> resolvePrice(p, currentPrices).multiply(p.getQuantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("Portfolio total value is zero");
        }

        Map<String, BigDecimal> currentValues = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            currentValues.merge(pos.getTicker(), resolvePrice(pos, currentPrices).multiply(pos.getQuantity()), BigDecimal::add);
        }

        Map<String, Double> currentWeights = new HashMap<>();
        for (Map.Entry<String, BigDecimal> e : currentValues.entrySet()) {
            currentWeights.put(e.getKey(), e.getValue().divide(totalValue, 8, RoundingMode.HALF_UP).doubleValue());
        }

        double weightSum = req.targetWeights().values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(weightSum - 1.0) > 0.01) {
            throw new IllegalArgumentException(
                String.format("Target weights must sum to 1.0 (current sum: %.4f)", weightSum));
        }

        List<RebalanceResponse.RebalanceTrade> trades = req.targetWeights().entrySet().stream()
            .map(e -> {
                String ticker = e.getKey();
                double targetWeight = e.getValue();
                double currentWeight = currentWeights.getOrDefault(ticker, 0.0);
                double diff = targetWeight - currentWeight;
                BigDecimal amount = totalValue.multiply(BigDecimal.valueOf(Math.abs(diff)))
                    .setScale(2, RoundingMode.HALF_UP);
                String action = diff >= 0 ? "BUY" : "SELL";
                return new RebalanceResponse.RebalanceTrade(
                    ticker, action,
                    Math.round(currentWeight * 10000.0) / 10000.0,
                    Math.round(targetWeight * 10000.0) / 10000.0,
                    Math.round(diff * 10000.0) / 10000.0,
                    amount
                );
            })
            .filter(t -> Math.abs(t.diffWeight()) >= 0.001)
            .sorted((a, b) -> Double.compare(Math.abs(b.diffWeight()), Math.abs(a.diffWeight())))
            .collect(Collectors.toList());

        return new RebalanceResponse(totalValue.setScale(2, RoundingMode.HALF_UP), trades);
    }

    private record Totals(BigDecimal totalValue, BigDecimal totalCost, BigDecimal totalReturn, BigDecimal totalReturnPercent) {}

    /**
     * SecurityContext 기준 현재 사용자 엔티티를 조회한다.
     */
    private User currentUser() {
        String email = SecurityUtil.currentEmail();
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
