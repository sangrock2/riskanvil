package com.sw103302.backend.controller;

import com.sw103302.backend.dto.PaperAccountResponse;
import com.sw103302.backend.dto.PaperOrderRequest;
import com.sw103302.backend.dto.PaperOrderResponse;
import com.sw103302.backend.dto.PaperPositionResponse;
import com.sw103302.backend.entity.PaperOrder;
import com.sw103302.backend.service.PaperTradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/paper")
@Validated
@Tag(name = "Paper Trading", description = "모의투자 - 가상 자금으로 주식 매매 연습")
public class PaperTradingController {

    private final PaperTradingService service;

    public PaperTradingController(PaperTradingService service) {
        this.service = service;
    }

    @GetMapping("/accounts")
    @Operation(summary = "계좌 조회", description = "US($100K)와 KR(₩100M) 계좌 조회. 없으면 자동 생성.")
    public ResponseEntity<List<PaperAccountResponse>> getAccounts() {
        return ResponseEntity.ok(service.getAccounts());
    }

    @PostMapping("/accounts/reset")
    @Operation(summary = "계좌 초기화", description = "선택한 시장 계좌를 초기 자본금으로 리셋")
    public ResponseEntity<PaperAccountResponse> resetAccount(
            @RequestParam @Pattern(regexp = "US|KR") String market) {
        return ResponseEntity.ok(service.resetAccount(market));
    }

    @PostMapping("/order")
    @Operation(summary = "시장가 주문", description = "BUY 또는 SELL 시장가 주문 실행. 수수료 0.1% 적용.")
    public ResponseEntity<PaperOrderResponse> placeOrder(@Valid @RequestBody PaperOrderRequest req) {
        return ResponseEntity.ok(service.placeOrder(req));
    }

    @GetMapping("/positions")
    @Operation(summary = "포지션 조회", description = "실시간 가격 포함 현재 보유 종목 조회")
    public ResponseEntity<List<PaperPositionResponse>> getPositions(
            @RequestParam @Pattern(regexp = "US|KR") String market) {
        return ResponseEntity.ok(service.getPositions(market));
    }

    @GetMapping("/orders")
    @Operation(summary = "주문 이력", description = "주문 이력 조회 (최신순, 페이징 지원)")
    public ResponseEntity<Map<String, Object>> getOrders(
            @RequestParam @Pattern(regexp = "US|KR") String market,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<PaperOrder> ordersPage = service.getOrders(market, page, size);
        List<Map<String, Object>> orders = ordersPage.getContent().stream().map(o -> Map.<String, Object>of(
                "id", o.getId(),
                "ticker", o.getTicker(),
                "direction", o.getDirection(),
                "quantity", o.getQuantity(),
                "price", o.getPrice(),
                "amount", o.getAmount(),
                "commission", o.getCommission(),
                "createdAt", o.getCreatedAt()
        )).toList();

        return ResponseEntity.ok(Map.of(
                "orders", orders,
                "totalPages", ordersPage.getTotalPages(),
                "totalElements", ordersPage.getTotalElements(),
                "currentPage", page
        ));
    }
}
