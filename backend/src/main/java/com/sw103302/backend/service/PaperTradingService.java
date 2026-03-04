package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.PaperAccountResponse;
import com.sw103302.backend.dto.PaperOrderRequest;
import com.sw103302.backend.dto.PaperOrderResponse;
import com.sw103302.backend.dto.PaperPositionResponse;
import com.sw103302.backend.entity.PaperAccount;
import com.sw103302.backend.entity.PaperOrder;
import com.sw103302.backend.entity.PaperPosition;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.PaperAccountRepository;
import com.sw103302.backend.repository.PaperOrderRepository;
import com.sw103302.backend.repository.PaperPositionRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class PaperTradingService {

    private static final BigDecimal US_INITIAL_BALANCE = new BigDecimal("100000.00");   // $100,000
    private static final BigDecimal KR_INITIAL_BALANCE = new BigDecimal("100000000.00"); // ₩100,000,000
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.001"); // 0.1%

    private final PaperAccountRepository accountRepository;
    private final PaperPositionRepository positionRepository;
    private final PaperOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public PaperTradingService(PaperAccountRepository accountRepository,
                                PaperPositionRepository positionRepository,
                                PaperOrderRepository orderRepository,
                                UserRepository userRepository,
                                AiClient aiClient,
                                ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Get all accounts (US + KR). Auto-creates missing accounts.
     */
    @Transactional
    public List<PaperAccountResponse> getAccounts() {
        User user = currentUser();
        ensureAccountsExist(user);

        List<PaperAccount> accounts = accountRepository.findByUser_Id(user.getId());
        List<PaperAccountResponse> responses = new ArrayList<>();

        for (PaperAccount account : accounts) {
            List<PaperPosition> positions = positionRepository.findByAccount_Id(account.getId());
            Map<String, BigDecimal> prices = fetchPrices(positions, account.getMarket());
            responses.add(toAccountResponse(account, positions, prices));
        }
        return responses;
    }

    /**
     * Reset account to initial balance and clear all positions/orders.
     */
    @Transactional
    public PaperAccountResponse resetAccount(String market) {
        User user = currentUser();
        PaperAccount account = getOrCreateAccount(user, market);

        // Delete all positions and orders
        positionRepository.deleteAll(positionRepository.findByAccount_Id(account.getId()));
        orderRepository.deleteAllByAccountId(account.getId());

        account.resetBalance();
        accountRepository.save(account);

        return toAccountResponse(account, List.of(), Map.of());
    }

    /**
     * Execute a market order (BUY or SELL).
     */
    @Transactional
    public PaperOrderResponse placeOrder(PaperOrderRequest req) {
        User user = currentUser();
        PaperAccount account = getOrCreateAccount(user, req.market());

        // Fetch current market price
        BigDecimal price = fetchSinglePrice(req.ticker(), req.market());

        BigDecimal quantity = req.quantity();
        BigDecimal amount = price.multiply(quantity).setScale(4, RoundingMode.HALF_UP);
        BigDecimal commission = amount.multiply(COMMISSION_RATE).setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalCost = amount.add(commission);

        if ("BUY".equals(req.direction())) {
            executeBuy(account, req.ticker(), quantity, price, amount, commission, totalCost);
        } else if ("SELL".equals(req.direction())) {
            executeSell(account, req.ticker(), quantity, price, amount, commission);
        } else {
            throw new IllegalArgumentException("Invalid direction: " + req.direction());
        }

        // Save order record
        PaperOrder order = new PaperOrder(account, req.ticker(), req.direction(),
                quantity, price, amount, commission);
        orderRepository.save(order);
        accountRepository.save(account);

        return new PaperOrderResponse(
                order.getId(),
                order.getTicker(),
                order.getDirection(),
                order.getQuantity(),
                order.getPrice(),
                order.getAmount(),
                order.getCommission(),
                account.getBalance(),
                order.getCreatedAt()
        );
    }

    /**
     * Get positions with real-time prices.
     */
    @Transactional(readOnly = true)
    public List<PaperPositionResponse> getPositions(String market) {
        User user = currentUser();
        PaperAccount account = accountRepository.findByUser_IdAndMarket(user.getId(), market)
                .orElse(null);
        if (account == null) return List.of();

        List<PaperPosition> positions = positionRepository.findByAccount_Id(account.getId());
        Map<String, BigDecimal> prices = fetchPrices(positions, market);
        return toPositionResponses(positions, prices);
    }

    /**
     * Get order history with pagination.
     */
    @Transactional(readOnly = true)
    public Page<PaperOrder> getOrders(String market, int page, int size) {
        User user = currentUser();
        PaperAccount account = accountRepository.findByUser_IdAndMarket(user.getId(), market)
                .orElse(null);
        if (account == null) return Page.empty();

        return orderRepository.findByAccount_IdOrderByCreatedAtDesc(account.getId(), PageRequest.of(page, size));
    }

    // ---- private helpers ----

    private void executeBuy(PaperAccount account, String ticker, BigDecimal quantity,
                             BigDecimal price, BigDecimal amount, BigDecimal commission, BigDecimal totalCost) {
        if (account.getBalance().compareTo(totalCost) < 0) {
            throw new IllegalStateException("잔고가 부족합니다. 필요: " + totalCost + ", 보유: " + account.getBalance());
        }

        // Deduct balance
        account.setBalance(account.getBalance().subtract(totalCost));

        // Update position with weighted average price
        Optional<PaperPosition> existingOpt = positionRepository.findByAccount_IdAndTicker(account.getId(), ticker);
        if (existingOpt.isPresent()) {
            PaperPosition pos = existingOpt.get();
            BigDecimal newQty = pos.getQuantity().add(quantity);
            BigDecimal newTotalCost = pos.getTotalCost().add(amount);
            BigDecimal newAvgPrice = newTotalCost.divide(newQty, 4, RoundingMode.HALF_UP);
            pos.setQuantity(newQty);
            pos.setAvgPrice(newAvgPrice);
            pos.setTotalCost(newTotalCost);
            positionRepository.save(pos);
        } else {
            PaperPosition pos = new PaperPosition(account, ticker, quantity, price, amount);
            positionRepository.save(pos);
        }
    }

    private void executeSell(PaperAccount account, String ticker, BigDecimal quantity,
                              BigDecimal price, BigDecimal amount, BigDecimal commission) {
        PaperPosition pos = positionRepository.findByAccount_IdAndTicker(account.getId(), ticker)
                .orElseThrow(() -> new IllegalStateException(ticker + " 포지션이 없습니다"));

        if (pos.getQuantity().compareTo(quantity) < 0) {
            throw new IllegalStateException("보유 수량이 부족합니다. 보유: " + pos.getQuantity() + ", 매도 요청: " + quantity);
        }

        // Add sale proceeds minus commission
        BigDecimal proceeds = amount.subtract(commission);
        account.setBalance(account.getBalance().add(proceeds));

        // Reduce position
        BigDecimal remaining = pos.getQuantity().subtract(quantity);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            positionRepository.delete(pos);
        } else {
            BigDecimal newTotalCost = pos.getAvgPrice().multiply(remaining).setScale(4, RoundingMode.HALF_UP);
            pos.setQuantity(remaining);
            pos.setTotalCost(newTotalCost);
            positionRepository.save(pos);
        }
    }

    private void ensureAccountsExist(User user) {
        getOrCreateAccount(user, "US");
        getOrCreateAccount(user, "KR");
    }

    private PaperAccount getOrCreateAccount(User user, String market) {
        return accountRepository.findByUser_IdAndMarket(user.getId(), market)
                .orElseGet(() -> {
                    BigDecimal initial = "KR".equals(market) ? KR_INITIAL_BALANCE : US_INITIAL_BALANCE;
                    String currency = "KR".equals(market) ? "KRW" : "USD";
                    PaperAccount acc = new PaperAccount(user, market, initial, currency);
                    return accountRepository.save(acc);
                });
    }

    private BigDecimal fetchSinglePrice(String ticker, String market) {
        try {
            Map<String, Object> body = Map.of(
                    "tickers", List.of(ticker),
                    "market", market
            );
            String json = aiClient.post("/prices/batch", body);
            JsonNode root = objectMapper.readTree(json);
            JsonNode prices = root.get("prices");
            if (prices != null && prices.has(ticker)) {
                JsonNode priceNode = prices.get(ticker).get("price");
                if (priceNode != null && !priceNode.isNull()) {
                    return new BigDecimal(priceNode.asText());
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch price for {}/{}: {}", ticker, market, e.getMessage());
        }
        throw new IllegalStateException("현재가를 가져올 수 없습니다: " + ticker);
    }

    private Map<String, BigDecimal> fetchPrices(List<PaperPosition> positions, String market) {
        if (positions.isEmpty()) return Map.of();

        try {
            List<String> tickers = positions.stream().map(PaperPosition::getTicker).toList();
            Map<String, Object> body = Map.of("tickers", tickers, "market", market);
            String json = aiClient.post("/prices/batch", body);
            JsonNode root = objectMapper.readTree(json);
            JsonNode prices = root.get("prices");

            java.util.HashMap<String, BigDecimal> result = new java.util.HashMap<>();
            if (prices != null) {
                prices.fields().forEachRemaining(entry -> {
                    JsonNode priceNode = entry.getValue().get("price");
                    if (priceNode != null && !priceNode.isNull()) {
                        result.put(entry.getKey(), new BigDecimal(priceNode.asText()));
                    }
                });
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch batch prices: {}", e.getMessage());
            return Map.of();
        }
    }

    private PaperAccountResponse toAccountResponse(PaperAccount account, List<PaperPosition> positions, Map<String, BigDecimal> prices) {
        List<PaperPositionResponse> posResponses = toPositionResponses(positions, prices);
        return new PaperAccountResponse(
                account.getId(),
                account.getMarket(),
                account.getBalance(),
                account.getInitialBalance(),
                account.getCurrency(),
                posResponses
        );
    }

    private List<PaperPositionResponse> toPositionResponses(List<PaperPosition> positions, Map<String, BigDecimal> prices) {
        List<PaperPositionResponse> list = new ArrayList<>();
        for (PaperPosition pos : positions) {
            BigDecimal currentPrice = prices.getOrDefault(pos.getTicker(), pos.getAvgPrice());
            BigDecimal currentValue = currentPrice.multiply(pos.getQuantity()).setScale(4, RoundingMode.HALF_UP);
            BigDecimal unrealizedGain = currentValue.subtract(pos.getTotalCost());
            BigDecimal unrealizedGainPct = pos.getTotalCost().compareTo(BigDecimal.ZERO) != 0
                    ? unrealizedGain.divide(pos.getTotalCost(), new MathContext(6, RoundingMode.HALF_UP)).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            list.add(new PaperPositionResponse(
                    pos.getId(),
                    pos.getTicker(),
                    pos.getQuantity(),
                    pos.getAvgPrice(),
                    pos.getTotalCost(),
                    currentPrice,
                    currentValue,
                    unrealizedGain,
                    unrealizedGainPct
            ));
        }
        return list;
    }

    private User currentUser() {
        String email = SecurityUtil.currentEmail();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
