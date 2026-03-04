package com.sw103302.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;

@Schema(description = "Request for stock market insights and analysis")
public record InsightRequest(
        @Schema(description = "Stock ticker symbol (e.g., AAPL, TSLA, 005930)", example = "AAPL", required = true)
        @NotBlank(message = "ticker is required")
        @Size(max = 32, message = "ticker length must be <= 32")
        // AAPL, BRK.B, 005930 등 허용 (영문/숫자/./-)
        @Pattern(regexp = "^[A-Za-z0-9.\\-]{1,32}$", message = "ticker format is invalid")
        String ticker,

        @Schema(description = "Market type: US or KR", example = "US", defaultValue = "US")
        @Size(max = 8, message = "market length must be <= 8")
        // 현재 UI가 US/KR 기반이면 제한. 확장할 계획이면 이 Pattern은 제거해도 됨.
        @Pattern(regexp = "^(US|KR)?$", message = "market must be US or KR")
        String market,

        @Schema(description = "Number of days for historical analysis", example = "90", minimum = "1", maximum = "365", defaultValue = "90")
        @Min(value = 1, message = "days must be >= 1")
        @Max(value = 365, message = "days must be <= 365")
        Integer days,

        @Schema(description = "Maximum number of news articles to analyze", example = "20", minimum = "1", maximum = "200", defaultValue = "20")
        @Min(value = 1, message = "newsLimit must be >= 1")
        @Max(value = 200, message = "newsLimit must be <= 200")
        Integer newsLimit,

        @Schema(description = "Technical indicators to include (e.g., RSI, MACD, BB)", example = "[\"RSI\", \"MACD\", \"BB\"]")
        List<String> indicators,        // Specific indicators to include

        @Schema(description = "Benchmark ticker for comparison (e.g., SPY for S&P 500)", example = "SPY")
        @Size(max = 32, message = "benchmark ticker length must be <= 32")
        @Pattern(regexp = "^[A-Za-z0-9.\\-]*$", message = "benchmark ticker format is invalid")
        String benchmark,               // Benchmark ticker for comparison (e.g., "SPY")

        @Schema(description = "Include AI-powered price forecasts", defaultValue = "false")
        Boolean includeForecasts,       // Include AI price forecasts

        @Schema(description = "Compare with sector average performance", defaultValue = "false")
        Boolean compareWithSector       // Compare with sector average
) {
    public InsightRequest {
        if (ticker != null) ticker = ticker.trim().toUpperCase();
        if (market != null && !market.isBlank()) market = market.trim().toUpperCase();
        if (benchmark != null && !benchmark.isBlank()) benchmark = benchmark.trim().toUpperCase();
    }
}
