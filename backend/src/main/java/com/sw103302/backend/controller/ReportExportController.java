package com.sw103302.backend.controller;

import com.sw103302.backend.dto.InsightRequest;
import com.sw103302.backend.dto.ReportExportResponse;
import com.sw103302.backend.entity.MarketCache;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.MarketCacheRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/market/report/export")
@Tag(name = "Report Export", description = "Export reports in various formats")
public class ReportExportController {

    private final MarketCacheRepository marketCacheRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ReportExportController(MarketCacheRepository marketCacheRepository,
                                  UserRepository userRepository,
                                  ObjectMapper objectMapper) {
        this.marketCacheRepository = marketCacheRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/txt")
    @Operation(summary = "Export report as plain text")
    public ResponseEntity<byte[]> exportTxt(@Valid @RequestBody InsightRequest req) {
        String userEmail = SecurityUtil.currentEmail();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // Get cached report or generate new one
        String ticker = req.ticker();
        String market = req.market() != null ? req.market() : "US";
        int days = req.days() != null ? req.days() : 90;
        int newsLimit = req.newsLimit() != null ? req.newsLimit() : 20;
        boolean test = false; // Report export is production-only, no test mode

        MarketCache cache = marketCacheRepository
                .findByUser_IdAndTickerAndMarketAndTestModeAndDaysAndNewsLimit(
                        user.getId(), ticker, market, test, days, newsLimit
                )
                .orElse(null);

        String report = "";
        if (cache != null && cache.getReportText() != null) {
            report = cache.getReportText();
        } else {
            // If no cached report, return a message
            report = "No report available for " + ticker + ". Please generate insights first.";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment",
                String.format("report_%s_%s.txt", ticker, LocalDate.now()));

        return ResponseEntity.ok()
                .headers(headers)
                .body(report.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/json")
    @Operation(summary = "Export report as JSON with metadata")
    public ResponseEntity<ReportExportResponse> exportJson(@Valid @RequestBody InsightRequest req) {
        String userEmail = SecurityUtil.currentEmail();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        String ticker = req.ticker();
        String market = req.market() != null ? req.market() : "US";
        int days = req.days() != null ? req.days() : 90;
        int newsLimit = req.newsLimit() != null ? req.newsLimit() : 20;
        boolean test = false; // Report export is production-only, no test mode

        MarketCache cache = marketCacheRepository
                .findByUser_IdAndTickerAndMarketAndTestModeAndDaysAndNewsLimit(
                        user.getId(), ticker, market, test, days, newsLimit
                )
                .orElse(null);

        String reportText = "";
        String insightsJson = "";
        LocalDateTime updatedAt = LocalDateTime.now();

        if (cache != null) {
            reportText = cache.getReportText() != null ? cache.getReportText() : "";
            insightsJson = cache.getInsightsJson() != null ? cache.getInsightsJson() : "";
            updatedAt = cache.getInsightsUpdatedAt() != null ? cache.getInsightsUpdatedAt() : updatedAt;
        }

        ReportExportResponse response = new ReportExportResponse(
                ticker,
                market,
                days,
                newsLimit,
                LocalDateTime.now(),
                updatedAt,
                reportText,
                insightsJson
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/pdf")
    @Operation(summary = "Export report as PDF")
    public ResponseEntity<byte[]> exportPdf(@Valid @RequestBody InsightRequest req) {
        String userEmail = SecurityUtil.currentEmail();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        String ticker = req.ticker();
        String market = req.market() != null ? req.market() : "US";
        int days = req.days() != null ? req.days() : 90;
        int newsLimit = req.newsLimit() != null ? req.newsLimit() : 20;
        boolean test = false;

        MarketCache cache = marketCacheRepository
                .findByUser_IdAndTickerAndMarketAndTestModeAndDaysAndNewsLimit(
                        user.getId(), ticker, market, test, days, newsLimit
                )
                .orElse(null);

        if (cache == null || cache.getInsightsJson() == null) {
            throw new IllegalArgumentException("No report available. Please generate insights first.");
        }

        try {
            // Parse insights JSON
            JsonNode insights = objectMapper.readTree(cache.getInsightsJson());
            String reportText = cache.getReportText() != null ? cache.getReportText() : "";

            // Generate PDF
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Set margins
            document.setMargins(50, 50, 50, 50);

            // Title Page
            addTitle(document, ticker, market);
            document.add(new Paragraph("\n\n"));

            // Executive Summary
            addSection(document, "Executive Summary", extractSummary(insights));

            // Price Information
            JsonNode priceInfo = insights.get("currentPrice");
            if (priceInfo != null) {
                StringBuilder priceSection = new StringBuilder();
                priceSection.append("Current Price: $").append(priceInfo.asDouble()).append("\n");

                JsonNode changePercent = insights.get("changePercent");
                if (changePercent != null) {
                    priceSection.append("Change: ").append(String.format("%.2f", changePercent.asDouble())).append("%\n");
                }

                JsonNode high52Week = insights.get("high52Week");
                JsonNode low52Week = insights.get("low52Week");
                if (high52Week != null && low52Week != null) {
                    priceSection.append("52-Week Range: $").append(low52Week.asDouble())
                            .append(" - $").append(high52Week.asDouble()).append("\n");
                }

                addSection(document, "Price Information", priceSection.toString());
            }

            // Full Report
            if (!reportText.isEmpty()) {
                addSection(document, "Detailed Analysis", reportText);
            }

            // Footer
            addFooter(document, user.getEmail());

            document.close();

            // Prepare response
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    String.format("report_%s_%s.pdf", ticker, LocalDate.now().format(DateTimeFormatter.ISO_DATE)));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(baos.toByteArray());

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }

    private void addTitle(Document document, String ticker, String market) throws Exception {
        Paragraph title = new Paragraph("Stock Analysis Report")
                .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
                .setFontSize(24)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);

        Paragraph subtitle = new Paragraph(ticker + " (" + market + ")")
                .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
                .setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(subtitle);

        Paragraph date = new Paragraph("Generated: " + LocalDate.now().format(DateTimeFormatter.ISO_DATE))
                .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(date);
    }

    private void addSection(Document document, String sectionTitle, String content) throws Exception {
        document.add(new Paragraph("\n"));

        Paragraph title = new Paragraph(sectionTitle)
                .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
                .setFontSize(16);
        document.add(title);

        Paragraph body = new Paragraph(content)
                .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
                .setFontSize(11);
        document.add(body);
    }

    private void addFooter(Document document, String userEmail) throws Exception {
        document.add(new Paragraph("\n\n"));

        Paragraph footer = new Paragraph("Generated by Stock-AI for " + userEmail)
                .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
                .setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(footer);
    }

    private String extractSummary(JsonNode insights) {
        StringBuilder summary = new StringBuilder();

        JsonNode decision = insights.get("decision");
        if (decision != null) {
            JsonNode action = decision.get("action");
            JsonNode confidence = decision.get("confidence");
            if (action != null) {
                summary.append("Recommendation: ").append(action.asText()).append("\n");
            }
            if (confidence != null) {
                summary.append("Confidence: ").append(String.format("%.1f", confidence.asDouble() * 100)).append("%\n");
            }
        }

        JsonNode reason = insights.get("reason");
        if (reason != null && !reason.isNull()) {
            summary.append("\nAnalysis: ").append(reason.asText()).append("\n");
        }

        return summary.toString();
    }
}
