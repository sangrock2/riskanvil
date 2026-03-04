package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.config.ValuationProperties;
import com.sw103302.backend.config.ValuationProperties.Ratio;
import com.sw103302.backend.config.ValuationProperties.SectorBands;
import com.sw103302.backend.dto.ValuationResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class ValuationService {
    private final ObjectMapper om;
    private final ValuationProperties props;
    private final AiClient aiClient;

    public ValuationService(ObjectMapper om, ValuationProperties props, AiClient aiClient) {
        this.om = om;
        this.props = props;
        this.aiClient = aiClient;
    }

    public ValuationResult compute(String insightsJson, String ticker, String market, boolean testMode) {
        JsonNode root = readJsonSafe(insightsJson);

        boolean cached = root.path("_cache").path("cached").asBoolean(false);
        String insightsUpdatedAt = textOrNull(root.path("_cache").get("insightsUpdatedAt"));

        JsonNode payload = root;
        if (payload.has("data")) payload = payload.get("data");

        JsonNode fundamentals = payload.path("fundamentals");

        // 기본 지표
        Double pe = asDoubleOrNull(fundamentals.get("pe"));
        Double ps = asDoubleOrNull(fundamentals.get("ps"));
        Double pb = asDoubleOrNull(fundamentals.get("pb"));
        Double revYoY = asDoubleOrNull(fundamentals.get("revYoY"));

        // 수익성 지표 (신규)
        Double roe = asDoubleOrNull(fundamentals.get("roe"));
        Double operatingMargin = asDoubleOrNull(fundamentals.get("operatingMargin"));
        Double profitMargin = asDoubleOrNull(fundamentals.get("profitMargin"));

        Double marketCap = asDoubleOrNull(fundamentals.get("marketCap"));

        Long latestQuarterRevenue = asLongOrNull(fundamentals.path("revYoYMeta").get("latestRevenue"));
        Double annualRevenue = (latestQuarterRevenue == null || latestQuarterRevenue <= 0) ? null : latestQuarterRevenue.doubleValue() * 4.0;

        boolean psComputed = false;
        if (ps == null && marketCap != null && marketCap > 0 && annualRevenue != null && annualRevenue > 0) {
            ps = marketCap / annualRevenue;
            psComputed = true;
        }

        String sector = textOrNull(fundamentals.get("sector"));

        Double close = extractCloseFromJson(payload);
        String closeAsOf = null;

        if (close == null) {
            PriceSnap p = fetchLatestClose(ticker, market);
            close = p.close;
            closeAsOf = p.asOf;
        }

        // 섹터별 밴드 가져오기
        SectorBands bands = props.getBandsForSector(sector);

        DcfSnap dcf = computeDcf(sector, revYoY, annualRevenue, marketCap, close);

        int score = score(pe, ps, pb, revYoY, roe, operatingMargin, dcf, bands);
        String label = label(score, pe, ps, pb);
        List<String> rationales = rationales(pe, ps, pb, revYoY, roe, operatingMargin, profitMargin,
                psComputed, marketCap, annualRevenue, close, closeAsOf, dcf, sector, bands);

        return new ValuationResult(
                ticker,
                market,
                testMode,
                LocalDateTime.now(),
                pe, ps, pb, revYoY,
                score,
                label,
                rationales,
                new ValuationResult.CacheMeta(cached, insightsUpdatedAt)
        );
    }

    // ---------------- score/label/rationales ----------------

    private int score(Double pe, Double ps, Double pb, Double revYoY,
                      Double roe, Double operatingMargin,
                      DcfSnap dcf, SectorBands bands) {
        double sum = 0.0;
        double wsum = 0.0;

        Ratio peRatio = bands.getPe();
        Ratio psRatio = bands.getPs();
        Ratio pbRatio = bands.getPb();

        // PE 점수
        if (pe != null && peRatio != null) {
            sum += cheapnessScore(pe, peRatio.getLow(), peRatio.getHigh()) * peRatio.getWeight();
            wsum += peRatio.getWeight();
        }

        // PS 점수
        if (ps != null && psRatio != null) {
            sum += cheapnessScore(ps, psRatio.getLow(), psRatio.getHigh()) * psRatio.getWeight();
            wsum += psRatio.getWeight();
        }

        // PB 점수
        if (pb != null && pbRatio != null) {
            sum += cheapnessScore(pb, pbRatio.getLow(), pbRatio.getHigh()) * pbRatio.getWeight();
            wsum += pbRatio.getWeight();
        }

        // ROE 점수 (수익성 - 높을수록 좋음)
        if (roe != null && bands.getRoeWeight() > 0) {
            double roeScore = profitabilityScore(roe, 0.05, 0.25); // 5%~25% 범위
            sum += roeScore * bands.getRoeWeight();
            wsum += bands.getRoeWeight();
        }

        // Operating Margin 점수 (수익성 - 높을수록 좋음)
        if (operatingMargin != null && bands.getOpmWeight() > 0) {
            double opmScore = profitabilityScore(operatingMargin, 0.05, 0.30); // 5%~30% 범위
            sum += opmScore * bands.getOpmWeight();
            wsum += bands.getOpmWeight();
        }

        if (wsum <= 0.0) return 0;

        double base = sum / wsum;

        // 성장 보너스
        double bonus = 0.0;
        if (revYoY != null && revYoY > 0) {
            double cap = Math.max(0.000001, props.getGrowth().getYoyCap());
            double ratio = Math.min(revYoY, cap) / cap;
            bonus = ratio * props.getGrowth().getYoyMaxBonus();
        }

        // DCF 보너스/페널티
        double dcfAdj = 0.0;
        if (props.getDcf().isEnabled() && dcf != null && dcf.available && dcf.upsidePct != null) {
            double cap = Math.max(0.000001, props.getDcf().getUpsideCap());
            double t = clamp(dcf.upsidePct / cap, -1.0, 1.0);
            dcfAdj = t * props.getDcf().getScoreMaxBonus();
        }

        return (int) Math.round(clamp(base + bonus + dcfAdj, 0, 100));
    }

    /**
     * 수익성 지표 점수 (높을수록 좋음)
     */
    private double profitabilityScore(double value, double low, double high) {
        if (high <= low) return 50.0;
        if (value >= high) return 100.0;
        if (value <= low) return 0.0;
        double t = (value - low) / (high - low);
        return 100.0 * t;
    }

    private String label(int score, Double pe, Double ps, Double pb) {
        boolean any = (pe != null) || (ps != null) || (pb != null);
        if (!any) return "UNKNOWN";
        if (score >= 70) return "CHEAP";
        if (score >= 40) return "FAIR";
        return "EXPENSIVE";
    }

    private List<String> rationales(Double pe, Double ps, Double pb, Double revYoY,
                                    Double roe, Double operatingMargin, Double profitMargin,
                                    boolean psComputed, Double marketCap, Double annualRevenue,
                                    Double close, String closeAsOf,
                                    DcfSnap dcf, String sector, SectorBands bands) {
        List<String> out = new ArrayList<>();

        // 섹터 정보
        out.add(String.format("Sector: %s", sector != null ? sector : "UNKNOWN"));

        Ratio peRatio = bands.getPe();
        Ratio psRatio = bands.getPs();
        Ratio pbRatio = bands.getPb();

        if (pe != null && peRatio != null) {
            out.add(String.format("P/E=%.2f (sector band: %.1f~%.1f, weight=%.0f%%)",
                    pe, peRatio.getLow(), peRatio.getHigh(), peRatio.getWeight() * 100));
        }

        if (ps != null && psRatio != null) {
            String extra = psComputed ? " [computed]" : "";
            out.add(String.format("P/S=%.2f%s (sector band: %.1f~%.1f, weight=%.0f%%)",
                    ps, extra, psRatio.getLow(), psRatio.getHigh(), psRatio.getWeight() * 100));
        }

        if (pb != null && pbRatio != null) {
            out.add(String.format("P/B=%.2f (sector band: %.1f~%.1f, weight=%.0f%%)",
                    pb, pbRatio.getLow(), pbRatio.getHigh(), pbRatio.getWeight() * 100));
        }

        // 수익성 지표
        if (roe != null) {
            String quality = roe >= 0.15 ? "Good" : roe >= 0.08 ? "Average" : "Low";
            out.add(String.format("ROE=%.1f%% (%s, weight=%.0f%%)",
                    roe * 100, quality, bands.getRoeWeight() * 100));
        }

        if (operatingMargin != null) {
            String quality = operatingMargin >= 0.20 ? "Good" : operatingMargin >= 0.10 ? "Average" : "Low";
            out.add(String.format("Operating Margin=%.1f%% (%s, weight=%.0f%%)",
                    operatingMargin * 100, quality, bands.getOpmWeight() * 100));
        }

        if (profitMargin != null) {
            out.add(String.format("Net Profit Margin=%.1f%%", profitMargin * 100));
        }

        if (revYoY != null) {
            out.add(String.format("Revenue YoY=%.1f%% (growth bonus cap=%.0f%%)",
                    revYoY * 100.0, props.getGrowth().getYoyCap() * 100.0));
        }

        if (marketCap != null) out.add(String.format("marketCap=%.0f", marketCap));
        if (annualRevenue != null) out.add(String.format("annualRevenue(est.)=%.0f", annualRevenue));

        if (close != null) {
            String asOf = (closeAsOf == null || closeAsOf.isBlank()) ? "" : (" asOf=" + closeAsOf);
            out.add(String.format("lastClose=%.2f%s", close, asOf));
        }

        if (props.getDcf().isEnabled()) {
            if (dcf != null && dcf.available) {
                out.add(String.format(
                        "DCF intrinsic≈%.2f (years=%d r=%.3f tg=%.3f fcfMargin=%.3f growth=%.3f) upside=%.1f%%",
                        dcf.intrinsicPerShare,
                        dcf.years,
                        dcf.discountRate,
                        dcf.terminalGrowth,
                        dcf.fcfMargin,
                        dcf.growth,
                        dcf.upsidePct * 100.0
                ));
            } else {
                out.add("DCF skipped: " + (dcf == null ? "missing_inputs" : dcf.reason));
            }
        }

        if (out.isEmpty()) out.add("No valuation ratios found in insights.fundamentals.");
        return out;
    }

    // ---------------- DCF core ----------------

    private DcfSnap computeDcf(String sector, Double revYoY, Double annualRevenue, Double marketCap, Double close) {
        if (!props.getDcf().isEnabled()) return DcfSnap.skip("disabled");

        if (annualRevenue == null || annualRevenue <= 0) return DcfSnap.skip("missing_revenue");
        if (marketCap == null || marketCap <= 0) return DcfSnap.skip("missing_marketCap");
        if (close == null || close <= 0) return DcfSnap.skip("missing_price");

        int years = Math.max(1, props.getDcf().getProjectionYears());
        double r = props.getDcf().getDiscountRate();
        double tg = props.getDcf().getTerminalGrowth();
        if (r <= tg) return DcfSnap.skip("invalid_discount_vs_terminal_growth");

        double growth = (revYoY == null) ? props.getDcf().getYoyDefault() : revYoY;
        growth = clamp(growth, props.getDcf().getYoyMin(), props.getDcf().getYoyMax());

        // 섹터별 FCF Margin 사용
        double fcfMargin = props.getDcf().getFcfMarginForSector(sector);
        double baseFcf = annualRevenue * fcfMargin;

        double pv = 0.0;
        for (int t = 1; t <= years; t++) {
            double fcfT = baseFcf * Math.pow(1 + growth, t);
            pv += fcfT / Math.pow(1 + r, t);
        }

        double fcfN = baseFcf * Math.pow(1 + growth, years);
        double terminalValue = (fcfN * (1 + tg)) / (r - tg);
        double terminalPv = terminalValue / Math.pow(1 + r, years);

        double equityValue = pv + terminalPv;

        double shares = marketCap / close;
        if (shares <= 0) return DcfSnap.skip("invalid_shares");

        double intrinsic = equityValue / shares;
        double upsidePct = (intrinsic - close) / close;

        return DcfSnap.ok(intrinsic, upsidePct, years, r, tg, fcfMargin, growth);
    }

    // ---------------- price extract/fetch ----------------

    private Double extractCloseFromJson(JsonNode payload) {
        JsonNode q = payload.get("quant");
        if (q != null) {
            Double c = asDoubleOrNull(q.get("close"));
            if (c != null) return c;
            c = asDoubleOrNull(q.get("lastClose"));
            if (c != null) return c;
        }
        return null;
    }

    private PriceSnap fetchLatestClose(String ticker, String market) {
        try {
            String raw = aiClient.prices(ticker, market, 60);
            JsonNode n = readJsonSafe(raw);
            JsonNode ts = n.get("Time Series (Daily)");
            if (ts == null || !ts.isObject()) return new PriceSnap(null, null);

            String bestDate = null;
            Iterator<String> it = ts.fieldNames();
            while (it.hasNext()) {
                String d = it.next();
                if (bestDate == null || d.compareTo(bestDate) > 0) bestDate = d;
            }
            if (bestDate == null) return new PriceSnap(null, null);

            JsonNode day = ts.get(bestDate);
            Double close = asDoubleOrNull(day.get("4. close"));
            return new PriceSnap(bestDate, close);
        } catch (Exception e) {
            return new PriceSnap(null, null);
        }
    }

    // ---------------- utils ----------------

    private double cheapnessScore(double value, double low, double high) {
        if (high <= low) return 50.0;
        if (value <= low) return 100.0;
        if (value >= high) return 0.0;
        double t = (high - value) / (high - low);
        return 100.0 * t;
    }

    private double clamp(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    private JsonNode readJsonSafe(String raw) {
        try {
            if (raw == null || raw.isBlank()) return om.createObjectNode();
            JsonNode n = om.readTree(raw);
            return (n == null) ? om.createObjectNode() : n;
        } catch (Exception e) {
            return om.createObjectNode();
        }
    }

    private Double asDoubleOrNull(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isNumber()) return n.asDouble();
        if (n.isTextual()) {
            try {
                String s = n.asText().trim();
                if (s.isBlank() || "None".equalsIgnoreCase(s) || "-".equals(s)) return null;
                return Double.parseDouble(s.replace("%", "").replace(",", ""));
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private Long asLongOrNull(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isNumber()) return n.asLong();
        if (n.isTextual()) {
            try {
                String s = n.asText().trim();
                if (s.isBlank()) return null;
                return Long.parseLong(s);
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private String textOrNull(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isTextual()) return n.asText();
        return n.toString();
    }

    // ---------------- internal records ----------------

    private record PriceSnap(String asOf, Double close) {}

    private static class DcfSnap {
        boolean available;
        String reason;

        Double intrinsicPerShare;
        Double upsidePct;

        int years;
        double discountRate;
        double terminalGrowth;
        double fcfMargin;
        double growth;

        static DcfSnap ok(double intrinsic, double upsidePct, int years, double r, double tg, double fcfMargin, double growth) {
            DcfSnap s = new DcfSnap();
            s.available = true;
            s.intrinsicPerShare = intrinsic;
            s.upsidePct = upsidePct;
            s.years = years;
            s.discountRate = r;
            s.terminalGrowth = tg;
            s.fcfMargin = fcfMargin;
            s.growth = growth;
            return s;
        }

        static DcfSnap skip(String reason) {
            DcfSnap s = new DcfSnap();
            s.available = false;
            s.reason = reason;
            return s;
        }
    }
}
