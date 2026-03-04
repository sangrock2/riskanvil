import { useEffect, useState } from "react";
import { usePortfolios, usePortfolioEarningsCalendar } from "../hooks/queries";
import { useTranslation } from "../hooks/useTranslation";
import styles from "../css/EarningsCalendar.module.css";

export default function EarningsCalendar() {
  const { t } = useTranslation();
  const [selectedPortfolio, setSelectedPortfolio] = useState(null);
  const [daysAhead, setDaysAhead] = useState(90);

  const { data: portfolios = [], isLoading: portfoliosLoading } = usePortfolios();
  const { data, isLoading: calendarLoading, error } = usePortfolioEarningsCalendar(selectedPortfolio, daysAhead);

  useEffect(() => {
    if (!selectedPortfolio && portfolios.length > 0) {
      setSelectedPortfolio(portfolios[0].id);
    }
  }, [selectedPortfolio, portfolios]);

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>{t("earningsPage.title")}</h1>
          <p className={styles.subtitle}>{t("earningsPage.subtitle")}</p>
        </div>
        <div className={styles.controls}>
          <select
            className={styles.select}
            value={selectedPortfolio || ""}
            onChange={(e) => setSelectedPortfolio(Number(e.target.value))}
            disabled={portfoliosLoading || portfolios.length === 0}
          >
            {portfolios.map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
          <select
            className={styles.select}
            value={daysAhead}
            onChange={(e) => setDaysAhead(Number(e.target.value))}
          >
            <option value={30}>{t("earningsPage.days30")}</option>
            <option value={60}>{t("earningsPage.days60")}</option>
            <option value={90}>{t("earningsPage.days90")}</option>
            <option value={180}>{t("earningsPage.days180")}</option>
          </select>
        </div>
      </header>

      {error && <div className={styles.error}>{error.message || t("earningsPage.loadFailed")}</div>}

      {calendarLoading ? (
        <div className={styles.loading}>{t("earningsPage.loading")}</div>
      ) : !data || !data.events || data.events.length === 0 ? (
        <div className={styles.empty}>{t("earningsPage.empty")}</div>
      ) : (
        <>
          <div className={styles.summary}>
            <div className={styles.card}>
              <span className={styles.label}>{t("earningsPage.scheduledEvents")}</span>
              <span className={styles.value}>{data.events.length} {t("earningsPage.cases")}</span>
            </div>
            <div className={styles.card}>
              <span className={styles.label}>{t("earningsPage.range")}</span>
              <span className={styles.value}>{data.daysAhead} {t("earningsPage.days")}</span>
            </div>
          </div>

          <div className={styles.tableWrapper}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>{t("earningsPage.ticker")}</th>
                  <th>{t("earningsPage.market")}</th>
                  <th>{t("earningsPage.earningsDate")}</th>
                  <th>{t("earningsPage.epsEstimate")}</th>
                  <th>{t("earningsPage.epsActual")}</th>
                </tr>
              </thead>
              <tbody>
                {data.events.map((event, idx) => (
                  <tr key={`${event.ticker}-${event.earningsDate}-${idx}`}>
                    <td><strong>{event.ticker}</strong></td>
                    <td>{event.market}</td>
                    <td>{event.earningsDate || "-"}</td>
                    <td>{event.epsEstimate == null ? "-" : event.epsEstimate.toFixed(2)}</td>
                    <td>{event.epsActual == null ? "-" : event.epsActual.toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
