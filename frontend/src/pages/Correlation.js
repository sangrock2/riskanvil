import { useState } from "react";
import { analyzeCorrelation } from "../api/analysis";
import { useToast } from "../components/ui/Toast";
import styles from "../css/Correlation.module.css";

export default function Correlation() {
  const [tickers, setTickers] = useState(["AAPL", "MSFT", "GOOGL"]);
  const [tickerInput, setTickerInput] = useState("");
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [market, setMarket] = useState("US");
  const [days, setDays] = useState(90);
  const toast = useToast();

  const addTicker = () => {
    const trimmed = tickerInput.trim().toUpperCase();
    if (!trimmed) {
      toast.warning("Please enter a ticker");
      return;
    }
    if (tickers.includes(trimmed)) {
      toast.warning("Ticker already added");
      return;
    }
    if (tickers.length >= 10) {
      toast.warning("Maximum 10 tickers allowed");
      return;
    }
    setTickers([...tickers, trimmed]);
    setTickerInput("");
  };

  const removeTicker = (ticker) => {
    setTickers(tickers.filter(t => t !== ticker));
  };

  const analyze = async () => {
    if (tickers.length < 2) {
      toast.warning("Need at least 2 tickers");
      return;
    }

    setLoading(true);
    try {
      const data = await analyzeCorrelation(tickers, market, days);
      setResult(data);
      toast.success("Analysis complete");
    } catch (e) {
      toast.error(e.message || "Analysis failed");
    } finally {
      setLoading(false);
    }
  };

  const getCorrelationColor = (value) => {
    if (value >= 0.7) return styles.corrHigh;
    if (value >= 0.3) return styles.corrMedium;
    if (value >= -0.3) return styles.corrLow;
    return styles.corrNegative;
  };

  return (
    <div className={styles.container}>
      <h1>Correlation Analysis</h1>

      <div className={styles.controls}>
        <div className={styles.tickerInput}>
          <input
            type="text"
            placeholder="Enter ticker (e.g., AAPL)"
            value={tickerInput}
            onChange={(e) => setTickerInput(e.target.value)}
            onKeyPress={(e) => e.key === "Enter" && addTicker()}
            className={styles.input}
          />
          <button onClick={addTicker} className={styles.btnAdd}>Add</button>
        </div>

        <div className={styles.settings}>
          <select value={market} onChange={(e) => setMarket(e.target.value)} className={styles.select}>
            <option value="US">US Market</option>
            <option value="KR">KR Market</option>
          </select>

          <select value={days} onChange={(e) => setDays(parseInt(e.target.value))} className={styles.select}>
            <option value="30">30 Days</option>
            <option value="90">90 Days</option>
            <option value="180">180 Days</option>
            <option value="365">1 Year</option>
          </select>
        </div>
      </div>

      <div className={styles.tickerList}>
        {tickers.map(t => (
          <span key={t} className={styles.tickerChip}>
            {t}
            <button onClick={() => removeTicker(t)} className={styles.removeBtn}>×</button>
          </span>
        ))}
      </div>

      <button
        className={styles.analyzeBtn}
        onClick={analyze}
        disabled={loading || tickers.length < 2}
      >
        {loading ? "Analyzing..." : "Analyze Correlation"}
      </button>

      {result && (
        <div className={styles.results}>
          {/* Correlation Matrix */}
          <div className={styles.section}>
            <h2>Correlation Matrix</h2>
            <div className={styles.matrix}>
              <table className={styles.matrixTable}>
                <thead>
                  <tr>
                    <th></th>
                    {result.tickers.map(t => (
                      <th key={t}>{t}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {result.tickers.map((t1, i) => (
                    <tr key={t1}>
                      <th>{t1}</th>
                      {result.tickers.map((t2, j) => {
                        const value = result.correlationMatrix[i][j];
                        return (
                          <td key={t2} className={getCorrelationColor(value)}>
                            {value.toFixed(3)}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className={styles.legend}>
              <span><span className={styles.corrHigh}></span> Strong (≥0.7)</span>
              <span><span className={styles.corrMedium}></span> Moderate (0.3-0.7)</span>
              <span><span className={styles.corrLow}></span> Weak (-0.3-0.3)</span>
              <span><span className={styles.corrNegative}></span> Negative (&lt;-0.3)</span>
            </div>
          </div>

          {/* Statistics */}
          <div className={styles.section}>
            <h2>Stock Statistics</h2>
            <table className={styles.statsTable}>
              <thead>
                <tr>
                  <th>Ticker</th>
                  <th>Mean Return</th>
                  <th>Std Deviation</th>
                  <th>Sharpe Ratio</th>
                  <th>Beta</th>
                </tr>
              </thead>
              <tbody>
                {Object.values(result.stats).map(stat => (
                  <tr key={stat.ticker}>
                    <td><strong>{stat.ticker}</strong></td>
                    <td>{(stat.mean * 100).toFixed(4)}%</td>
                    <td>{(stat.stdDev * 100).toFixed(4)}%</td>
                    <td>{stat.sharpe.toFixed(3)}</td>
                    <td>{stat.beta.toFixed(3)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
