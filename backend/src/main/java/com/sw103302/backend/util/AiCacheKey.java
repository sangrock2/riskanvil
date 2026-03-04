package com.sw103302.backend.util;

import com.sw103302.backend.dto.InsightRequest;

public class AiCacheKey {
    private AiCacheKey() {}

    private static String norm(String s, String def) {
        if (s == null) return def;
        String t = s.trim();
        return t.isBlank() ? def : t;
    }

    private static int normInt(Integer v, int def) {
        if (v == null || v <= 0) return def;
        return v;
    }

    public static String quote(String ticker, String market) {
        return "q:" + norm(ticker, "") + ":" + norm(market, "US");
    }

    public static String prices(String ticker, String market, int days) {
        return "p:" + norm(ticker, "") + ":" + norm(market, "US") + ":" + days;
    }

    public static String ohlc(String ticker, String market, int days) {
        return "ohlc:" + norm(ticker, "") + ":" + norm(market, "US") + ":" + days;
    }

    public static String fundamentals(String ticker, String market) {
        return "f:" + norm(ticker, "") + ":" + norm(market, "US");
    }

    public static String news(String ticker, String market, int limit) {
        return "n:" + norm(ticker, "") + ":" + norm(market, "US") + ":" + limit;
    }

    public static String symbolSearch(String keywords, String market, boolean test) {
        return "s:" + norm(keywords, "") + ":" + norm(market, "US") + ":" + test;
    }

    public static String insights(InsightRequest req, boolean test) {
        String t = norm(req.ticker(), "");
        String m = norm(req.market(), "US");
        int days = normInt(req.days(), 90);
        int newsLimit = normInt(req.newsLimit(), 20);
        return "ins:" + t + ":" + m + ":" + test + ":" + days + ":" + newsLimit;
    }

    public static String report(InsightRequest req, boolean test, boolean web) {
        String t = norm(req.ticker(), "");
        String m = norm(req.market(), "US");
        int days = normInt(req.days(), 90);
        int newsLimit = normInt(req.newsLimit(), 20);
        return "rep:" + t + ":" + m + ":" + test + ":" + days + ":" + newsLimit + ":" + web;
    }
}
