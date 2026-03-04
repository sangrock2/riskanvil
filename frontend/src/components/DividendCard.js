import React from "react";
import styles from "../css/DividendCard.module.css";

/**
 * Card displaying dividend event information
 */
export default function DividendCard({ dividend }) {
    const formatDate = (dateString) => {
        if (!dateString) return "N/A";
        try {
            return new Date(dateString).toLocaleDateString();
        } catch {
            return dateString;
        }
    };

    const formatCurrency = (amount, currency = "USD") => {
        if (amount == null) return "N/A";
        const symbol = currency === "KRW" ? "₩" : "$";
        return `${symbol}${Number(amount).toFixed(2)}`;
    };

    const getDaysUntil = (dateString) => {
        if (!dateString) return null;
        try {
            const date = new Date(dateString);
            const today = new Date();
            const diffTime = date.getTime() - today.getTime();
            const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
            return diffDays;
        } catch {
            return null;
        }
    };

    const daysUntil = getDaysUntil(dividend.exDate);
    const isUpcoming = daysUntil !== null && daysUntil >= 0;
    const isPast = daysUntil !== null && daysUntil < 0;

    return (
        <div className={`${styles.card} ${isUpcoming ? styles.upcoming : isPast ? styles.past : ""}`}>
            <div className={styles.header}>
                <div className={styles.ticker}>{dividend.ticker}</div>
                {isUpcoming && daysUntil <= 7 && (
                    <span className={styles.badge}>In {daysUntil} days</span>
                )}
                {isPast && (
                    <span className={styles.badgePast}>Past</span>
                )}
            </div>

            <div className={styles.amount}>
                {formatCurrency(dividend.dividendPerShare || dividend.amount, dividend.currency)}
                <span className={styles.label}>per share</span>
            </div>

            {dividend.totalAmount && (
                <div className={styles.total}>
                    Total: {formatCurrency(dividend.totalAmount, dividend.currency)}
                    {dividend.quantity && (
                        <span className={styles.small}> ({dividend.quantity} shares)</span>
                    )}
                </div>
            )}

            <div className={styles.dates}>
                <div className={styles.dateRow}>
                    <span className={styles.dateLabel}>Ex-Date:</span>
                    <span className={styles.dateValue}>{formatDate(dividend.exDate)}</span>
                </div>

                {dividend.paymentDate && (
                    <div className={styles.dateRow}>
                        <span className={styles.dateLabel}>Payment:</span>
                        <span className={styles.dateValue}>{formatDate(dividend.paymentDate)}</span>
                    </div>
                )}

                {dividend.frequency && (
                    <div className={styles.dateRow}>
                        <span className={styles.dateLabel}>Frequency:</span>
                        <span className={styles.dateValue}>{dividend.frequency}</span>
                    </div>
                )}
            </div>
        </div>
    );
}
