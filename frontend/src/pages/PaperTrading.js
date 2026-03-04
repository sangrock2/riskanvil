import { useState, useEffect, useCallback } from "react";
import { useTranslation } from "../hooks/useTranslation";
import { getAccounts, resetAccount, placeOrder, getOrders } from "../api/paperTrading";
import styles from "../css/PaperTrading.module.css";

const MARKETS = ["US", "KR"];

function formatAmount(amount, currency) {
    if (currency === "KRW") {
        return "₩" + Number(amount).toLocaleString("ko-KR", { maximumFractionDigits: 0 });
    }
    return "$" + Number(amount).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatPrice(price, currency) {
    return formatAmount(price, currency);
}

export default function PaperTrading() {
    const { t, language } = useTranslation();
    const [activeMarket, setActiveMarket] = useState("US");
    const [accounts, setAccounts] = useState([]);
    const [orders, setOrders] = useState([]);
    const [ordersPage, setOrdersPage] = useState(0);
    const [totalOrderPages, setTotalOrderPages] = useState(0);
    const [loading, setLoading] = useState(true);
    const [orderLoading, setOrderLoading] = useState(false);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");

    // Order form state
    const [ticker, setTicker] = useState("");
    const [direction, setDirection] = useState("BUY");
    const [quantity, setQuantity] = useState("");

    const currentAccount = accounts.find(a => a.market === activeMarket);

    const loadAccounts = useCallback(async () => {
        try {
            setLoading(true);
            const data = await getAccounts();
            setAccounts(data);
        } catch (e) {
            setError(e.message || t("paper.failedToLoad"));
        } finally {
            setLoading(false);
        }
    }, [t]);

    const loadOrders = useCallback(async (page = 0) => {
        try {
            const data = await getOrders(activeMarket, page, 20);
            setOrders(data.orders || []);
            setTotalOrderPages(data.totalPages || 0);
            setOrdersPage(page);
        } catch (e) {
            // orders load failure is non-critical
        }
    }, [activeMarket]);

    useEffect(() => {
        loadAccounts();
    }, [loadAccounts]);

    useEffect(() => {
        loadOrders(0);
    }, [loadOrders]);

    async function handleReset() {
        if (!window.confirm(t("paper.confirmReset"))) return;
        try {
            setError("");
            const updated = await resetAccount(activeMarket);
            setAccounts(prev => prev.map(a => a.market === activeMarket ? updated : a));
            setOrders([]);
            setSuccess(t("paper.resetSuccess"));
            setTimeout(() => setSuccess(""), 3000);
        } catch (e) {
            setError(e.message || t("paper.resetFailed"));
        }
    }

    async function handleOrder(e) {
        e.preventDefault();
        setError("");
        setSuccess("");
        if (!ticker.trim()) {
            setError(t("paper.tickerRequired"));
            return;
        }
        const qty = parseFloat(quantity);
        if (!qty || qty <= 0) {
            setError(t("paper.quantityRequired"));
            return;
        }

        try {
            setOrderLoading(true);
            const result = await placeOrder({
                market: activeMarket,
                ticker: ticker.trim().toUpperCase(),
                direction,
                quantity: qty
            });

            const currency = activeMarket === "KR" ? "KRW" : "USD";
            setSuccess(
                t("paper.orderSuccess", {
                    action: direction === "BUY" ? t("paper.bought") : t("paper.sold"),
                    ticker: result.ticker,
                    quantity: result.quantity,
                    unit: t("paper.sharesUnit"),
                    price: formatPrice(result.price, currency),
                    remaining: t("paper.remaining"),
                    balance: formatAmount(result.balanceAfter, currency)
                })
            );

            setTicker("");
            setQuantity("");
            await loadAccounts();
            await loadOrders(0);
            setTimeout(() => setSuccess(""), 5000);
        } catch (e) {
            setError(e.message || t("paper.orderFailed"));
        } finally {
            setOrderLoading(false);
        }
    }

    const account = currentAccount;
    const currency = activeMarket === "KR" ? "KRW" : "USD";
    const positions = account?.positions || [];

    // Calculate total position value
    const totalPositionValue = positions.reduce((sum, p) => sum + Number(p.currentValue), 0);
    const totalPnl = positions.reduce((sum, p) => sum + Number(p.unrealizedGain), 0);
    const totalAssets = account ? Number(account.balance) + totalPositionValue : 0;
    const totalReturn = account ? ((totalAssets - Number(account.initialBalance)) / Number(account.initialBalance)) * 100 : 0;

    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <h1>{t("nav.paperTrading")}</h1>
                <span style={{ fontSize: "var(--text-sm)", color: "var(--color-text-muted)" }}>
                    {t("paper.subtitle")}
                </span>
            </div>

            {/* Market Tabs */}
            <div className={styles.tabs}>
                {MARKETS.map(m => (
                    <button
                        key={m}
                        className={`${styles.tab} ${activeMarket === m ? styles.active : ""}`}
                        onClick={() => { setActiveMarket(m); setError(""); setSuccess(""); }}
                    >
                        {m === "US" ? t("paper.usMarket") : t("paper.krMarket")}
                    </button>
                ))}
            </div>

            {error && <div className={styles.errorMsg}>{error}</div>}
            {success && <div className={styles.successMsg}>{success}</div>}

            {/* Account Summary */}
            {account && (
                <div className={styles.accountCard}>
                    <div className={styles.accountGrid}>
                        <div className={styles.accountStat}>
                            <label>{t("paper.balance")}</label>
                            <span className={styles.value}>{formatAmount(account.balance, currency)}</span>
                        </div>
                        <div className={styles.accountStat}>
                            <label>{t("paper.positionValue")}</label>
                            <span className={styles.value}>{formatAmount(totalPositionValue, currency)}</span>
                        </div>
                        <div className={styles.accountStat}>
                            <label>{t("paper.totalAssets")}</label>
                            <span className={styles.value}>{formatAmount(totalAssets, currency)}</span>
                        </div>
                        <div className={styles.accountStat}>
                            <label>{t("paper.unrealizedPnl")}</label>
                            <span className={`${styles.value} ${totalPnl >= 0 ? styles.positive : styles.negative}`}>
                                {totalPnl >= 0 ? "+" : ""}{formatAmount(totalPnl, currency)}
                            </span>
                        </div>
                        <div className={styles.accountStat}>
                            <label>{t("paper.totalReturn")}</label>
                            <span className={`${styles.value} ${totalReturn >= 0 ? styles.positive : styles.negative}`}>
                                {totalReturn >= 0 ? "+" : ""}{totalReturn.toFixed(2)}%
                            </span>
                        </div>
                    </div>
                    <button className={styles.resetBtn} onClick={handleReset}>
                        {t("paper.reset")}
                    </button>
                </div>
            )}

            {/* Order Form */}
            <div className={styles.orderForm}>
                <h2>{t("paper.placeOrder")}</h2>
                <form onSubmit={handleOrder}>
                    <div className={styles.formGrid}>
                        <div className={styles.formGroup}>
                            <label>{t("paper.ticker")}</label>
                            <input
                                className={styles.input}
                                value={ticker}
                                onChange={e => setTicker(e.target.value)}
                                placeholder={activeMarket === "US" ? "AAPL" : "005930"}
                                autoComplete="off"
                            />
                        </div>
                        <div className={styles.formGroup}>
                            <label>{t("paper.direction")}</label>
                            <div className={styles.directionBtns}>
                                <button
                                    type="button"
                                    className={`${styles.buyBtn} ${direction === "BUY" ? styles.selected : ""}`}
                                    onClick={() => setDirection("BUY")}
                                >
                                    {t("paper.buy")}
                                </button>
                                <button
                                    type="button"
                                    className={`${styles.sellBtn} ${direction === "SELL" ? styles.selected : ""}`}
                                    onClick={() => setDirection("SELL")}
                                >
                                    {t("paper.sell")}
                                </button>
                            </div>
                        </div>
                        <div className={styles.formGroup}>
                            <label>{t("paper.quantity")}</label>
                            <input
                                className={styles.input}
                                type="number"
                                min="0.0001"
                                step="1"
                                value={quantity}
                                onChange={e => setQuantity(e.target.value)}
                                placeholder="10"
                            />
                        </div>
                        <div className={styles.formGroup}>
                            <label>&nbsp;</label>
                            <button
                                type="submit"
                                className={styles.submitBtn}
                                disabled={orderLoading}
                            >
                                {orderLoading ? t("paper.executing") : t("paper.execute")}
                            </button>
                        </div>
                    </div>
                </form>
                <div style={{ fontSize: "var(--text-xs)", color: "var(--color-text-muted)", marginTop: "var(--space-2)" }}>
                    * {t("paper.commissionNote")}
                </div>
            </div>

            {/* Positions */}
            <div className={styles.section}>
                <h2>{t("paper.positions")} ({positions.length})</h2>
                {loading ? (
                    <div className={styles.empty}>{t("loading")}</div>
                ) : positions.length === 0 ? (
                    <div className={styles.empty}>{t("paper.noPositions")}</div>
                ) : (
                    <div className={styles.tableWrapper}>
                        <table className={styles.table}>
                            <thead>
                                <tr>
                                    <th>{t("paper.ticker")}</th>
                                    <th>{t("paper.quantity")}</th>
                                    <th>{t("paper.avgPrice")}</th>
                                    <th>{t("paper.currentPrice")}</th>
                                    <th>{t("paper.currentValue")}</th>
                                    <th>{t("paper.unrealizedPnl")}</th>
                                    <th>{t("paper.return")}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {positions.map(pos => (
                                    <tr key={pos.id}>
                                        <td><strong>{pos.ticker}</strong></td>
                                        <td>{Number(pos.quantity).toLocaleString()}</td>
                                        <td>{formatPrice(pos.avgPrice, currency)}</td>
                                        <td>{formatPrice(pos.currentPrice, currency)}</td>
                                        <td>{formatAmount(pos.currentValue, currency)}</td>
                                        <td className={pos.unrealizedGain >= 0 ? styles.positive : styles.negative}>
                                            {pos.unrealizedGain >= 0 ? "+" : ""}{formatAmount(pos.unrealizedGain, currency)}
                                        </td>
                                        <td className={pos.unrealizedGainPct >= 0 ? styles.positive : styles.negative}>
                                            {pos.unrealizedGainPct >= 0 ? "+" : ""}{Number(pos.unrealizedGainPct).toFixed(2)}%
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {/* Order History */}
            <div className={styles.section}>
                <h2>{t("paper.orderHistory")}</h2>
                {orders.length === 0 ? (
                    <div className={styles.empty}>{t("paper.noOrders")}</div>
                ) : (
                    <>
                        <div className={styles.tableWrapper}>
                            <table className={styles.table}>
                                <thead>
                                    <tr>
                                        <th>{t("paper.date")}</th>
                                        <th>{t("paper.ticker")}</th>
                                        <th>{t("paper.direction")}</th>
                                        <th>{t("paper.quantity")}</th>
                                        <th>{t("paper.price")}</th>
                                        <th>{t("paper.amount")}</th>
                                        <th>{t("paper.commission")}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {orders.map(order => (
                                        <tr key={order.id}>
                                            <td>{new Date(order.createdAt).toLocaleString(language === "en" ? "en-US" : "ko-KR")}</td>
                                            <td><strong>{order.ticker}</strong></td>
                                            <td>
                                                <span className={`${styles.badge} ${order.direction === "BUY" ? styles.buy : styles.sell}`}>
                                                    {order.direction === "BUY" ? t("paper.buy") : t("paper.sell")}
                                                </span>
                                            </td>
                                            <td>{Number(order.quantity).toLocaleString()}</td>
                                            <td>{formatPrice(order.price, currency)}</td>
                                            <td>{formatAmount(order.amount, currency)}</td>
                                            <td>{formatAmount(order.commission, currency)}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                        {totalOrderPages > 1 && (
                            <div className={styles.pagination}>
                                <button
                                    className={styles.pageBtn}
                                    disabled={ordersPage === 0}
                                    onClick={() => loadOrders(ordersPage - 1)}
                                >
                                    ←
                                </button>
                                <span className={styles.pageInfo}>
                                    {ordersPage + 1} / {totalOrderPages}
                                </span>
                                <button
                                    className={styles.pageBtn}
                                    disabled={ordersPage >= totalOrderPages - 1}
                                    onClick={() => loadOrders(ordersPage + 1)}
                                >
                                    →
                                </button>
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}
