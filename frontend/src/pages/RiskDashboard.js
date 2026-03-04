import { useEffect, useState } from "react";
import { usePortfolios, usePortfolioRiskDashboard } from "../hooks/queries";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from "recharts";
import { useTranslation } from "../hooks/useTranslation";
import styles from "../css/RiskDashboard.module.css";

function fmt(value, digits = 2) {
  if (value == null || Number.isNaN(Number(value))) return "-";
  return Number(value).toFixed(digits);
}

export default function RiskDashboard() {
  const { t } = useTranslation();
  const [selectedPortfolio, setSelectedPortfolio] = useState(null);
  const [lookbackDays, setLookbackDays] = useState(252);

  const { data: portfolios = [], isLoading: portfoliosLoading } = usePortfolios();
  const { data, isLoading, error } = usePortfolioRiskDashboard(selectedPortfolio, lookbackDays);

  useEffect(() => {
    if (!selectedPortfolio && portfolios.length > 0) {
      setSelectedPortfolio(portfolios[0].id);
    }
  }, [selectedPortfolio, portfolios]);

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>{t("riskPage.title")}</h1>
          <p className={styles.subtitle}>{t("riskPage.subtitle")}</p>
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
          <select className={styles.select} value={lookbackDays} onChange={(e) => setLookbackDays(Number(e.target.value))}>
            <option value={126}>{t("riskPage.months6")}</option>
            <option value={252}>{t("riskPage.year1")}</option>
            <option value={365}>{t("riskPage.months15")}</option>
          </select>
        </div>
      </header>

      {error && <div className={styles.error}>{error.message || t("riskPage.loadFailed")}</div>}

      {isLoading ? (
        <div className={styles.loading}>{t("riskPage.loading")}</div>
      ) : !data ? (
        <div className={styles.empty}>{t("riskPage.empty")}</div>
      ) : (
        <>
          <div className={styles.riskBadgeWrap}>
            <span className={`${styles.riskBadge} ${styles[`risk${(data.riskLevel || "LOW").toLowerCase()}`]}`}>
              {t("riskPage.riskLevel")}:{" "}
              {data.riskLevel === "HIGH"
                ? t("riskPage.levelHigh")
                : data.riskLevel === "MEDIUM"
                  ? t("riskPage.levelMedium")
                  : t("riskPage.levelLow")}
            </span>
          </div>

          <div className={styles.grid}>
            <div className={styles.card}><span>{t("riskPage.annualizedVolatility")}</span><strong>{fmt(data.annualizedVolatilityPct)}%</strong></div>
            <div className={styles.card}><span>{t("riskPage.maxDrawdown")}</span><strong>{fmt(data.maxDrawdownPct)}%</strong></div>
            <div className={styles.card}><span>{t("riskPage.var95")}</span><strong>{fmt(data.valueAtRisk95Pct)}%</strong></div>
            <div className={styles.card}><span>{t("riskPage.es95")}</span><strong>{fmt(data.expectedShortfall95Pct)}%</strong></div>
            <div className={styles.card}><span>{t("riskPage.sharpe")}</span><strong>{fmt(data.sharpeRatio)}</strong></div>
            <div className={styles.card}><span>{t("riskPage.beta")}</span><strong>{fmt(data.betaToMarket)}</strong></div>
            <div className={styles.card}><span>{t("riskPage.diversification")}</span><strong>{fmt(data.diversificationScore)} / 100</strong></div>
            <div className={styles.card}><span>{t("riskPage.concentration")}</span><strong>{fmt(data.concentrationScore)}%</strong></div>
          </div>

          {(data.timeSeries || []).length > 0 && (
            <div className={styles.section}>
              <h2>{t("riskPage.riskSeries")}</h2>
              <div className={styles.chartGrid}>
                <div className={styles.chartCard}>
                  <h3>{t("riskPage.drawdownSeries")}</h3>
                  <div className={styles.chartWrap}>
                    <ResponsiveContainer width="100%" height={260}>
                      <LineChart data={data.timeSeries}>
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                        <XAxis dataKey="date" minTickGap={24} tick={{ fontSize: 11 }} />
                        <YAxis tickFormatter={(v) => `${fmt(v, 1)}%`} tick={{ fontSize: 11 }} />
                        <Tooltip formatter={(v) => `${fmt(v, 2)}%`} />
                        <Line
                          type="monotone"
                          dataKey="drawdownPct"
                          stroke="var(--color-danger)"
                          strokeWidth={2}
                          dot={false}
                          isAnimationActive={false}
                        />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                </div>

                <div className={styles.chartCard}>
                  <h3>{t("riskPage.rollingVolSeries")}</h3>
                  <div className={styles.chartWrap}>
                    <ResponsiveContainer width="100%" height={260}>
                      <LineChart data={data.timeSeries}>
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                        <XAxis dataKey="date" minTickGap={24} tick={{ fontSize: 11 }} />
                        <YAxis tickFormatter={(v) => `${fmt(v, 1)}%`} tick={{ fontSize: 11 }} />
                        <Tooltip formatter={(v) => `${fmt(v, 2)}%`} />
                        <Line
                          type="monotone"
                          dataKey="rollingVolatilityPct"
                          stroke="var(--color-primary)"
                          strokeWidth={2}
                          dot={false}
                          connectNulls={false}
                          isAnimationActive={false}
                        />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              </div>
            </div>
          )}

          <div className={styles.section}>
            <h2>{t("riskPage.holdings")}</h2>
            <div className={styles.tableWrapper}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>{t("riskPage.ticker")}</th>
                    <th>{t("riskPage.market")}</th>
                    <th>{t("riskPage.weight")}</th>
                    <th>{t("riskPage.value")}</th>
                  </tr>
                </thead>
                <tbody>
                  {(data.holdings || []).map((h) => (
                    <tr key={`${h.ticker}-${h.market}`}>
                      <td><strong>{h.ticker}</strong></td>
                      <td>{h.market}</td>
                      <td>{fmt(h.weightPct)}%</td>
                      <td>${fmt(h.value)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
