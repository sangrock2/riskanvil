package com.sw103302.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "valuation")
public class ValuationProperties {
    private Ratios ratios = new Ratios();
    private Growth growth = new Growth();
    private Dcf dcf = new Dcf();
    private Map<String, SectorBands> sectorBands = new HashMap<>();

    public ValuationProperties() {
        initDefaultSectorBands();
    }

    private void initDefaultSectorBands() {
        // Technology: 고성장, 높은 PE/PS 허용
        sectorBands.put("TECHNOLOGY", new SectorBands(
                new Ratio(18.0, 50.0, 0.50),   // PE
                new Ratio(4.0, 15.0, 0.30),    // PS
                new Ratio(3.0, 12.0, 0.10),    // PB
                0.25, 0.30                      // ROE, OPM weight
        ));

        // Healthcare: 중간 성장
        sectorBands.put("HEALTHCARE", new SectorBands(
                new Ratio(15.0, 40.0, 0.50),
                new Ratio(3.0, 12.0, 0.25),
                new Ratio(2.0, 8.0, 0.15),
                0.20, 0.20
        ));

        // Financial Services: PB 중요, 낮은 PE
        sectorBands.put("FINANCIAL SERVICES", new SectorBands(
                new Ratio(8.0, 18.0, 0.40),
                new Ratio(1.5, 5.0, 0.15),
                new Ratio(0.8, 2.5, 0.35),     // PB 가중치 높음
                0.25, 0.15
        ));

        // Consumer Cyclical: 경기 민감, 중간
        sectorBands.put("CONSUMER CYCLICAL", new SectorBands(
                new Ratio(12.0, 30.0, 0.50),
                new Ratio(1.5, 6.0, 0.25),
                new Ratio(2.0, 8.0, 0.15),
                0.20, 0.20
        ));

        // Consumer Defensive: 안정적, 낮은 성장
        sectorBands.put("CONSUMER DEFENSIVE", new SectorBands(
                new Ratio(15.0, 28.0, 0.55),
                new Ratio(1.0, 4.0, 0.25),
                new Ratio(2.0, 6.0, 0.10),
                0.15, 0.25
        ));

        // Industrials
        sectorBands.put("INDUSTRIALS", new SectorBands(
                new Ratio(12.0, 28.0, 0.50),
                new Ratio(1.5, 5.0, 0.25),
                new Ratio(2.0, 6.0, 0.15),
                0.20, 0.20
        ));

        // Energy: 변동성 높음, 낮은 PE
        sectorBands.put("ENERGY", new SectorBands(
                new Ratio(6.0, 18.0, 0.45),
                new Ratio(0.5, 3.0, 0.25),
                new Ratio(1.0, 3.0, 0.20),
                0.20, 0.25
        ));

        // Basic Materials
        sectorBands.put("BASIC MATERIALS", new SectorBands(
                new Ratio(8.0, 20.0, 0.45),
                new Ratio(1.0, 4.0, 0.25),
                new Ratio(1.5, 4.0, 0.20),
                0.20, 0.20
        ));

        // Utilities: 안정적, 낮은 성장
        sectorBands.put("UTILITIES", new SectorBands(
                new Ratio(12.0, 22.0, 0.50),
                new Ratio(1.5, 4.0, 0.20),
                new Ratio(1.0, 2.5, 0.20),
                0.15, 0.25
        ));

        // Real Estate: PB 중요
        sectorBands.put("REAL ESTATE", new SectorBands(
                new Ratio(15.0, 35.0, 0.35),
                new Ratio(3.0, 10.0, 0.20),
                new Ratio(0.8, 2.0, 0.35),     // PB 가중치 높음
                0.15, 0.15
        ));

        // Communication Services
        sectorBands.put("COMMUNICATION SERVICES", new SectorBands(
                new Ratio(15.0, 40.0, 0.50),
                new Ratio(2.0, 8.0, 0.30),
                new Ratio(2.0, 8.0, 0.10),
                0.20, 0.25
        ));
    }

    public Ratios getRatios() { return ratios; }
    public void setRatios(Ratios ratios) { this.ratios = ratios; }

    public Growth getGrowth() { return growth; }
    public void setGrowth(Growth growth) { this.growth = growth; }

    public Dcf getDcf() { return dcf; }
    public void setDcf(Dcf dcf) { this.dcf = dcf; }

    public Map<String, SectorBands> getSectorBands() { return sectorBands; }
    public void setSectorBands(Map<String, SectorBands> sectorBands) { this.sectorBands = sectorBands; }

    /**
     * 섹터에 맞는 밴드 반환. 없으면 기본값(ratios) 사용
     */
    public SectorBands getBandsForSector(String sector) {
        if (sector == null || sector.isBlank()) {
            return toSectorBands(ratios);
        }
        String key = sector.trim().toUpperCase();
        return sectorBands.getOrDefault(key, toSectorBands(ratios));
    }

    private SectorBands toSectorBands(Ratios r) {
        return new SectorBands(r.getPe(), r.getPs(), r.getPb(), 0.20, 0.20);
    }

    // ========== Inner Classes ==========

    public static class Ratios {
        private Ratio pe = new Ratio(12.0, 35.0, 0.50);
        private Ratio ps = new Ratio(2.0, 10.0, 0.25);
        private Ratio pb = new Ratio(1.5, 8.0, 0.15);

        public Ratio getPe() { return pe; }
        public void setPe(Ratio pe) { this.pe = pe; }

        public Ratio getPs() { return ps; }
        public void setPs(Ratio ps) { this.ps = ps; }

        public Ratio getPb() { return pb; }
        public void setPb(Ratio pb) { this.pb = pb; }
    }

    public static class Ratio {
        private double low;
        private double high;
        private double weight;

        public Ratio() {}

        public Ratio(double low, double high, double weight) {
            this.low = low;
            this.high = high;
            this.weight = weight;
        }

        public double getLow() { return low; }
        public void setLow(double low) { this.low = low; }

        public double getHigh() { return high; }
        public void setHigh(double high) { this.high = high; }

        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }
    }

    public static class SectorBands {
        private Ratio pe;
        private Ratio ps;
        private Ratio pb;
        private double roeWeight;       // ROE 가중치
        private double opmWeight;       // Operating Margin 가중치

        public SectorBands() {}

        public SectorBands(Ratio pe, Ratio ps, Ratio pb, double roeWeight, double opmWeight) {
            this.pe = pe;
            this.ps = ps;
            this.pb = pb;
            this.roeWeight = roeWeight;
            this.opmWeight = opmWeight;
        }

        public Ratio getPe() { return pe; }
        public void setPe(Ratio pe) { this.pe = pe; }

        public Ratio getPs() { return ps; }
        public void setPs(Ratio ps) { this.ps = ps; }

        public Ratio getPb() { return pb; }
        public void setPb(Ratio pb) { this.pb = pb; }

        public double getRoeWeight() { return roeWeight; }
        public void setRoeWeight(double roeWeight) { this.roeWeight = roeWeight; }

        public double getOpmWeight() { return opmWeight; }
        public void setOpmWeight(double opmWeight) { this.opmWeight = opmWeight; }
    }

    public static class Growth {
        private double yoyMaxBonus = 15.0;
        private double yoyCap = 0.25;      // 25%까지 성장 보너스

        public double getYoyMaxBonus() { return yoyMaxBonus; }
        public void setYoyMaxBonus(double yoyMaxBonus) { this.yoyMaxBonus = yoyMaxBonus; }

        public double getYoyCap() { return yoyCap; }
        public void setYoyCap(double yoyCap) { this.yoyCap = yoyCap; }
    }

    public static class Dcf {
        private boolean enabled = true;

        private int projectionYears = 5;
        private double discountRate = 0.09;
        private double terminalGrowth = 0.025;

        private double yoyDefault = 0.05;
        private double yoyMin = -0.10;
        private double yoyMax = 0.25;

        // 섹터별 FCF Margin
        private double fcfMarginDefault = 0.10;
        private double fcfMarginTech = 0.22;
        private double fcfMarginHealthcare = 0.18;
        private double fcfMarginFinancial = 0.15;
        private double fcfMarginEnergy = 0.12;
        private double fcfMarginUtilities = 0.14;

        private double upsideCap = 0.25;
        private double scoreMaxBonus = 10.0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getProjectionYears() { return projectionYears; }
        public void setProjectionYears(int projectionYears) { this.projectionYears = projectionYears; }

        public double getDiscountRate() { return discountRate; }
        public void setDiscountRate(double discountRate) { this.discountRate = discountRate; }

        public double getTerminalGrowth() { return terminalGrowth; }
        public void setTerminalGrowth(double terminalGrowth) { this.terminalGrowth = terminalGrowth; }

        public double getYoyDefault() { return yoyDefault; }
        public void setYoyDefault(double yoyDefault) { this.yoyDefault = yoyDefault; }

        public double getYoyMin() { return yoyMin; }
        public void setYoyMin(double yoyMin) { this.yoyMin = yoyMin; }

        public double getYoyMax() { return yoyMax; }
        public void setYoyMax(double yoyMax) { this.yoyMax = yoyMax; }

        public double getFcfMarginDefault() { return fcfMarginDefault; }
        public void setFcfMarginDefault(double fcfMarginDefault) { this.fcfMarginDefault = fcfMarginDefault; }

        public double getFcfMarginTech() { return fcfMarginTech; }
        public void setFcfMarginTech(double fcfMarginTech) { this.fcfMarginTech = fcfMarginTech; }

        public double getFcfMarginHealthcare() { return fcfMarginHealthcare; }
        public void setFcfMarginHealthcare(double fcfMarginHealthcare) { this.fcfMarginHealthcare = fcfMarginHealthcare; }

        public double getFcfMarginFinancial() { return fcfMarginFinancial; }
        public void setFcfMarginFinancial(double fcfMarginFinancial) { this.fcfMarginFinancial = fcfMarginFinancial; }

        public double getFcfMarginEnergy() { return fcfMarginEnergy; }
        public void setFcfMarginEnergy(double fcfMarginEnergy) { this.fcfMarginEnergy = fcfMarginEnergy; }

        public double getFcfMarginUtilities() { return fcfMarginUtilities; }
        public void setFcfMarginUtilities(double fcfMarginUtilities) { this.fcfMarginUtilities = fcfMarginUtilities; }

        public double getUpsideCap() { return upsideCap; }
        public void setUpsideCap(double upsideCap) { this.upsideCap = upsideCap; }

        public double getScoreMaxBonus() { return scoreMaxBonus; }
        public void setScoreMaxBonus(double scoreMaxBonus) { this.scoreMaxBonus = scoreMaxBonus; }

        /**
         * 섹터에 맞는 FCF Margin 반환
         */
        public double getFcfMarginForSector(String sector) {
            if (sector == null) return fcfMarginDefault;
            String s = sector.trim().toUpperCase();
            if (s.contains("TECHNOLOGY")) return fcfMarginTech;
            if (s.contains("HEALTHCARE")) return fcfMarginHealthcare;
            if (s.contains("FINANCIAL")) return fcfMarginFinancial;
            if (s.contains("ENERGY")) return fcfMarginEnergy;
            if (s.contains("UTILITIES")) return fcfMarginUtilities;
            return fcfMarginDefault;
        }
    }
}
