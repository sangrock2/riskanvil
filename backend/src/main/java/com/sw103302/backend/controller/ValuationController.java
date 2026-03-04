package com.sw103302.backend.controller;

import com.sw103302.backend.dto.InsightRequest;
import com.sw103302.backend.dto.ValuationResult;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.service.MarketCacheService;
import com.sw103302.backend.service.UsageService;
import com.sw103302.backend.service.ValuationService;
import com.sw103302.backend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/market")
@Tag(name = "Valuation", description = "Stock valuation APIs")
public class ValuationController {
    private final UserRepository userRepository;
    private final MarketCacheService marketCacheService;
    private final ValuationService valuationService;
    private final UsageService usageService;

    public ValuationController(UserRepository userRepository, MarketCacheService marketCacheService, ValuationService valuationService, UsageService usageService) {
        this.userRepository = userRepository;
        this.marketCacheService = marketCacheService;
        this.valuationService = valuationService;
        this.usageService = usageService;
    }

    @PostMapping("/valuation")
    @Operation(summary = "Calculate stock valuation", description = "Compute valuation score based on PE, PS, PB ratios and DCF analysis")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Valuation calculated successfully",
                    content = @Content(schema = @Schema(implementation = ValuationResult.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ValuationResult valuation(
            @Parameter(description = "Use test data") @RequestParam(defaultValue = "false") boolean test,
            @Parameter(description = "Force refresh cache") @RequestParam(defaultValue = "false") boolean refresh,
            @RequestBody InsightRequest req) {
        String email = SecurityUtil.currentEmail();
        if (email == null) throw new IllegalStateException("unauthenticated");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found"));

        String ticker = req.ticker().trim();
        String market = (req.market() == null ? "US" : req.market().trim());
        int days = req.days() != null ? req.days() : 90;
        int newsLimit = req.newsLimit() != null ? req.newsLimit() : 20;

        try {
            // ✅ INSIGHTS 호출로 집계되지 않도록 logInsightsUsage=false
            String insightsJson = marketCacheService.getInsightsForUser(user, req, test, refresh, false);

            ValuationResult out = valuationService.compute(insightsJson, ticker, market, test);

            boolean cached = (out._cache() != null && out._cache().cached());
            usageService.log(user, "VALUATION", ticker, market, test, days, newsLimit, cached, refresh, false, null);

            return out;
        } catch (Exception e) {
            usageService.log(user, "VALUATION", ticker, market, test, days, newsLimit, false, refresh, false, e.getMessage());
            throw e;
        }
    }
}
