import { useMemo } from "react";
import styles from "../css/InsightConfidence.module.css";

const CONFIDENCE_LEVELS = {
  A: { label: "Very High", color: "#10b981", description: "Highly reliable analysis with complete data" },
  B: { label: "High", color: "#22c55e", description: "Reliable analysis with good data coverage" },
  C: { label: "Moderate", color: "#eab308", description: "Moderate confidence, some data gaps" },
  D: { label: "Low", color: "#f97316", description: "Lower confidence, significant data missing" },
  F: { label: "Very Low", color: "#ef4444", description: "Unreliable, insufficient data" },
};

export function ConfidenceBadge({ grade, confidence }) {
  const level = CONFIDENCE_LEVELS[grade] || CONFIDENCE_LEVELS.C;

  return (
    <div className={styles.badge} style={{ "--badge-color": level.color }}>
      <span className={styles.badgeGrade}>{grade}</span>
      <span className={styles.badgeLabel}>{level.label}</span>
      {confidence !== undefined && (
        <span className={styles.badgeValue}>{Math.round(confidence)}%</span>
      )}
    </div>
  );
}

export function ConfidenceDetails({ recommendation }) {
  const { confidence, confidenceGrade, dataCompleteness, breakdown } = recommendation || {};

  const level = CONFIDENCE_LEVELS[confidenceGrade] || CONFIDENCE_LEVELS.C;

  const dataQualityFactors = useMemo(() => {
    const factors = [];

    // Check data completeness
    if (dataCompleteness !== undefined) {
      factors.push({
        name: "Data Completeness",
        value: dataCompleteness * 100,
        status: dataCompleteness >= 0.8 ? "good" : dataCompleteness >= 0.5 ? "moderate" : "poor",
      });
    }

    // Check breakdown coverage
    if (Array.isArray(breakdown)) {
      const withData = breakdown.filter((b) => b.value !== null && b.value !== "N/A").length;
      const coverage = breakdown.length > 0 ? (withData / breakdown.length) * 100 : 0;
      factors.push({
        name: "Metric Coverage",
        value: coverage,
        status: coverage >= 80 ? "good" : coverage >= 60 ? "moderate" : "poor",
      });
    }

    return factors;
  }, [dataCompleteness, breakdown]);

  const insights = useMemo(() => {
    if (!Array.isArray(breakdown)) return [];

    return breakdown
      .filter((b) => b.evidence && b.rawScore != null && Number.isFinite(b.rawScore))
      .map((b) => ({
        metric: b.metric,
        score: b.rawScore,
        category: categorizeMetric(b.metric),
        evidence: b.evidence,
        impact: (b.contribution ?? 0) > 5 ? "high" : (b.contribution ?? 0) > 2 ? "medium" : "low",
      }))
      .sort((a, b) => Math.abs((b.score ?? 50) - 50) - Math.abs((a.score ?? 50) - 50));
  }, [breakdown]);

  const categorizedInsights = useMemo(() => {
    const categories = {
      valuation: { label: "Valuation", items: [] },
      technical: { label: "Technical", items: [] },
      fundamental: { label: "Fundamental", items: [] },
      sentiment: { label: "Sentiment", items: [] },
      other: { label: "Other", items: [] },
    };

    insights.forEach((insight) => {
      const cat = categories[insight.category] || categories.other;
      cat.items.push(insight);
    });

    return Object.entries(categories)
      .filter(([, cat]) => cat.items.length > 0)
      .map(([key, cat]) => ({ key, ...cat }));
  }, [insights]);

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h4 className={styles.title}>Analysis Confidence</h4>
        <ConfidenceBadge grade={confidenceGrade} confidence={confidence} />
      </div>

      <p className={styles.description}>{level.description}</p>

      {/* Data Quality Factors */}
      <div className={styles.factors}>
        <h5 className={styles.subtitle}>Data Quality</h5>
        {dataQualityFactors.map((factor) => (
          <div key={factor.name} className={styles.factor}>
            <div className={styles.factorHeader}>
              <span className={styles.factorName}>{factor.name}</span>
              <span className={`${styles.factorValue} ${styles[factor.status]}`}>
                {factor.value.toFixed(0)}%
              </span>
            </div>
            <div className={styles.progressBar}>
              <div
                className={`${styles.progressFill} ${styles[factor.status]}`}
                style={{ width: `${Math.min(factor.value, 100)}%` }}
              />
            </div>
          </div>
        ))}
      </div>

      {/* Categorized Insights */}
      {categorizedInsights.length > 0 && (
        <div className={styles.insights}>
          <h5 className={styles.subtitle}>Key Insights by Category</h5>
          {categorizedInsights.map((cat) => (
            <div key={cat.key} className={styles.category}>
              <div className={styles.categoryHeader}>
                <span className={styles.categoryLabel}>{cat.label}</span>
                <span className={styles.categoryCount}>{cat.items.length} factors</span>
              </div>
              <ul className={styles.insightList}>
                {cat.items.slice(0, 3).map((insight, idx) => (
                  <li key={idx} className={styles.insightItem}>
                    <div className={styles.insightHeader}>
                      <span className={styles.insightMetric}>{insight.metric}</span>
                      <span
                        className={`${styles.insightScore} ${
                          (insight.score ?? 50) >= 60
                            ? styles.positive
                            : (insight.score ?? 50) <= 40
                            ? styles.negative
                            : styles.neutral
                        }`}
                      >
                        {insight.score?.toFixed(0) ?? "N/A"}
                      </span>
                    </div>
                    <p className={styles.insightEvidence}>{insight.evidence}</p>
                    {insight.impact === "high" && (
                      <span className={styles.impactBadge}>High Impact</span>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function categorizeMetric(metric) {
  const lower = metric.toLowerCase();

  if (lower.includes("pe") || lower.includes("pb") || lower.includes("ps") || lower.includes("valuation")) {
    return "valuation";
  }
  if (lower.includes("sma") || lower.includes("ema") || lower.includes("rsi") || lower.includes("macd") || lower.includes("momentum")) {
    return "technical";
  }
  if (lower.includes("revenue") || lower.includes("earnings") || lower.includes("growth") || lower.includes("margin") || lower.includes("roe")) {
    return "fundamental";
  }
  if (lower.includes("sentiment") || lower.includes("news")) {
    return "sentiment";
  }
  return "other";
}

export default ConfidenceDetails;
