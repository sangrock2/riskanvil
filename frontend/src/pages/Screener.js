import { useState } from "react";
import { screenStocks, saveScreenerPreset, getScreenerPresets } from "../api/screener";
import { useToast } from "../components/ui/Toast";
import { useTranslation } from "../hooks/useTranslation";
import styles from "../css/Screener.module.css";

export default function Screener() {
  const { t } = useTranslation();
  const [filters, setFilters] = useState({
    peMin: "",
    peMax: "",
    psMin: "",
    psMax: "",
    pbMin: "",
    pbMax: "",
    roeMin: "",
    roeMax: "",
    revenueGrowthMin: "",
    dividendYieldMin: "",
    marketCapMin: "",
    marketCapMax: "",
    sector: "",
    rsiMin: "",
    rsiMax: ""
  });

  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [market, setMarket] = useState("US");
  const [sortBy, setSortBy] = useState("pe");
  const [sortOrder, setSortOrder] = useState("asc");
  const [limit, setLimit] = useState(50);
  const toast = useToast();

  const handleScreen = async () => {
    setLoading(true);
    try {
      const cleanFilters = {};
      Object.keys(filters).forEach(key => {
        if (filters[key] !== "" && filters[key] !== null) {
          cleanFilters[key] = parseFloat(filters[key]) || filters[key];
        }
      });

      cleanFilters.limit = limit;

      const data = await screenStocks(cleanFilters, market, sortBy, sortOrder);
      setResults(data);
      toast.success(t('screener.foundStocks', { count: data.length }));
    } catch (e) {
      toast.error(e.message || t('screener.noResults'));
    } finally {
      setLoading(false);
    }
  };

  const handleTickerClick = (ticker) => {
    window.open(`/insight-detail?ticker=${encodeURIComponent(ticker)}&market=${encodeURIComponent(market)}`, "_blank");
  };

  const handleReset = () => {
    setFilters({
      peMin: "",
      peMax: "",
      psMin: "",
      psMax: "",
      pbMin: "",
      pbMax: "",
      roeMin: "",
      roeMax: "",
      revenueGrowthMin: "",
      dividendYieldMin: "",
      marketCapMin: "",
      marketCapMax: "",
      sector: "",
      rsiMin: "",
      rsiMax: ""
    });
    setResults([]);
  };

  return (
    <div className={styles.container}>
      <h1>{t('screener.title')}</h1>

      {/* Filters */}
      <div className={styles.filters}>
        <div className={styles.filterSection}>
          <h3>{t('screener.marketSection')}</h3>
          <select value={market} onChange={(e) => setMarket(e.target.value)} className={styles.select}>
            <option value="US">{t('screener.usMarket')}</option>
            <option value="KR">{t('screener.krMarket')}</option>
          </select>

          <div className={styles.filterRow} style={{ marginTop: "1rem" }}>
            <label>{t('screener.resultLimit')}</label>
            <input
              type="number"
              min="10"
              max="500"
              step="10"
              value={limit}
              onChange={(e) => setLimit(parseInt(e.target.value) || 50)}
              className={styles.input}
              placeholder="50"
            />
          </div>
        </div>

        <div className={styles.filterSection}>
          <h3>{t('screener.valuationSection')}</h3>
          <div className={styles.filterRow}>
            <label>{t('screener.pe')}</label>
            <input
              type="number"
              step="0.1"
              placeholder="Min"
              value={filters.peMin}
              onChange={(e) => setFilters({...filters, peMin: e.target.value})}
              className={styles.input}
            />
            <input
              type="number"
              step="0.1"
              placeholder="Max"
              value={filters.peMax}
              onChange={(e) => setFilters({...filters, peMax: e.target.value})}
              className={styles.input}
            />
          </div>

          <div className={styles.filterRow}>
            <label>{t('screener.ps')}</label>
            <input
              type="number"
              step="0.1"
              placeholder="Min"
              value={filters.psMin}
              onChange={(e) => setFilters({...filters, psMin: e.target.value})}
              className={styles.input}
            />
            <input
              type="number"
              step="0.1"
              placeholder="Max"
              value={filters.psMax}
              onChange={(e) => setFilters({...filters, psMax: e.target.value})}
              className={styles.input}
            />
          </div>

          <div className={styles.filterRow}>
            <label>{t('screener.pb')}</label>
            <input
              type="number"
              step="0.1"
              placeholder="Min"
              value={filters.pbMin}
              onChange={(e) => setFilters({...filters, pbMin: e.target.value})}
              className={styles.input}
            />
            <input
              type="number"
              step="0.1"
              placeholder="Max"
              value={filters.pbMax}
              onChange={(e) => setFilters({...filters, pbMax: e.target.value})}
              className={styles.input}
            />
          </div>
        </div>

        <div className={styles.filterSection}>
          <h3>{t('screener.profitabilitySection')}</h3>
          <div className={styles.filterRow}>
            <label>{t('screener.roe')}</label>
            <input
              type="number"
              step="0.01"
              placeholder="Min"
              value={filters.roeMin}
              onChange={(e) => setFilters({...filters, roeMin: e.target.value})}
              className={styles.input}
            />
            <input
              type="number"
              step="0.01"
              placeholder="Max"
              value={filters.roeMax}
              onChange={(e) => setFilters({...filters, roeMax: e.target.value})}
              className={styles.input}
            />
          </div>

          <div className={styles.filterRow}>
            <label>{t('screener.revenueGrowth')}</label>
            <input
              type="number"
              step="0.01"
              placeholder="Min"
              value={filters.revenueGrowthMin}
              onChange={(e) => setFilters({...filters, revenueGrowthMin: e.target.value})}
              className={styles.input}
            />
          </div>
        </div>

        <div className={styles.filterSection}>
          <h3>{t('screener.otherSection')}</h3>
          <div className={styles.filterRow}>
            <label>{t('screener.dividendYield')}</label>
            <input
              type="number"
              step="0.01"
              placeholder="Min"
              value={filters.dividendYieldMin}
              onChange={(e) => setFilters({...filters, dividendYieldMin: e.target.value})}
              className={styles.input}
            />
          </div>

          <div className={styles.filterRow}>
            <label>{t('screener.marketCap')}</label>
            <input
              type="number"
              placeholder="Min"
              value={filters.marketCapMin}
              onChange={(e) => setFilters({...filters, marketCapMin: e.target.value})}
              className={styles.input}
            />
            <input
              type="number"
              placeholder="Max"
              value={filters.marketCapMax}
              onChange={(e) => setFilters({...filters, marketCapMax: e.target.value})}
              className={styles.input}
            />
          </div>
        </div>

        <div className={styles.actions}>
          <button className={styles.btnPrimary} onClick={handleScreen} disabled={loading}>
            {loading ? t('screener.screening') : t('screener.screenStocks')}
          </button>
          <button className={styles.btnSecondary} onClick={handleReset}>
            {t('screener.resetFilters')}
          </button>
        </div>
      </div>

      {/* Results */}
      {results.length > 0 && (
        <div className={styles.results}>
          <div className={styles.resultsHeader}>
            <h2>{t('screener.resultsHeader', { count: results.length })}</h2>
            <div className={styles.sortControls}>
              <label>{t('screener.sortBy')}:</label>
              <select value={sortBy} onChange={(e) => setSortBy(e.target.value)} className={styles.select}>
                <option value="pe">{t('screener.pe')}</option>
                <option value="ps">{t('screener.ps')}</option>
                <option value="pb">{t('screener.pb')}</option>
                <option value="roe">{t('screener.roeLabel')}</option>
                <option value="price">{t('screener.price')}</option>
                <option value="marketCap">{t('screener.marketCap')}</option>
                <option value="overallScore">{t('screener.score')}</option>
              </select>
              <select value={sortOrder} onChange={(e) => setSortOrder(e.target.value)} className={styles.select}>
                <option value="asc">{t('watchlist.ascending')}</option>
                <option value="desc">{t('watchlist.descending')}</option>
              </select>
            </div>
          </div>

          <table className={styles.table}>
            <thead>
              <tr>
                <th>{t('analyze.ticker')}</th>
                <th>{t('screener.name')}</th>
                <th>{t('screener.sector')}</th>
                <th>{t('screener.price')}</th>
                <th>{t('screener.peShort')}</th>
                <th>{t('screener.psShort')}</th>
                <th>{t('screener.pbShort')}</th>
                <th>{t('screener.roeLabel')}</th>
                <th>{t('screener.revGrowth')}</th>
                <th>{t('screener.divYield')}</th>
                <th>{t('screener.marketCap')}</th>
                <th>{t('screener.score')}</th>
              </tr>
            </thead>
            <tbody>
              {results.map(stock => (
                <tr key={stock.ticker} className={styles.clickableRow} onClick={() => handleTickerClick(stock.ticker)}>
                  <td><strong style={{ color: "#4a9eff", cursor: "pointer" }}>{stock.ticker}</strong></td>
                  <td>{stock.name || "-"}</td>
                  <td>{stock.sector || "-"}</td>
                  <td>${stock.price?.toFixed(2) || "-"}</td>
                  <td>{stock.pe?.toFixed(2) || "-"}</td>
                  <td>{stock.ps?.toFixed(2) || "-"}</td>
                  <td>{stock.pb?.toFixed(2) || "-"}</td>
                  <td>{stock.roe ? (stock.roe * 100).toFixed(2) + "%" : "-"}</td>
                  <td>{stock.revenueGrowth ? (stock.revenueGrowth * 100).toFixed(2) + "%" : "-"}</td>
                  <td>{stock.dividendYield ? (stock.dividendYield * 100).toFixed(2) + "%" : "-"}</td>
                  <td>{stock.marketCap ? `$${(stock.marketCap / 1e9).toFixed(2)}B` : "-"}</td>
                  <td>
                    <span className={stock.overallScore >= 70 ? styles.scoreHigh : stock.overallScore >= 50 ? styles.scoreMid : styles.scoreLow}>
                      {stock.overallScore || "-"}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
