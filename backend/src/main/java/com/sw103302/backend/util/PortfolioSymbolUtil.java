package com.sw103302.backend.util;

import com.sw103302.backend.entity.PortfolioPosition;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class PortfolioSymbolUtil {

    private PortfolioSymbolUtil() {
    }

    public static String symbolKey(String ticker, String market) {
        return (ticker == null ? "" : ticker.toUpperCase()) + ":" + (market == null ? "US" : market.toUpperCase());
    }

    public static String symbolKey(PortfolioPosition pos) {
        return symbolKey(pos.getTicker(), pos.getMarket());
    }

    public static Map<String, List<String>> groupUniqueTickersByMarket(List<PortfolioPosition> positions) {
        return positions.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getMarket().toUpperCase(),
                        Collectors.collectingAndThen(
                                Collectors.mapping(p -> p.getTicker().toUpperCase(), Collectors.toCollection(LinkedHashSet::new)),
                                List::copyOf
                        )
                ));
    }
}
