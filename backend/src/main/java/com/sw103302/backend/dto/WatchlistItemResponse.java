package com.sw103302.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Watchlist item with current market data and metadata")
public record WatchlistItemResponse(
        @Schema(description = "Watchlist item ID", example = "123")
        Long id,

        @Schema(description = "Stock ticker symbol", example = "AAPL")
        String ticker,

        @Schema(description = "Market type (US or KR)", example = "US")
        String market,

        @Schema(description = "Test mode flag", example = "false")
        boolean testMode,

        @Schema(description = "Timestamp when item was added to watchlist")
        LocalDateTime createdAt,

        @Schema(description = "User notes (max 500 characters)", example = "Strong buy candidate")
        String notes,

        @Schema(description = "Associated tags")
        List<TagResponse> tags,

        @Schema(description = "Latest insights summary from AI analysis")
        Summary summary
) {
    @Schema(description = "Summary of latest AI analysis insights")
    public record Summary(
            @Schema(description = "Recommended action (buy/sell/hold)", example = "buy")
            String action,

            @Schema(description = "Overall score (0-100)", example = "75", minimum = "0", maximum = "100")
            Integer score,

            @Schema(description = "Current stock price", example = "175.43")
            Double price,

            @Schema(description = "Timestamp when insights were last updated")
            LocalDateTime insightsUpdatedAt
    ) {}
}
