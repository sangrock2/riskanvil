import { useState, useEffect } from "react";
import { getETFInfo, getETFHoldings } from "../api/etf";
import { useToast } from "./ui/Toast";
import styles from "../css/ETFInfo.module.css";

export default function ETFInfo({ ticker, market = "US" }) {
    const [info, setInfo] = useState(null);
    const [holdings, setHoldings] = useState(null);
    const [loading, setLoading] = useState(true);
    const [showHoldings, setShowHoldings] = useState(false);
    const toast = useToast();

    useEffect(() => {
        loadETFInfo();
    }, [ticker, market]);

    const loadETFInfo = async () => {
        setLoading(true);
        try {
            const data = await getETFInfo(ticker, market);
            setInfo(data);

            // Only show ETF-specific info if it's actually an ETF
            if (!data.isETF) {
                setInfo(null);
            }
        } catch (e) {
            console.error("Failed to load ETF info:", e);
        } finally {
            setLoading(false);
        }
    };

    const loadHoldings = async () => {
        try {
            const data = await getETFHoldings(ticker, market);
            setHoldings(data);
            setShowHoldings(true);
        } catch (e) {
            toast.error("Failed to load ETF holdings");
        }
    };

    if (loading) return <div className={styles.loading}>Loading ETF info...</div>;
    if (!info || !info.isETF) return null;

    const formatPercent = (val) => {
        if (val == null) return "N/A";
        return `${(val * 100).toFixed(2)}%`;
    };

    const formatCurrency = (val) => {
        if (val == null) return "N/A";
        if (val > 1e9) return `$${(val / 1e9).toFixed(2)}B`;
        if (val > 1e6) return `$${(val / 1e6).toFixed(2)}M`;
        return `$${val.toFixed(2)}`;
    };

    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <h3 className={styles.title}>📊 ETF Information</h3>
                <span className={styles.badge}>ETF</span>
            </div>

            <div className={styles.grid}>
                <div className={styles.item}>
                    <div className={styles.label}>Name</div>
                    <div className={styles.value}>{info.name || "N/A"}</div>
                </div>

                <div className={styles.item}>
                    <div className={styles.label}>Category</div>
                    <div className={styles.value}>{info.category || "N/A"}</div>
                </div>

                <div className={styles.item}>
                    <div className={styles.label}>Total Assets</div>
                    <div className={styles.value}>{formatCurrency(info.totalAssets)}</div>
                </div>

                <div className={styles.item}>
                    <div className={styles.label}>Expense Ratio</div>
                    <div className={styles.value}>{formatPercent(info.expenseRatio)}</div>
                </div>

                <div className={styles.item}>
                    <div className={styles.label}>YTD Return</div>
                    <div className={styles.value}>{formatPercent(info.ytdReturn)}</div>
                </div>

                {!showHoldings && (
                    <div className={styles.item}>
                        <button className={styles.btn} onClick={loadHoldings}>
                            View Holdings
                        </button>
                    </div>
                )}
            </div>

            {showHoldings && holdings && (
                <div className={styles.holdingsSection}>
                    <h4 className={styles.subtitle}>Top Holdings</h4>
                    {holdings.topHoldings && holdings.topHoldings.length > 0 ? (
                        <ul className={styles.holdingsList}>
                            {holdings.topHoldings.slice(0, 10).map((holding, idx) => (
                                <li key={idx} className={styles.holdingItem}>
                                    <span className={styles.holdingName}>{holding.name || holding.symbol}</span>
                                    <span className={styles.holdingWeight}>
                                        {formatPercent(holding.weight || holding.holdingPercent)}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <p className={styles.noData}>Holdings data not available</p>
                    )}
                </div>
            )}
        </div>
    );
}
