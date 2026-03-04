import { apiFetch } from "./http";

export async function fetchInsights(baseUrl, req) {
    const res = await fetch(`${baseUrl}/insights?test=false`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            market: req.market || "US",
            ticker: req.ticker,
            days: req.days ?? 90,
            newsLimit: req.newsLimit ?? 10,
        }),
    });

    if (!res.ok) {
        const text = await res.text().catch(() => "");
        throw new Error(text || `HTTP ${res.status}`);
    }

    return res.json();
}

export async function fetchOHLC(ticker, market = "US", days = 90) {
    return apiFetch(`/api/market/ohlc?ticker=${encodeURIComponent(ticker)}&market=${encodeURIComponent(market)}&days=${days}`);
}
