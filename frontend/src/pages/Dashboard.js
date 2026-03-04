import { useState, useMemo, useCallback, memo } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../api/http";
import { getRecentTickers } from "../api/userLists";
import { STALE_TIMES } from "../api/queryClient";
import { SkeletonTable, SkeletonCard } from "../components/ui/Loading";
import { useRealtimeQuote } from "../hooks/useRealtimeQuote";
import { useTranslation } from "../hooks/useTranslation";
import styles from "../css/Dashboard.module.css";

const WIDGET_TYPES = {
  WATCHLIST: "watchlist",
  RECENT: "recent",
  MARKET_OVERVIEW: "market_overview",
  QUICK_ANALYSIS: "quick_analysis",
  TOP_MOVERS: "top_movers",
  NEWS_SUMMARY: "news_summary",
};

const DEFAULT_WIDGETS = [
  { id: "w1", type: WIDGET_TYPES.QUICK_ANALYSIS, visible: true, order: 0 },
  { id: "w2", type: WIDGET_TYPES.WATCHLIST, visible: true, order: 1 },
  { id: "w3", type: WIDGET_TYPES.RECENT, visible: true, order: 2 },
  { id: "w4", type: WIDGET_TYPES.MARKET_OVERVIEW, visible: true, order: 3 },
  { id: "w5", type: WIDGET_TYPES.TOP_MOVERS, visible: true, order: 4 },
  { id: "w6", type: WIDGET_TYPES.NEWS_SUMMARY, visible: false, order: 5 },
];

// Widget labels are now translated dynamically in the Widget component

function loadDashboardConfig() {
  try {
    const saved = localStorage.getItem("dashboard_config");
    if (saved) {
      const parsed = JSON.parse(saved);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed;
      }
    }
  } catch (e) {
    console.error("Failed to load dashboard config:", e);
  }
  return DEFAULT_WIDGETS;
}

function saveDashboardConfig(config) {
  try {
    localStorage.setItem("dashboard_config", JSON.stringify(config));
  } catch (e) {
    console.error("Failed to save dashboard config:", e);
  }
}

// Widget Components
const QuickAnalysisWidget = memo(function QuickAnalysisWidget() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [ticker, setTicker] = useState("");
  const [market, setMarket] = useState("US");

  const handleAnalyze = () => {
    if (ticker.trim()) {
      navigate(`/insight-detail?ticker=${encodeURIComponent(ticker.trim())}&market=${market}`);
    }
  };

  return (
    <div className={styles.widgetContent}>
      <div className={styles.quickForm}>
        <input
          type="text"
          placeholder={t("dashboard.enterTicker")}
          value={ticker}
          onChange={(e) => setTicker(e.target.value.toUpperCase())}
          onKeyDown={(e) => e.key === "Enter" && handleAnalyze()}
          className={styles.input}
        />
        <select
          value={market}
          onChange={(e) => setMarket(e.target.value)}
          className={styles.select}
        >
          <option value="US">{t("common.market.us")} (US)</option>
          <option value="KR">{t("common.market.kr")} (KR)</option>
        </select>
        <button onClick={handleAnalyze} className={styles.btnPrimary}>
          {t("dashboard.analyze")}
        </button>
      </div>
      <div className={styles.quickLinks}>
        <span className={styles.quickLabel}>{t("dashboard.popular")}</span>
        {["AAPL", "MSFT", "GOOGL", "AMZN", "NVDA"].map((t) => (
          <Link key={t} to={`/insight-detail?ticker=${t}&market=US`} className={styles.quickLink}>
            {t}
          </Link>
        ))}
      </div>
    </div>
  );
});

const WatchlistWidget = memo(function WatchlistWidget() {
  const { t } = useTranslation();
  const { data: rawItems, isLoading: loading } = useQuery({
    queryKey: ["watchlist"],
    queryFn: () => apiFetch("/api/watchlist"),
    staleTime: STALE_TIMES.WATCHLIST,
    select: (data) => (Array.isArray(data) ? data.slice(0, 5) : []),
  });
  const items = rawItems || [];

  if (loading) return <SkeletonTable rows={5} columns={3} />;

  if (items.length === 0) {
    return (
      <div className={styles.empty}>
        {t("dashboard.noItemsInWatchlist")}{" "}
        <Link to="/watchlist">{t("dashboard.addSome")}</Link>
      </div>
    );
  }

  return (
    <div className={styles.widgetContent}>
      <ul className={styles.list}>
        {items.map((item) => (
          <li key={item.id} className={styles.listItem}>
            <Link to={`/insight-detail?ticker=${item.ticker}&market=${item.market}`}>
              <span className={styles.ticker}>{item.ticker}</span>
              <span className={styles.market}>{item.market}</span>
              {item.summary?.score && (
                <span className={`${styles.score} ${item.summary.score >= 60 ? styles.scoreGood : item.summary.score >= 40 ? styles.scoreNeutral : styles.scoreBad}`}>
                  {item.summary.score}
                </span>
              )}
            </Link>
          </li>
        ))}
      </ul>
      <Link to="/watchlist" className={styles.viewAll}>{t("dashboard.viewAll")}</Link>
    </div>
  );
});

const RecentWidget = memo(function RecentWidget() {
  const { t } = useTranslation();
  const recent = useMemo(() => getRecentTickers().slice(0, 5), []);

  if (recent.length === 0) {
    return <div className={styles.empty}>{t("dashboard.noRecentAnalysis")}</div>;
  }

  return (
    <div className={styles.widgetContent}>
      <ul className={styles.list}>
        {recent.map((ticker, i) => (
          <li key={i} className={styles.listItem}>
            <Link to={`/insight-detail?ticker=${ticker}&market=US`}>
              <span className={styles.ticker}>{ticker}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
});

const IndexCard = memo(function IndexCard({ ticker, initialData }) {
  const { t } = useTranslation();
  const realtime = useRealtimeQuote(ticker);
  const price = realtime?.price ?? initialData?.price;
  const changePercent = initialData?.changePercent;

  return (
    <div className={styles.indexCard}>
      <div className={styles.indexName}>{ticker}</div>
      <div className={styles.indexPrice}>
        ${price != null ? Number(price).toFixed(2) : t("messages.noData")}
        {realtime && <span className={styles.liveIndicator} title={t("dashboard.live")}> *</span>}
      </div>
      <div
        className={`${styles.indexChange} ${
          parseFloat(changePercent) >= 0 ? styles.positive : styles.negative
        }`}
      >
        {changePercent || t("messages.noData")}
      </div>
    </div>
  );
});

const MarketOverviewWidget = memo(function MarketOverviewWidget() {
  const indices = ["SPY", "QQQ", "DIA"];
  const { data, isLoading: loading } = useQuery({
    queryKey: ["market-overview"],
    queryFn: async () => {
      const quotes = await Promise.all(
        indices.map((t) =>
          apiFetch(`/api/market/quote?ticker=${t}&market=US`).catch(() => null)
        )
      );
      return indices.map((t, i) => ({ ticker: t, ...quotes[i] }));
    },
    staleTime: STALE_TIMES.QUOTE,
  });

  if (loading) {
    return (
      <div className={styles.widgetContent}>
        <SkeletonCard variant="default" count={3} />
      </div>
    );
  }

  return (
    <div className={styles.widgetContent}>
      <div className={styles.indicesGrid}>
        {data?.map((item) => (
          <IndexCard key={item.ticker} ticker={item.ticker} initialData={item} />
        ))}
      </div>
    </div>
  );
});

const TopMoversWidget = memo(function TopMoversWidget() {
  const movers = [
    { ticker: "NVDA", change: "+5.2%", direction: "up" },
    { ticker: "TSLA", change: "+3.8%", direction: "up" },
    { ticker: "AMD", change: "-2.1%", direction: "down" },
    { ticker: "META", change: "+1.9%", direction: "up" },
  ];

  return (
    <div className={styles.widgetContent}>
      <ul className={styles.moversList}>
        {movers.map((m) => (
          <li key={m.ticker} className={styles.moverItem}>
            <Link to={`/insight-detail?ticker=${m.ticker}&market=US`}>
              <span className={styles.ticker}>{m.ticker}</span>
              <span className={m.direction === "up" ? styles.positive : styles.negative}>
                {m.change}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
});

const NewsSummaryWidget = memo(function NewsSummaryWidget() {
  const { t } = useTranslation();
  return (
    <div className={styles.widgetContent}>
      <div className={styles.newsPlaceholder}>
        {t("dashboard.newsComingSoon")}
        <br />
        <Link to="/analyze">{t("dashboard.goToAnalysis")}</Link>
      </div>
    </div>
  );
});

function Widget({ widget, onRemove }) {
  const { t } = useTranslation();

  const getWidgetLabel = (type) => {
    const labelMap = {
      [WIDGET_TYPES.QUICK_ANALYSIS]: t("dashboard.quickAnalysis"),
      [WIDGET_TYPES.WATCHLIST]: t("dashboard.watchlist"),
      [WIDGET_TYPES.RECENT]: t("dashboard.recentAnalysis"),
      [WIDGET_TYPES.MARKET_OVERVIEW]: t("dashboard.marketOverview"),
      [WIDGET_TYPES.TOP_MOVERS]: t("dashboard.topMovers"),
      [WIDGET_TYPES.NEWS_SUMMARY]: t("dashboard.newsSummary"),
    };
    return labelMap[type] || type;
  };

  const renderContent = () => {
    switch (widget.type) {
      case WIDGET_TYPES.QUICK_ANALYSIS:
        return <QuickAnalysisWidget />;
      case WIDGET_TYPES.WATCHLIST:
        return <WatchlistWidget />;
      case WIDGET_TYPES.RECENT:
        return <RecentWidget />;
      case WIDGET_TYPES.MARKET_OVERVIEW:
        return <MarketOverviewWidget />;
      case WIDGET_TYPES.TOP_MOVERS:
        return <TopMoversWidget />;
      case WIDGET_TYPES.NEWS_SUMMARY:
        return <NewsSummaryWidget />;
      default:
        return null;
    }
  };

  return (
    <div className={styles.widget}>
      <div className={styles.widgetHeader}>
        <h3 className={styles.widgetTitle}>{getWidgetLabel(widget.type)}</h3>
        <button
          className={styles.removeBtn}
          onClick={() => onRemove(widget.id)}
          title={t("dashboard.hideWidget")}
        >
          ×
        </button>
      </div>
      {renderContent()}
    </div>
  );
}

export default function Dashboard() {
  const { t } = useTranslation();
  const [widgets, setWidgets] = useState(() => loadDashboardConfig());
  const [showSettings, setShowSettings] = useState(false);

  const getWidgetLabel = useCallback((type) => {
    const labelMap = {
      [WIDGET_TYPES.QUICK_ANALYSIS]: t("dashboard.quickAnalysis"),
      [WIDGET_TYPES.WATCHLIST]: t("dashboard.watchlist"),
      [WIDGET_TYPES.RECENT]: t("dashboard.recentAnalysis"),
      [WIDGET_TYPES.MARKET_OVERVIEW]: t("dashboard.marketOverview"),
      [WIDGET_TYPES.TOP_MOVERS]: t("dashboard.topMovers"),
      [WIDGET_TYPES.NEWS_SUMMARY]: t("dashboard.newsSummary"),
    };
    return labelMap[type] || type;
  }, [t]);

  const visibleWidgets = useMemo(
    () => widgets.filter((w) => w.visible).sort((a, b) => a.order - b.order),
    [widgets]
  );

  const toggleWidget = useCallback((id) => {
    setWidgets((prev) => {
      const updated = prev.map((w) =>
        w.id === id ? { ...w, visible: !w.visible } : w
      );
      saveDashboardConfig(updated);
      return updated;
    });
  }, []);

  const removeWidget = useCallback((id) => {
    setWidgets((prev) => {
      const updated = prev.map((w) =>
        w.id === id ? { ...w, visible: false } : w
      );
      saveDashboardConfig(updated);
      return updated;
    });
  }, []);

  const moveWidget = useCallback((id, direction) => {
    setWidgets((prev) => {
      const visible = prev.filter((w) => w.visible).sort((a, b) => a.order - b.order);
      const idx = visible.findIndex((w) => w.id === id);
      if (idx === -1) return prev;

      const newIdx = direction === "up" ? idx - 1 : idx + 1;
      if (newIdx < 0 || newIdx >= visible.length) return prev;

      // Swap orders
      const updated = prev.map((w) => {
        if (w.id === visible[idx].id) return { ...w, order: visible[newIdx].order };
        if (w.id === visible[newIdx].id) return { ...w, order: visible[idx].order };
        return w;
      });

      saveDashboardConfig(updated);
      return updated;
    });
  }, []);

  const resetToDefault = useCallback(() => {
    setWidgets(DEFAULT_WIDGETS);
    saveDashboardConfig(DEFAULT_WIDGETS);
    setShowSettings(false);
  }, []);

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>{t("dashboard.title")}</h1>
        <button
          className={styles.settingsBtn}
          onClick={() => setShowSettings(!showSettings)}
        >
          {showSettings ? t("dashboard.closeSettings") : t("dashboard.customize")}
        </button>
      </div>

      {showSettings && (
        <div className={styles.settingsPanel}>
          <h3 className={styles.settingsTitle}>{t("dashboard.settings")}</h3>
          <p className={styles.settingsDesc}>
            {t("dashboard.settingsDesc")}
          </p>
          <div className={styles.widgetList}>
            {widgets
              .sort((a, b) => a.order - b.order)
              .map((w, idx) => (
                <div key={w.id} className={styles.widgetSetting}>
                  <label className={styles.checkboxLabel}>
                    <input
                      type="checkbox"
                      checked={w.visible}
                      onChange={() => toggleWidget(w.id)}
                    />
                    {getWidgetLabel(w.type)}
                  </label>
                  {w.visible && (
                    <div className={styles.orderBtns}>
                      <button
                        onClick={() => moveWidget(w.id, "up")}
                        disabled={idx === 0}
                        className={styles.orderBtn}
                      >
                        ↑
                      </button>
                      <button
                        onClick={() => moveWidget(w.id, "down")}
                        disabled={idx === widgets.length - 1}
                        className={styles.orderBtn}
                      >
                        ↓
                      </button>
                    </div>
                  )}
                </div>
              ))}
          </div>
          <button onClick={resetToDefault} className={styles.resetBtn}>
            {t("dashboard.resetToDefault")}
          </button>
        </div>
      )}

      <div className={styles.widgetsGrid}>
        {visibleWidgets.map((widget) => (
          <Widget key={widget.id} widget={widget} onRemove={removeWidget} />
        ))}
      </div>

      {visibleWidgets.length === 0 && (
        <div className={styles.emptyDashboard}>
          <p>{t("dashboard.noWidgetsVisible")}</p>
        </div>
      )}
    </div>
  );
}
