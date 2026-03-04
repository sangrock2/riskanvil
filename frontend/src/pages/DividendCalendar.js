import React, { useState, useEffect } from "react";
import { useToast } from "../components/ui/Toast";
import { useTranslation } from "../hooks/useTranslation";
import { usePortfolios } from "../hooks/queries";
import { useDividendCalendar } from "../hooks/queries";
import DividendCard from "../components/DividendCard";
import styles from "../css/DividendCalendar.module.css";

export default function DividendCalendar() {
    const { t } = useTranslation();
    const [selectedPortfolio, setSelectedPortfolio] = useState(null);
    const toast = useToast();

    // React Query hooks
    const { data: portfolios = [] } = usePortfolios();
    const { data: calendar, isLoading: loading, error: calendarError } = useDividendCalendar(selectedPortfolio);
    const error = calendarError?.message || "";

    // Auto-select first portfolio
    useEffect(() => {
        if (portfolios.length > 0 && !selectedPortfolio) {
            setSelectedPortfolio(portfolios[0].id);
        }
    }, [portfolios, selectedPortfolio]);

    const formatCurrency = (amount, defaultCurrency = "USD") => {
        if (amount == null) return "$0.00";
        const symbol = defaultCurrency === "KRW" ? "₩" : "$";
        return `${symbol}${Number(amount).toFixed(2)}`;
    };

    const upcomingCount = calendar?.upcoming?.length || 0;
    const pastCount = calendar?.past?.length || 0;

    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <div>
                    <h1 className={styles.title}>{t("dividends.title")}</h1>
                    <p className={styles.subtitle}>{t("dividends.subtitle")}</p>
                </div>

                {portfolios.length > 0 && (
                    <select
                        className={styles.select}
                        value={selectedPortfolio || ""}
                        onChange={(e) => setSelectedPortfolio(Number(e.target.value))}
                    >
                        {portfolios.map((p) => (
                            <option key={p.id} value={p.id}>
                                {p.name}
                            </option>
                        ))}
                    </select>
                )}
            </div>

            {error && <div className={styles.error}>{error}</div>}

            {loading ? (
                <div className={styles.loading}>{t("dividends.loadingCalendar")}</div>
            ) : !calendar ? (
                <div className={styles.empty}>
                    {t("dividends.selectPortfolio")}
                </div>
            ) : (
                <>
                    {/* Summary Cards */}
                    <div className={styles.summaryGrid}>
                        <div className={styles.summaryCard}>
                            <div className={styles.summaryLabel}>{t("dividends.upcomingDividends")}</div>
                            <div className={styles.summaryValue}>
                                {formatCurrency(calendar.totalUpcoming)}
                            </div>
                            <div className={styles.summaryCount}>{upcomingCount} {t("dividends.payments")}</div>
                        </div>

                        <div className={styles.summaryCard}>
                            <div className={styles.summaryLabel}>{t("dividends.pastDividends30d")}</div>
                            <div className={styles.summaryValue}>
                                {formatCurrency(calendar.totalPast)}
                            </div>
                            <div className={styles.summaryCount}>{pastCount} {t("dividends.payments")}</div>
                        </div>

                        <div className={styles.summaryCard}>
                            <div className={styles.summaryLabel}>{t("dividends.totalIncome")}</div>
                            <div className={styles.summaryValue}>
                                {formatCurrency((calendar.totalUpcoming || 0) + (calendar.totalPast || 0))}
                            </div>
                            <div className={styles.summaryCount}>{t("dividends.combined")}</div>
                        </div>
                    </div>

                    {/* Upcoming Dividends */}
                    <div className={styles.section}>
                        <h2 className={styles.sectionTitle}>
                            {t("dividends.upcomingPayments")}
                            <span className={styles.badge}>{upcomingCount}</span>
                        </h2>

                        {upcomingCount === 0 ? (
                            <div className={styles.empty}>{t("dividends.noUpcomingPayments")}</div>
                        ) : (
                            <div className={styles.grid}>
                                {calendar.upcoming.map((div, idx) => (
                                    <DividendCard key={idx} dividend={div} />
                                ))}
                            </div>
                        )}
                    </div>

                    {/* Past Dividends */}
                    <div className={styles.section}>
                        <h2 className={styles.sectionTitle}>
                            {t("dividends.recentPayments30d")}
                            <span className={styles.badge}>{pastCount}</span>
                        </h2>

                        {pastCount === 0 ? (
                            <div className={styles.empty}>{t("dividends.noRecentPayments")}</div>
                        ) : (
                            <div className={styles.grid}>
                                {calendar.past.map((div, idx) => (
                                    <DividendCard key={idx} dividend={div} />
                                ))}
                            </div>
                        )}
                    </div>
                </>
            )}

            {portfolios.length === 0 && !loading && (
                <div className={styles.emptyState}>
                    <h3>{t("dividends.noPortfoliosFound")}</h3>
                    <p>{t("dividends.createPortfolioFirst")}</p>
                    <a href="/portfolio" className={styles.link}>{t("dividends.goToPortfolio")}</a>
                </div>
            )}
        </div>
    );
}
