import { useState, useCallback, useRef } from "react";
import { useTranslation } from "../hooks/useTranslation";
import { ssePost } from "../api/sseFetch";
import { exportReportPdf } from "../api/report";
import styles from "../css/ReportGenerator.module.css";

// Translation keys will be used directly in the component
const REPORT_SECTIONS = [
  { key: "executive_summary", translationKey: "report.executiveSummary", default: true },
  { key: "price_analysis", translationKey: "report.priceAnalysis", default: true },
  { key: "fundamentals", translationKey: "report.fundamentals", default: true },
  { key: "technical_indicators", translationKey: "report.technicalIndicators", default: true },
  { key: "news_sentiment", translationKey: "report.newsSentiment", default: true },
  { key: "risk_assessment", translationKey: "report.riskAssessment", default: true },
  { key: "recommendation", translationKey: "report.recommendation", default: true },
];

const REPORT_TEMPLATES = [
  { key: "detailed", labelKey: "report.detailed", descriptionKey: "report.detailedDescription" },
  { key: "summary", labelKey: "report.summary", descriptionKey: "report.summaryDescription" },
  { key: "technical", labelKey: "report.technical", descriptionKey: "report.technicalDescription" },
  { key: "fundamental", labelKey: "report.fundamental", descriptionKey: "report.fundamentalDescription" },
];

export default function ReportGenerator({
  ticker,
  market,
  insights,
  onReportGenerated,
}) {
  const { t } = useTranslation();
  const [sections, setSections] = useState(() =>
    Object.fromEntries(
      REPORT_SECTIONS.map(({ key, default: defaultValue }) => [key, defaultValue])
    )
  );
  const [template, setTemplate] = useState("detailed");
  const [loading, setLoading] = useState(false);
  const [reportText, setReportText] = useState("");
  const [error, setError] = useState("");
  const [showOptions, setShowOptions] = useState(false);

  const reportRef = useRef(null);
  const connRef = useRef(null);

  const toggleSection = useCallback((key) => {
    setSections((prev) => ({ ...prev, [key]: !prev[key] }));
  }, []);

  const selectTemplate = useCallback((key) => {
    setTemplate(key);
    // Apply template presets
    if (key === "summary") {
      setSections({
        executive_summary: true,
        price_analysis: false,
        fundamentals: false,
        technical_indicators: false,
        news_sentiment: false,
        risk_assessment: true,
        recommendation: true,
      });
    } else if (key === "technical") {
      setSections({
        executive_summary: true,
        price_analysis: true,
        fundamentals: false,
        technical_indicators: true,
        news_sentiment: false,
        risk_assessment: true,
        recommendation: true,
      });
    } else if (key === "fundamental") {
      setSections({
        executive_summary: true,
        price_analysis: false,
        fundamentals: true,
        technical_indicators: false,
        news_sentiment: true,
        risk_assessment: true,
        recommendation: true,
      });
    } else {
      // detailed - all sections
      setSections(
        Object.fromEntries(
          REPORT_SECTIONS.map(({ key }) => [key, true])
        )
      );
    }
  }, []);

  const generateReport = useCallback(async () => {
    if (!ticker) return;

    setLoading(true);
    setError("");
    setReportText("");

    connRef.current?.close?.();

    try {
      const selectedSections = Object.entries(sections)
        .filter(([, v]) => v)
        .map(([k]) => k);

      const { close, done } = ssePost(
        `/api/market/report/stream?test=false&web=true`,
        {
          ticker,
          market,
          days: 90,
          newsLimit: 20,
          sections: selectedSections,
          template,
        },
        {
          onChunk: (chunk) => {
            setReportText((prev) => prev + chunk);
          },
          onDone: () => {
            setLoading(false);
          },
          onError: (err) => {
            setError(err.message || t("report.failedToGenerate"));
            setLoading(false);
          },
        }
      );

      connRef.current = { close };
      await done;

      if (onReportGenerated) {
        onReportGenerated(reportText);
      }
    } catch (e) {
      setError(e.message || t("report.failedToGenerate"));
      setLoading(false);
    }
  }, [ticker, market, sections, template, onReportGenerated, reportText, t]);

  const exportToPDF = useCallback(async () => {
    if (!ticker) return;

    try {
      await exportReportPdf({
        ticker,
        market,
        days: 90,
        newsLimit: 20,
      });
    } catch (e) {
      console.error("Failed to export PDF:", e);
      alert(t("report.exportFailed"));
    }
  }, [ticker, market, t]);

  const exportToMarkdown = useCallback(() => {
    if (!reportText) return;

    const header = `# ${ticker} (${market}) ${t("report.analysisReport")}\n\n_${t("report.generatedOn")} ${new Date().toLocaleString()}_\n\n---\n\n`;

    const content = header + reportText;
    const blob = new Blob([content], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${ticker}_report_${new Date().toISOString().slice(0, 10)}.md`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }, [ticker, market, reportText, t]);

  const copyToClipboard = useCallback(async () => {
    if (!reportText) return;

    try {
      await navigator.clipboard.writeText(reportText);
      alert(t("report.copiedToClipboard"));
    } catch (e) {
      console.error("Failed to copy:", e);
    }
  }, [reportText, t]);

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h3 className={styles.title}>{t("report.title")}</h3>
        <button
          className={styles.optionsBtn}
          onClick={() => setShowOptions(!showOptions)}
        >
          {showOptions ? t("report.hideOptions") : t("report.showOptions")}
        </button>
      </div>

      {showOptions && (
        <div className={styles.options}>
          <div className={styles.templates}>
            <h4 className={styles.sectionTitle}>{t("report.template")}</h4>
            <div className={styles.templateGrid}>
              {REPORT_TEMPLATES.map(({ key, labelKey, descriptionKey }) => (
                <button
                  key={key}
                  className={`${styles.templateBtn} ${template === key ? styles.templateActive : ""}`}
                  onClick={() => selectTemplate(key)}
                >
                  <span className={styles.templateLabel}>{t(labelKey)}</span>
                  <span className={styles.templateDesc}>{t(descriptionKey)}</span>
                </button>
              ))}
            </div>
          </div>

          <div className={styles.sections}>
            <h4 className={styles.sectionTitle}>{t("report.sections")}</h4>
            <div className={styles.sectionGrid}>
              {REPORT_SECTIONS.map(({ key, translationKey }) => (
                <label key={key} className={styles.checkboxLabel}>
                  <input
                    type="checkbox"
                    checked={sections[key]}
                    onChange={() => toggleSection(key)}
                  />
                  {t(translationKey)}
                </label>
              ))}
            </div>
          </div>
        </div>
      )}

      <div className={styles.actions}>
        <button
          className={styles.generateBtn}
          onClick={generateReport}
          disabled={loading || !ticker}
        >
          {loading ? t("report.generating") : t("report.generate")}
        </button>

        {reportText && (
          <div className={styles.exportBtns}>
            <button className={styles.exportBtn} onClick={exportToPDF}>
              {t("report.exportPDF")}
            </button>
            <button className={styles.exportBtn} onClick={exportToMarkdown}>
              {t("report.exportMD")}
            </button>
            <button className={styles.exportBtn} onClick={copyToClipboard}>
              {t("report.copy")}
            </button>
          </div>
        )}
      </div>

      {error && <div className={styles.error}>{error}</div>}

      {reportText && (
        <div ref={reportRef} className={styles.report}>
          <div className={styles.reportContent}>
            {reportText.split("\n").map((line, i) => {
              if (line.startsWith("# ")) {
                return <h1 key={i}>{line.slice(2)}</h1>;
              }
              if (line.startsWith("## ")) {
                return <h2 key={i}>{line.slice(3)}</h2>;
              }
              if (line.startsWith("### ")) {
                return <h3 key={i}>{line.slice(4)}</h3>;
              }
              if (line.startsWith("- ")) {
                return <li key={i}>{line.slice(2)}</li>;
              }
              if (line.startsWith("**") && line.endsWith("**")) {
                return <p key={i}><strong>{line.slice(2, -2)}</strong></p>;
              }
              if (line.trim() === "") {
                return <br key={i} />;
              }
              return <p key={i}>{line}</p>;
            })}
          </div>
        </div>
      )}

      {loading && (
        <div className={styles.loadingOverlay}>
          <div className={styles.spinner}></div>
          <p>{t("report.generatingComprehensive")}</p>
          <p className={styles.loadingHint}>{t("report.mayTakeTime")}</p>
        </div>
      )}
    </div>
  );
}
