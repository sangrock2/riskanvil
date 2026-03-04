package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuantAnalysisService {
    private final ObjectMapper om;

    public QuantAnalysisService(ObjectMapper om) {
        this.om = om;
    }

    /**
     * 기존 insights JSON 문자열에 "_quant" 블록을 덧붙여 반환.
     * - 원본이 JSON Object가 아니거나 파싱 실패하면 원본 그대로 반환(서비스 안정성 우선)
     */
    public String attachQuant(String insightsJson) {
        if (insightsJson == null || insightsJson.isBlank()) return insightsJson;

        try {
            JsonNode root = om.readTree(insightsJson);
            if (!root.isObject()) return insightsJson;

            ObjectNode obj = (ObjectNode) root;

            ObjectNode payload = obj;
            JsonNode data = obj.get("data");
            if (data != null && data.isObject()) payload = (ObjectNode) data;

            ObjectNode quant = computeQuant(payload);
            obj.set("_quant", quant);

            return om.writeValueAsString(obj);
        } catch (Exception e) {
            return insightsJson;
        }
    }

    private ObjectNode computeQuant(ObjectNode insightsRoot) {
        ObjectNode out = om.createObjectNode();

        // 1) 가격 시계열 추출
        PriceSeries ps = extractPrices(insightsRoot);

        // 2) 펀더멘털 추출(있는 것만)
        JsonNode fund = insightsRoot.get("fundamentals");
        Double pe = readDouble(fund, "pe");
        Double revYoY = readDouble(fund, "revYoY"); // 0.079 -> 7.9%

        // 수익성 지표 추출
        Double roe = readDouble(fund, "roe");
        Double operatingMargin = readDouble(fund, "operatingMargin");
        Double profitMargin = readDouble(fund, "profitMargin");

        // 기술적 분석에서 OBV/RSI 추출 (AI 서비스에서 이미 계산된 값)
        JsonNode techNode = insightsRoot.get("technicals");
        Double obvMomentum = null;
        String obvTrend = null;
        if (techNode != null) {
            obvMomentum = readDouble(techNode, "obvMomentum");
            JsonNode trendNode = techNode.get("obvTrend");
            if (trendNode != null && !trendNode.isNull()) {
                obvTrend = trendNode.asText();
            }
        }

        // 3) 지표 계산(데이터 부족하면 null)
        Double sma20 = sma(ps.closes, 20);
        Double sma50 = sma(ps.closes, 50);
        Double sma90 = sma(ps.closes, 90); // 요청이 90일인 경우 "장기" 대체선으로 사용

        Double rsi14 = rsi(ps.closes, 14);
        Macd macd = macd(ps.closes, 12, 26, 9);

        Boll boll20 = bollinger(ps.closes, 20, 2.0);

        Double vol20 = volatility(ps.closes, 20); // 20일 표준편차(일간 수익률)
        Double maxDd = maxDrawdown(ps.closes);

        Double lastClose = ps.closes.isEmpty() ? null : ps.closes.get(ps.closes.size() - 1);

        // 4) 밸류에이션(아주 보수적인 “규칙”만 제공: PEG 유사값)
        // revYoY가 0.08이면 성장률 8%로 보고 pe/8 = 4.35 같은 값 -> 해석은 “참고용”
        Double pegLike = null;
        if (pe != null && revYoY != null && revYoY > 0) {
            double growthPct = revYoY * 100.0;
            if (growthPct > 0.0001) pegLike = pe / growthPct;
        }

        ObjectNode indicators = out.putObject("indicators");
        put(indicators, "lastClose", lastClose);
        put(indicators, "sma20", sma20);
        put(indicators, "sma50", sma50);
        put(indicators, "sma90", sma90);
        put(indicators, "rsi14", rsi14);
        if (macd != null) {
            ObjectNode m = indicators.putObject("macd");
            put(m, "macd", macd.macd);
            put(m, "signal", macd.signal);
            put(m, "hist", macd.hist);
        } else {
            indicators.putNull("macd");
        }
        if (boll20 != null) {
            ObjectNode b = indicators.putObject("bollinger20");
            put(b, "mid", boll20.mid);
            put(b, "upper", boll20.upper);
            put(b, "lower", boll20.lower);
        } else {
            indicators.putNull("bollinger20");
        }
        put(indicators, "volatility20d", vol20);
        put(indicators, "maxDrawdown", maxDd);

        ObjectNode valuation = out.putObject("valuation");
        put(valuation, "pe", pe);
        put(valuation, "revYoY", revYoY);
        put(valuation, "pegLike", pegLike);

        // 수익성 지표 추가
        ObjectNode profitability = out.putObject("profitability");
        put(profitability, "roe", roe);
        put(profitability, "operatingMargin", operatingMargin);
        put(profitability, "profitMargin", profitMargin);

        // OBV (거래량 모멘텀) 추가
        ObjectNode volume = out.putObject("volume");
        put(volume, "obvMomentum", obvMomentum);
        if (obvTrend != null) {
            volume.put("obvTrend", obvTrend);
        } else {
            volume.putNull("obvTrend");
        }

        // 5) 신호 생성("연구용" 규칙 기반 점수화)
        // - 추후 2단계(백테스트)에서 가중치/규칙을 검증하고, 종목/섹터별로 튜닝하는 게 핵심
        Signal signal = buildSignal(lastClose, sma20, sma50, sma90, rsi14, macd, boll20, pegLike,
                roe, operatingMargin, obvTrend, obvMomentum);

        ObjectNode sig = out.putObject("signal");
        sig.put("action", signal.action);
        put(sig, "score", signal.score);
        ArrayNode reasons = sig.putArray("reasons");
        for (String r : signal.reasons) reasons.add(r);

        // 6) 상태/품질 메타
        ObjectNode meta = out.putObject("meta");
        meta.put("pricesCount", ps.closes.size());
        meta.put("hasEnoughForSma50", ps.closes.size() >= 50);
        meta.put("hasEnoughForSma90", ps.closes.size() >= 90);
        meta.put("hasEnoughForMacd", ps.closes.size() >= 26);
        meta.put("hasEnoughForRsi14", ps.closes.size() >= 15);

        return out;
    }

    /* =========================
       Signal rules (research-grade baseline)
       ========================= */

    private Signal buildSignal(
            Double lastClose,
            Double sma20,
            Double sma50,
            Double sma90,
            Double rsi14,
            Macd macd,
            Boll boll,
            Double pegLike,
            Double roe,
            Double operatingMargin,
            String obvTrend,
            Double obvMomentum
    ) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        int signalCount = 0;  // 유효 신호 개수

        // === 1) Trend (다중 시간대 분석) ===
        if (lastClose != null && sma20 != null && sma50 != null && sma90 != null) {
            // 모든 시간대가 정렬된 경우 강한 추세
            if (lastClose > sma20 && sma20 > sma50 && sma50 > sma90) {
                score += 0.35;
                reasons.add("추세: 강한 상승 (종가 > SMA20 > SMA50 > SMA90)");
            } else if (lastClose < sma20 && sma20 < sma50 && sma50 < sma90) {
                score -= 0.35;
                reasons.add("추세: 강한 하락 (종가 < SMA20 < SMA50 < SMA90)");
            } else if (lastClose > sma50 && sma50 > sma90) {
                score += 0.20;
                reasons.add("추세: 상승 (종가 > SMA50 > SMA90)");
            } else if (lastClose < sma50 && sma50 < sma90) {
                score -= 0.20;
                reasons.add("추세: 하락 (종가 < SMA50 < SMA90)");
            } else {
                reasons.add("추세: 혼조 (SMA 관계 불일치)");
            }
            signalCount++;
        } else if (lastClose != null && sma50 != null && sma90 != null) {
            if (lastClose > sma50 && sma50 > sma90) {
                score += 0.25;
                reasons.add("추세: 상승 (종가 > SMA50 > SMA90)");
            } else if (lastClose < sma50 && sma50 < sma90) {
                score -= 0.25;
                reasons.add("추세: 하락 (종가 < SMA50 < SMA90)");
            }
            signalCount++;
        } else {
            reasons.add("추세: 데이터 부족");
        }

        // === 2) RSI (연속 스코어링) ===
        if (rsi14 != null) {
            if (rsi14 <= 20) {
                score += 0.20;
                reasons.add(String.format("RSI14: %.1f (극단적 과매도, 강한 반등 가능성)", rsi14));
            } else if (rsi14 <= 30) {
                score += 0.15;
                reasons.add(String.format("RSI14: %.1f (과매도 영역)", rsi14));
            } else if (rsi14 >= 80) {
                score -= 0.20;
                reasons.add(String.format("RSI14: %.1f (극단적 과매수, 조정 가능성 높음)", rsi14));
            } else if (rsi14 >= 70) {
                score -= 0.15;
                reasons.add(String.format("RSI14: %.1f (과매수 영역)", rsi14));
            } else if (rsi14 >= 40 && rsi14 <= 60) {
                reasons.add(String.format("RSI14: %.1f (중립 구간)", rsi14));
            } else {
                // 30~40 또는 60~70: 약간의 편향
                if (rsi14 < 40) {
                    score += 0.05;
                    reasons.add(String.format("RSI14: %.1f (약 과매도)", rsi14));
                } else {
                    score -= 0.05;
                    reasons.add(String.format("RSI14: %.1f (약 과매수)", rsi14));
                }
            }
            signalCount++;
        } else {
            reasons.add("RSI14: 데이터 부족");
        }

        // === 3) MACD (히스토그램 + 추세) ===
        if (macd != null && macd.hist != null) {
            double absHist = Math.abs(macd.hist);
            if (macd.hist > 0) {
                // 히스토그램 크기에 따른 가중치
                if (absHist > 1.0) {
                    score += 0.15;
                    reasons.add("MACD: 강한 상승 모멘텀 (hist > 1.0)");
                } else {
                    score += 0.10;
                    reasons.add("MACD: 상승 모멘텀 (hist 양수)");
                }
            } else if (macd.hist < 0) {
                if (absHist > 1.0) {
                    score -= 0.15;
                    reasons.add("MACD: 강한 하락 모멘텀 (hist < -1.0)");
                } else {
                    score -= 0.10;
                    reasons.add("MACD: 하락 모멘텀 (hist 음수)");
                }
            }
            signalCount++;
        } else {
            reasons.add("MACD: 데이터 부족");
        }

        // === 4) Bollinger (mean reversion + 밴드 폭 고려) ===
        if (lastClose != null && boll != null && boll.lower != null && boll.upper != null && boll.mid != null) {
            double bandWidth = (boll.upper - boll.lower) / boll.mid;
            boolean narrowBand = bandWidth < 0.10;  // 좁은 밴드 = 변동성 수렴

            if (lastClose < boll.lower) {
                if (narrowBand) {
                    score += 0.15;
                    reasons.add("볼린저: 하단 밴드 하회 + 밴드 수렴 (돌파 가능성)");
                } else {
                    score += 0.10;
                    reasons.add("볼린저: 하단 밴드 하회 (되돌림 가능성)");
                }
            } else if (lastClose > boll.upper) {
                if (narrowBand) {
                    score -= 0.05;  // 돌파일 수 있으므로 패널티 감소
                    reasons.add("볼린저: 상단 밴드 상회 + 밴드 수렴 (돌파 vs 과열)");
                } else {
                    score -= 0.10;
                    reasons.add("볼린저: 상단 밴드 상회 (과열 가능성)");
                }
            } else if (narrowBand) {
                reasons.add("볼린저: 밴드 수렴 (변동성 확대 예상)");
            }
            signalCount++;
        }

        // === 5) ROE (연속 스코어링) ===
        if (roe != null) {
            if (roe >= 0.25) {
                score += 0.18;
                reasons.add(String.format("ROE: %.1f%% (매우 우수)", roe * 100));
            } else if (roe >= 0.15) {
                score += 0.12;
                reasons.add(String.format("ROE: %.1f%% (우수)", roe * 100));
            } else if (roe >= 0.08) {
                score += 0.05;
                reasons.add(String.format("ROE: %.1f%% (양호)", roe * 100));
            } else if (roe >= 0) {
                reasons.add(String.format("ROE: %.1f%% (저조)", roe * 100));
            } else {
                score -= 0.15;
                reasons.add(String.format("ROE: %.1f%% (적자)", roe * 100));
            }
            signalCount++;
        }

        // === 6) Operating Margin (연속 스코어링) ===
        if (operatingMargin != null) {
            if (operatingMargin >= 0.25) {
                score += 0.12;
                reasons.add(String.format("영업이익률: %.1f%% (매우 높음)", operatingMargin * 100));
            } else if (operatingMargin >= 0.15) {
                score += 0.08;
                reasons.add(String.format("영업이익률: %.1f%% (높음)", operatingMargin * 100));
            } else if (operatingMargin >= 0.05) {
                score += 0.03;
                reasons.add(String.format("영업이익률: %.1f%% (보통)", operatingMargin * 100));
            } else if (operatingMargin >= 0) {
                reasons.add(String.format("영업이익률: %.1f%% (낮음)", operatingMargin * 100));
            } else {
                score -= 0.12;
                reasons.add(String.format("영업이익률: %.1f%% (영업적자)", operatingMargin * 100));
            }
            signalCount++;
        }

        // === 7) OBV (가격-거래량 발산 감지 추가) ===
        if (obvTrend != null) {
            Double priceChange = null;
            if (lastClose != null && sma20 != null && sma20 > 0) {
                priceChange = (lastClose - sma20) / sma20;
            }

            if ("RISING".equals(obvTrend)) {
                // 거래량 유입
                if (priceChange != null && priceChange < -0.03) {
                    // Bullish divergence: 거래량 유입인데 가격 하락
                    score += 0.15;
                    reasons.add("OBV: 상승 + 가격 하락 (강세 발산, 반등 가능성)");
                } else {
                    score += 0.10;
                    reasons.add("OBV: 상승 (거래량 유입)");
                }
            } else if ("FALLING".equals(obvTrend)) {
                // 거래량 유출
                if (priceChange != null && priceChange > 0.03) {
                    // Bearish divergence: 거래량 유출인데 가격 상승
                    score -= 0.15;
                    reasons.add("OBV: 하락 + 가격 상승 (약세 발산, 조정 가능성)");
                } else {
                    score -= 0.10;
                    reasons.add("OBV: 하락 (거래량 유출)");
                }
            } else {
                reasons.add("OBV: 중립");
            }
            signalCount++;
        } else if (obvMomentum != null) {
            if (obvMomentum > 0.08) {
                score += 0.12;
                reasons.add(String.format("OBV 모멘텀: +%.1f%% (강한 유입)", obvMomentum * 100));
            } else if (obvMomentum > 0.03) {
                score += 0.08;
                reasons.add(String.format("OBV 모멘텀: +%.1f%% (유입)", obvMomentum * 100));
            } else if (obvMomentum < -0.08) {
                score -= 0.12;
                reasons.add(String.format("OBV 모멘텀: %.1f%% (강한 유출)", obvMomentum * 100));
            } else if (obvMomentum < -0.03) {
                score -= 0.08;
                reasons.add(String.format("OBV 모멘텀: %.1f%% (유출)", obvMomentum * 100));
            }
            signalCount++;
        }

        // === 8) Valuation (PEG-like) ===
        if (pegLike != null) {
            if (pegLike <= 0.5) {
                score += 0.18;
                reasons.add(String.format("밸류에이션: PEG %.2f (매우 저평가)", pegLike));
            } else if (pegLike <= 1.0) {
                score += 0.12;
                reasons.add(String.format("밸류에이션: PEG %.2f (저평가)", pegLike));
            } else if (pegLike <= 1.5) {
                score += 0.05;
                reasons.add(String.format("밸류에이션: PEG %.2f (적정)", pegLike));
            } else if (pegLike >= 3.0) {
                score -= 0.15;
                reasons.add(String.format("밸류에이션: PEG %.2f (고평가)", pegLike));
            } else if (pegLike >= 2.0) {
                score -= 0.10;
                reasons.add(String.format("밸류에이션: PEG %.2f (약 고평가)", pegLike));
            }
            signalCount++;
        } else {
            reasons.add("밸류에이션: 데이터 부족");
        }

        // === 신호 강도 조정 (유효 신호 수에 따른 신뢰도) ===
        double confidence = signalCount >= 6 ? 1.0 : signalCount >= 4 ? 0.8 : signalCount >= 2 ? 0.6 : 0.4;
        reasons.add(String.format("신호 신뢰도: %.0f%% (%d개 지표 활용)", confidence * 100, signalCount));

        // === 최종 액션 결정 (개선된 임계값) ===
        String action;
        if (score >= 0.45) {
            action = "STRONG_BUY_CANDIDATE";
        } else if (score >= 0.30) {
            action = "BUY_CANDIDATE";
        } else if (score <= -0.45) {
            action = "STRONG_SELL_CANDIDATE";
        } else if (score <= -0.30) {
            action = "SELL_CANDIDATE";
        } else if (Math.abs(score) <= 0.10) {
            action = "NEUTRAL";
        } else {
            action = "HOLD";
        }

        return new Signal(action, score, reasons);
    }

    /* =========================
       Extractors + helpers
       ========================= */

    private PriceSeries extractPrices(ObjectNode insightsRoot) {
        List<Double> closes = new ArrayList<>();
        JsonNode prices = insightsRoot.get("prices");
        JsonNode points = (prices == null) ? null : prices.get("points");
        if (points != null && points.isArray()) {
            for (JsonNode p : points) {
                Double c = readDouble(p, "close");
                if (c != null) closes.add(c);
            }
        }
        return new PriceSeries(closes);
    }

    private Double readDouble(JsonNode node, String field) {
        if (node == null || field == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isNumber()) return v.asDouble();
        if (v.isTextual()) {
            try {
                String s = v.asText().trim();
                if (s.isBlank()) return null;
                return Double.parseDouble(s);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void put(ObjectNode n, String key, Double v) {
        if (v == null || !Double.isFinite(v)) n.putNull(key);
        else n.put(key, v);
    }

    /* =========================
       Indicators
       ========================= */

    private Double sma(List<Double> closes, int window) {
        if (closes == null || closes.size() < window || window <= 1) return null;
        double sum = 0.0;
        for (int i = closes.size() - window; i < closes.size(); i++) sum += closes.get(i);
        return sum / window;
    }

    private Double rsi(List<Double> closes, int period) {
        if (closes == null || closes.size() < period + 1) return null;

        double gain = 0.0, loss = 0.0;
        int start = closes.size() - (period + 1);

        for (int i = start + 1; i < closes.size(); i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            if (diff > 0) gain += diff;
            else loss += -diff;
        }

        double avgGain = gain / period;
        double avgLoss = loss / period;

        if (avgLoss == 0.0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private Macd macd(List<Double> closes, int fast, int slow, int signal) {
        if (closes == null || closes.size() < slow + signal) return null;

        List<Double> emaFast = emaSeries(closes, fast);
        List<Double> emaSlow = emaSeries(closes, slow);
        if (emaFast == null || emaSlow == null) return null;

        List<Double> macdLine = new ArrayList<>();
        int offset = emaFast.size() - emaSlow.size();
        for (int i = 0; i < emaSlow.size(); i++) {
            double m = emaFast.get(i + offset) - emaSlow.get(i);
            macdLine.add(m);
        }

        List<Double> signalLine = emaSeries(macdLine, signal);
        if (signalLine == null) return null;

        double macdLast = macdLine.get(macdLine.size() - 1);
        double sigLast = signalLine.get(signalLine.size() - 1);
        double histLast = macdLast - sigLast;

        return new Macd(macdLast, sigLast, histLast);
    }

    private List<Double> emaSeries(List<Double> values, int period) {
        if (values == null || values.size() < period || period <= 1) return null;

        double alpha = 2.0 / (period + 1.0);

        // 초기값: 첫 period의 SMA
        double sum = 0.0;
        for (int i = 0; i < period; i++) sum += values.get(i);
        double prevEma = sum / period;

        List<Double> out = new ArrayList<>();
        // period-1까지는 EMA를 정의하지 않고, period-1 위치부터 시작한다고 보면 됨
        out.add(prevEma);

        for (int i = period; i < values.size(); i++) {
            double v = values.get(i);
            double ema = (v * alpha) + (prevEma * (1.0 - alpha));
            out.add(ema);
            prevEma = ema;
        }
        return out;
    }

    private Boll bollinger(List<Double> closes, int window, double k) {
        if (closes == null || closes.size() < window || window <= 1) return null;

        double mean = 0.0;
        for (int i = closes.size() - window; i < closes.size(); i++) mean += closes.get(i);
        mean /= window;

        double var = 0.0;
        for (int i = closes.size() - window; i < closes.size(); i++) {
            double d = closes.get(i) - mean;
            var += d * d;
        }
        var /= window;
        double std = Math.sqrt(var);

        return new Boll(mean, mean + k * std, mean - k * std);
    }

    private Double volatility(List<Double> closes, int window) {
        if (closes == null || closes.size() < window + 1) return null;

        // 일간 수익률 표준편차
        List<Double> rets = new ArrayList<>();
        for (int i = closes.size() - window; i < closes.size(); i++) {
            double prev = closes.get(i - 1);
            double cur = closes.get(i);
            if (prev == 0.0) continue;
            rets.add((cur / prev) - 1.0);
        }
        if (rets.size() < 2) return null;

        double mean = 0.0;
        for (double r : rets) mean += r;
        mean /= rets.size();

        double var = 0.0;
        for (double r : rets) {
            double d = r - mean;
            var += d * d;
        }
        var /= (rets.size() - 1); // sample variance

        return Math.sqrt(var);
    }

    private Double maxDrawdown(List<Double> closes) {
        if (closes == null || closes.size() < 2) return null;

        double peak = closes.get(0);
        double maxDd = 0.0;

        for (double c : closes) {
            if (c > peak) peak = c;
            if (peak > 0) {
                double dd = (c / peak) - 1.0; // 음수
                if (dd < maxDd) maxDd = dd;
            }
        }
        return maxDd; // 예: -0.23 => -23%
    }

    /* =========================
       DTO-like inner classes
       ========================= */

    private static class PriceSeries {
        final List<Double> closes;
        PriceSeries(List<Double> closes) { this.closes = closes; }
    }

    private static class Macd {
        final Double macd;
        final Double signal;
        final Double hist;
        Macd(Double macd, Double signal, Double hist) {
            this.macd = macd;
            this.signal = signal;
            this.hist = hist;
        }
    }

    private static class Boll {
        final Double mid;
        final Double upper;
        final Double lower;
        Boll(Double mid, Double upper, Double lower) {
            this.mid = mid;
            this.upper = upper;
            this.lower = lower;
        }
    }

    private static class Signal {
        final String action;
        final Double score;
        final List<String> reasons;
        Signal(String action, Double score, List<String> reasons) {
            this.action = action;
            this.score = score;
            this.reasons = reasons;
        }
    }
}
