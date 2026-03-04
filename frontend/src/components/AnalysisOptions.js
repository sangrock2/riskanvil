import { useState, useCallback, useMemo } from "react";
import styles from "../css/AnalysisOptions.module.css";

const PERIOD_OPTIONS = [
  { value: 30, label: "30 Days", description: "Short-term analysis" },
  { value: 60, label: "60 Days", description: "Medium-short term" },
  { value: 90, label: "90 Days", description: "Standard analysis" },
  { value: 180, label: "180 Days", description: "Medium-long term" },
  { value: 365, label: "1 Year", description: "Long-term trends" },
];

const INDICATOR_CATEGORIES = {
  technical: {
    label: "Technical Indicators",
    indicators: [
      { id: "sma", label: "SMA (Simple Moving Average)", default: true },
      { id: "ema", label: "EMA (Exponential Moving Average)", default: true },
      { id: "rsi", label: "RSI (Relative Strength Index)", default: true },
      { id: "macd", label: "MACD", default: true },
      { id: "bollinger", label: "Bollinger Bands", default: false },
      { id: "obv", label: "OBV (On-Balance Volume)", default: false },
      { id: "atr", label: "ATR (Average True Range)", default: false },
    ],
  },
  fundamental: {
    label: "Fundamental Indicators",
    indicators: [
      { id: "pe", label: "P/E Ratio", default: true },
      { id: "pb", label: "P/B Ratio", default: true },
      { id: "ps", label: "P/S Ratio", default: true },
      { id: "roe", label: "ROE", default: true },
      { id: "revenue", label: "Revenue Growth", default: true },
      { id: "eps", label: "EPS Growth", default: false },
      { id: "dividend", label: "Dividend Yield", default: false },
    ],
  },
  sentiment: {
    label: "Sentiment Analysis",
    indicators: [
      { id: "news", label: "News Sentiment", default: true },
      { id: "social", label: "Social Media Sentiment", default: false },
      { id: "analyst", label: "Analyst Ratings", default: true },
    ],
  },
};

const BENCHMARK_OPTIONS = [
  { value: "", label: "None" },
  { value: "SPY", label: "S&P 500 (SPY)" },
  { value: "QQQ", label: "NASDAQ 100 (QQQ)" },
  { value: "DIA", label: "Dow Jones (DIA)" },
  { value: "IWM", label: "Russell 2000 (IWM)" },
  { value: "VTI", label: "Total Market (VTI)" },
];

const DEFAULT_OPTIONS = {
  period: 90,
  indicators: Object.fromEntries(
    Object.values(INDICATOR_CATEGORIES).flatMap((cat) =>
      cat.indicators.map((ind) => [ind.id, ind.default])
    )
  ),
  benchmark: "",
  newsLimit: 20,
  includeForecasts: true,
  compareWithSector: true,
};

function loadSavedOptions() {
  try {
    const saved = localStorage.getItem("analysis_options");
    if (saved) {
      return { ...DEFAULT_OPTIONS, ...JSON.parse(saved) };
    }
  } catch (e) {
    console.error("Failed to load analysis options:", e);
  }
  return DEFAULT_OPTIONS;
}

function saveOptions(options) {
  try {
    localStorage.setItem("analysis_options", JSON.stringify(options));
  } catch (e) {
    console.error("Failed to save analysis options:", e);
  }
}

export default function AnalysisOptions({ onOptionsChange, initialOptions }) {
  const [options, setOptions] = useState(() => ({
    ...loadSavedOptions(),
    ...(initialOptions || {}),
  }));
  const [expanded, setExpanded] = useState(false);
  const [activeTab, setActiveTab] = useState("period");

  const updateOption = useCallback(
    (key, value) => {
      setOptions((prev) => {
        const updated = { ...prev, [key]: value };
        saveOptions(updated);
        if (onOptionsChange) {
          onOptionsChange(updated);
        }
        return updated;
      });
    },
    [onOptionsChange]
  );

  const toggleIndicator = useCallback(
    (id) => {
      setOptions((prev) => {
        const updated = {
          ...prev,
          indicators: {
            ...prev.indicators,
            [id]: !prev.indicators[id],
          },
        };
        saveOptions(updated);
        if (onOptionsChange) {
          onOptionsChange(updated);
        }
        return updated;
      });
    },
    [onOptionsChange]
  );

  const selectAllInCategory = useCallback(
    (category, select) => {
      setOptions((prev) => {
        const catIndicators = INDICATOR_CATEGORIES[category].indicators;
        const indicatorUpdates = Object.fromEntries(
          catIndicators.map((ind) => [ind.id, select])
        );
        const updated = {
          ...prev,
          indicators: {
            ...prev.indicators,
            ...indicatorUpdates,
          },
        };
        saveOptions(updated);
        if (onOptionsChange) {
          onOptionsChange(updated);
        }
        return updated;
      });
    },
    [onOptionsChange]
  );

  const resetToDefault = useCallback(() => {
    setOptions(DEFAULT_OPTIONS);
    saveOptions(DEFAULT_OPTIONS);
    if (onOptionsChange) {
      onOptionsChange(DEFAULT_OPTIONS);
    }
  }, [onOptionsChange]);

  const selectedIndicatorCount = useMemo(() => {
    return Object.values(options.indicators).filter(Boolean).length;
  }, [options.indicators]);

  return (
    <div className={styles.container}>
      <button
        className={styles.toggleBtn}
        onClick={() => setExpanded(!expanded)}
        aria-expanded={expanded}
      >
        <span className={styles.toggleIcon}>{expanded ? "▼" : "▶"}</span>
        <span>Analysis Options</span>
        <span className={styles.badge}>
          {options.period}d / {selectedIndicatorCount} indicators
          {options.benchmark && ` / vs ${options.benchmark}`}
        </span>
      </button>

      {expanded && (
        <div className={styles.panel}>
          <div className={styles.tabs}>
            <button
              className={`${styles.tab} ${activeTab === "period" ? styles.tabActive : ""}`}
              onClick={() => setActiveTab("period")}
            >
              Period
            </button>
            <button
              className={`${styles.tab} ${activeTab === "indicators" ? styles.tabActive : ""}`}
              onClick={() => setActiveTab("indicators")}
            >
              Indicators
            </button>
            <button
              className={`${styles.tab} ${activeTab === "comparison" ? styles.tabActive : ""}`}
              onClick={() => setActiveTab("comparison")}
            >
              Comparison
            </button>
            <button
              className={`${styles.tab} ${activeTab === "advanced" ? styles.tabActive : ""}`}
              onClick={() => setActiveTab("advanced")}
            >
              Advanced
            </button>
          </div>

          <div className={styles.tabContent}>
            {activeTab === "period" && (
              <div className={styles.section}>
                <h4 className={styles.sectionTitle}>Analysis Period</h4>
                <p className={styles.sectionDesc}>
                  Select the time period for historical data analysis
                </p>
                <div className={styles.periodGrid}>
                  {PERIOD_OPTIONS.map((opt) => (
                    <button
                      key={opt.value}
                      className={`${styles.periodBtn} ${options.period === opt.value ? styles.periodActive : ""}`}
                      onClick={() => updateOption("period", opt.value)}
                    >
                      <span className={styles.periodLabel}>{opt.label}</span>
                      <span className={styles.periodDesc}>{opt.description}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {activeTab === "indicators" && (
              <div className={styles.section}>
                <h4 className={styles.sectionTitle}>Select Indicators</h4>
                <p className={styles.sectionDesc}>
                  Choose which indicators to include in the analysis
                </p>

                {Object.entries(INDICATOR_CATEGORIES).map(([key, cat]) => (
                  <div key={key} className={styles.indicatorCategory}>
                    <div className={styles.categoryHeader}>
                      <span className={styles.categoryLabel}>{cat.label}</span>
                      <div className={styles.categoryActions}>
                        <button
                          className={styles.smallBtn}
                          onClick={() => selectAllInCategory(key, true)}
                        >
                          All
                        </button>
                        <button
                          className={styles.smallBtn}
                          onClick={() => selectAllInCategory(key, false)}
                        >
                          None
                        </button>
                      </div>
                    </div>
                    <div className={styles.indicatorGrid}>
                      {cat.indicators.map((ind) => (
                        <label key={ind.id} className={styles.indicatorLabel}>
                          <input
                            type="checkbox"
                            checked={options.indicators[ind.id] || false}
                            onChange={() => toggleIndicator(ind.id)}
                          />
                          <span>{ind.label}</span>
                        </label>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}

            {activeTab === "comparison" && (
              <div className={styles.section}>
                <h4 className={styles.sectionTitle}>Benchmark Comparison</h4>
                <p className={styles.sectionDesc}>
                  Compare performance against a market benchmark
                </p>

                <div className={styles.benchmarkGrid}>
                  {BENCHMARK_OPTIONS.map((opt) => (
                    <button
                      key={opt.value}
                      className={`${styles.benchmarkBtn} ${options.benchmark === opt.value ? styles.benchmarkActive : ""}`}
                      onClick={() => updateOption("benchmark", opt.value)}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>

                <div className={styles.checkboxGroup}>
                  <label className={styles.checkboxLabel}>
                    <input
                      type="checkbox"
                      checked={options.compareWithSector}
                      onChange={(e) =>
                        updateOption("compareWithSector", e.target.checked)
                      }
                    />
                    Compare with sector average
                  </label>
                </div>
              </div>
            )}

            {activeTab === "advanced" && (
              <div className={styles.section}>
                <h4 className={styles.sectionTitle}>Advanced Options</h4>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>News Article Limit</label>
                  <select
                    className={styles.select}
                    value={options.newsLimit}
                    onChange={(e) =>
                      updateOption("newsLimit", parseInt(e.target.value, 10))
                    }
                  >
                    <option value={10}>10 articles</option>
                    <option value={20}>20 articles</option>
                    <option value={30}>30 articles</option>
                    <option value={50}>50 articles</option>
                  </select>
                </div>

                <div className={styles.checkboxGroup}>
                  <label className={styles.checkboxLabel}>
                    <input
                      type="checkbox"
                      checked={options.includeForecasts}
                      onChange={(e) =>
                        updateOption("includeForecasts", e.target.checked)
                      }
                    />
                    Include AI price forecasts (experimental)
                  </label>
                </div>
              </div>
            )}
          </div>

          <div className={styles.footer}>
            <button className={styles.resetBtn} onClick={resetToDefault}>
              Reset to Default
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// Export for use in other components
export { DEFAULT_OPTIONS, PERIOD_OPTIONS, INDICATOR_CATEGORIES, BENCHMARK_OPTIONS };
