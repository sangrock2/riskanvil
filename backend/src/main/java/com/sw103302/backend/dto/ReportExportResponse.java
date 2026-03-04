package com.sw103302.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Response for report export containing report text and metadata")
public record ReportExportResponse(
        @Schema(description = "Stock ticker symbol", example = "AAPL")
        String ticker,

        @Schema(description = "Market type", example = "US")
        String market,

        @Schema(description = "Number of days analyzed", example = "90")
        int days,

        @Schema(description = "Number of news articles analyzed", example = "20")
        int newsLimit,

        @Schema(description = "Export generation timestamp")
        LocalDateTime exportedAt,

        @Schema(description = "Timestamp when insights were last updated")
        LocalDateTime insightsUpdatedAt,

        @Schema(description = "Full report text content")
        String reportText,

        @Schema(description = "Raw insights JSON data")
        String insightsJson
) {
}
