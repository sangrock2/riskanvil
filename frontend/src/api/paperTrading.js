import { apiFetch } from "./http";

export async function getAccounts() {
    return apiFetch("/api/paper/accounts");
}

export async function resetAccount(market) {
    return apiFetch(`/api/paper/accounts/reset?market=${market}`, { method: "POST" });
}

export async function placeOrder({ market, ticker, direction, quantity }) {
    return apiFetch("/api/paper/order", {
        method: "POST",
        body: JSON.stringify({ market, ticker, direction, quantity }),
    });
}

export async function getPositions(market) {
    return apiFetch(`/api/paper/positions?market=${market}`);
}

export async function getOrders(market, page = 0, size = 20) {
    return apiFetch(`/api/paper/orders?market=${market}&page=${page}&size=${size}`);
}
