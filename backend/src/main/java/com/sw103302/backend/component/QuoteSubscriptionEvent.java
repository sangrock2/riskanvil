package com.sw103302.backend.component;

public record QuoteSubscriptionEvent(String action, String ticker) {
    public static QuoteSubscriptionEvent subscribe(String ticker) {
        return new QuoteSubscriptionEvent("subscribe", ticker);
    }

    public static QuoteSubscriptionEvent unsubscribe(String ticker) {
        return new QuoteSubscriptionEvent("unsubscribe", ticker);
    }
}
