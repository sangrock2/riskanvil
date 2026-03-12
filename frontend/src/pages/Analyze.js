import { useEffect, useState, useRef, useMemo, useCallback } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { apiFetch } from "../api/http";
import { pushRecentTicker, getRecentTickers, getFavorites, toggleFavorite, isFavorite } from "../api/userLists";
import LineChartCanvas from "../components/LineChartCanvas";
import { useTranslation } from "../hooks/useTranslation";
import { toUserErrorMessage } from "../utils/errorMessage";
import styles from "../css/Analyze.module.css";

/** price stats for "풀버전 detail" */
function calcPriceStats(points) {
    if (!Array.isArray(points) || points.length < 2) return null;

    const clean = points.map((p) => ({
        date: p?.date,
        close: Number(p?.close),
        })
    ).filter((p) => p.date && Number.isFinite(p.close));

    if (clean.length < 2) return null;

    const first = clean[0];
    const last = clean[clean.length - 1];

    const closes = clean.map((p) => p.close);
    const min = Math.min(...closes);
    const max = Math.max(...closes);

    const totalReturn = (last.close - first.close) / first.close;

    // max drawdown (close 기준, 기간 내)
    let peak = closes[0];
    let mdd = 0;

    for (const c of closes) {
        if (c > peak) peak = c;
        const dd = c / peak - 1;
        if (dd < mdd) mdd = dd;
    }

    // annualized vol (단순: 일간 수익률 표준편차 * sqrt(252))
    const rets = [];
    for (let i = 1; i < closes.length; i++) {
        const prev = closes[i - 1];
        if (prev > 0) rets.push(closes[i] / prev - 1);
    }

    let volAnn = null;
    if (rets.length >= 10) {
        const mean = rets.reduce((a, b) => a + b, 0) / rets.length;
        const var0 = rets.reduce((a, r) => a + (r - mean) ** 2, 0) / (rets.length - 1);
        const sd = Math.sqrt(var0);
        volAnn = sd * Math.sqrt(252);
    }

    return {
        startDate: first.date,
        endDate: last.date,
        startClose: first.close,
        endClose: last.close,
        totalReturn,
        maxDrawdown: mdd,
        volAnn,
        min,
        max,
        n: clean.length,
    };
}

function pct(x) {
  if (x === null || x === undefined || Number.isNaN(Number(x))) return "N/A";
  return `${(Number(x) * 100).toFixed(1)}%`;
}

function num(x, d = 2) {
  if (x === null || x === undefined || Number.isNaN(Number(x))) return "N/A";
  return Number(x).toFixed(d);
}

function toDisplayText(value, fallback = "") {
  if (value === null || value === undefined) return fallback;

  if (typeof value === "string") {
    const trimmed = value.trim();
    return trimmed || fallback;
  }

  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }

  if (Array.isArray(value)) {
    const joined = value.map((v) => toDisplayText(v, "")).filter(Boolean).join(", ");
    return joined || fallback;
  }

  if (typeof value === "object") {
    const candidates = [
      value.displayName,
      value.name,
      value.title,
      value.text,
      value.label,
      value.symbol,
      value.url,
    ];

    for (const c of candidates) {
      const s = toDisplayText(c, "");
      if (s) return s;
    }

    return fallback;
  }

  return fallback;
}

function toHttpUrl(value) {
  if (typeof value !== "string") return "";
  const trimmed = value.trim();
  if (!trimmed) return "";
  if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
  return "";
}

/**
 * Stock analysis page with search, insights, and AI-powered recommendations
 *
 * @component
 * @returns {JSX.Element} Analysis page with ticker search, insights, and historical tracking
 *
 * @description
 * Main features:
 * - Ticker search with autocomplete suggestions
 * - Market selection (US/KR)
 * - Real-time insights with AI-powered analysis and recommendations
 * - Historical analysis tracking with pagination
 * - Recent tickers and favorites quick access
 * - Customizable analysis parameters (days, news limit, indicators)
 * - AI report generation with streaming support
 * - Score breakdown and risk assessment charts
 * - Test mode support for development
 *
 * URL Parameters:
 * - q: Query string for ticker or company name search
 * - test: Enable test mode (test=true)
 *
 * Features:
 * - Auto-detects ticker vs company name in query
 * - Saves recently viewed tickers to localStorage
 * - Supports favorites management
 * - Debounced search suggestions (250ms)
 * - Keyboard navigation support
 */
export default function Analyze(){
    const { t } = useTranslation();
    const location = useLocation();
    const navigate = useNavigate();
    const handledQRef = useRef("");

    const [ticker, setTicker] = useState("AAPL");
    const [market, setMarket] = useState("US");
    const [horizonDays, setHorizonDays] = useState(252);
    const [riskProfile, setRiskProfile] = useState("balanced");

    const [err, setErr] = useState("");
    const [run, setRun] = useState(null);        // { runId, result }
    const [history, setHistory] = useState([]);  // list
    const [detail, setDetail] = useState(null);  // Json

    const [keywords, setKeywords] = useState("");
    const [suggestions, setSuggestions] = useState([]);
    const [loadingSearch, setLoadingSearch] = useState(false);

    const [insights, setInsights] = useState(null);
    const [loadingInsights, setLoadingInsights] = useState(false);

    const [reportEnabled, setReportEnabled] = useState(() => {
        return localStorage.getItem("reportEnabled") === "1";
    });

    // ✅ 리포트 템플릿(14번) - Analyze에서 고르고 Detail로 전달
    const [reportTemplate, setReportTemplate] = useState(() => {
        return localStorage.getItem("reportTemplate") || "base"; // short|base|detail
    });

    // ✅ 최근/즐겨찾기 상태 (로컬스토리지 기반)
    const [recentTickers, setRecentTickers] = useState(() => getRecentTickers(12));
    const [favoriteTickers, setFavoriteTickers] = useState(() => getFavorites(24));

    const decision = run?.result?.decision;
    const rec = insights?.recommendation;
    const quote = insights?.quote;
    const fund = insights?.fundamentals;
    const news = insights?.news;
    const tech = insights?.technicals;

    const isFav = useMemo(() => isFavorite(ticker), [ticker]);

    const refreshLists = useCallback(() => {
        setRecentTickers(getRecentTickers(12));
        setFavoriteTickers(getFavorites(24));
    }, []);

    const testMode = useMemo(() => {
        return new URLSearchParams(location.search).get("test") === "true";
    }, [location.search]);

    // ✅ 전역검색 q
    const q = useMemo(() => {
        const params = new URLSearchParams(location.search);
        return (params.get("q") || "").trim();
    }, [location.search]);

    const onToggleFav = () => {
        const t = ticker.trim().toUpperCase();
        if (!t) return;
        toggleFavorite(t);
        refreshLists();
    };

    const quickPick = async (t) => {
        const sym = (t || "").trim().toUpperCase();
        if (!sym) return;
        setTicker(sym);
        await loadInsightsFor(sym, false);
    };

    async function loadHistory(page = 0) {
        const data = await apiFetch(`/api/analysis/history?page=${page}&size=20&sort=createdAt,desc`);

        // (1) 백엔드가 List로 주는 경우도 커버
        if (Array.isArray(data)) {
            setHistory(data);
            return;
        }

        // (2) PageResponse 형태
        setHistory(data.items || []);
    }

    const submit = async () => {
        setErr("");
        setRun(null);
        setDetail(null);

        try {
            const data = await apiFetch("/api/analysis", {
                method: "POST",
                body: JSON.stringify({ ticker, market, horizonDays: Number(horizonDays), riskProfile }),
            });

            setRun(data);

            await loadHistory();
        } catch (e) {
            setErr(toUserErrorMessage(e, t, "messages.errorLoadingData"));
        }
    };

    const openDetail = async (id) => {
        setErr("");

        try {
            const d = await apiFetch(`/api/analysis/${id}`);
            setDetail(d);
        } catch (e) {
            setErr(toUserErrorMessage(e, t, "messages.errorLoadingData"));
        }
    };

    function pickSuggestion(s) {
        // AlphaVantage search 결과: {symbol, name, ...}
        if (!s?.symbol) return;
        setTicker(s.symbol);
        setKeywords("");
        setSuggestions([]);
    }

    const loadInsightsFor = useCallback(
        async (tickerValue, refresh = false) => {
            const t = (tickerValue || "").trim().toUpperCase();
            if (!t) return;

            setErr("");
            setLoadingInsights(true);

            try {
                const data = await apiFetch(`/api/market/insights?test=${testMode}&refresh=${refresh}`, {
                    method: "POST",
                    body: JSON.stringify({
                        ticker: t,
                        market,
                        days: 90,
                        newsLimit: 20,
                    }),
                });

                setInsights(data);

                // ✅ 최근 본 종목 저장
                pushRecentTicker(t);
                refreshLists();
            } catch (e) {
                setErr(toUserErrorMessage(e, t, "messages.errorLoadingData"));
                setInsights(null);
            } finally {
                setLoadingInsights(false);
            }
        }, [market, testMode, refreshLists]
    );

    async function loadInsights(refresh = false) {
        await loadInsightsFor(ticker, refresh);
    }

    function openInsightDetail() {
        const t = ticker.trim();
        if (!t) return;

        pushRecentTicker(t);
        refreshLists();

        const url = `/insight-detail?ticker=${encodeURIComponent(t)}&market=${encodeURIComponent(market)}&test=${testMode}&template=${encodeURIComponent(reportTemplate)}`; // ✅ 템플릿 전달
        navigate(url);
    }

    useEffect(() => {
        if (!q) return;
        if (handledQRef.current === q) return;

        handledQRef.current = q;

        const run = async () => {
            setErr("");

            // 1) 콤마면 첫번째를 티커로(Compare 페이지가 있으면 그쪽으로 보내도 됨)
            if (q.includes(",")) {
                const first = q.split(",")[0].trim();

                if (first) {
                    const sym = first.toUpperCase();
                    setTicker(sym);
                    await loadInsightsFor(sym, false);
                }

                return;
            }

            // 2) 단일 입력: 티커로 보이면 바로
            const maybeTicker = /^[A-Za-z.-]{1,10}$/.test(q);
            if (maybeTicker) {
                const sym = q.toUpperCase();
                setTicker(sym);
                await loadInsightsFor(sym, false);
                return;
            }

            // 3) 회사명: search API로 가장 유사한 티커 1개 선택
            try {
                setLoadingSearch(true);
                const list = await apiFetch(`/api/market/search?keywords=${encodeURIComponent(q)}&market=${market}&test=${testMode}`);
                const best = Array.isArray(list) && list.length > 0 ? list[0] : null;

                if (best?.symbol) {
                    const sym = String(best.symbol).trim().toUpperCase();
                    setTicker(sym);
                    await loadInsightsFor(sym, false);
                    return;
                }

                // 아무것도 못 찾으면 검색창에만 채워주고 끝
                setKeywords(q);
            } catch (e) {
                setKeywords(q);
            } finally {
                setLoadingSearch(false);
            }
        };

        run();
    }, [q, market, testMode, loadInsightsFor]);

    useEffect(() => {
        const qq = keywords.trim();

        if (!qq) {
            setSuggestions([]);
            return;
        }

        const t = setTimeout(async () => {
            setLoadingSearch(true);

            try {
                const list = await apiFetch(`/api/market/search?keywords=${encodeURIComponent(qq)}&market=${market}&test=${testMode}`);
                setSuggestions(Array.isArray(list) ? list : []);
            } catch {
                setSuggestions([]);
            } finally {
                setLoadingSearch(false);
            }
        }, 250);

        return () => clearTimeout(t);
    }, [keywords, market, testMode]);

    useEffect(() => {
        localStorage.setItem("reportEnabled", reportEnabled ? "1" : "0");
    }, [reportEnabled]);

    useEffect(() => {
        localStorage.setItem("reportTemplate", reportTemplate);
    }, [reportTemplate]);

    return (
        <div className={styles.container}>
            <h2>
                {t('analyze.title')}{" "}
                {testMode && <span className={styles.testBadge}>({t('analyze.testMode')})</span>}
            </h2>

            {/* Quick Lists */}
            <div className={`${styles.card} ${styles.quickListsCard}`}>
                <div className={styles.quickListRow}>
                    <div className={styles.quickListLabel}>{t('analyze.favorites')}</div>
                    {favoriteTickers.length === 0 ? (
                        <span className={styles.small}>{t('analyze.noFavorites')}</span>
                    ) : (
                        favoriteTickers.map((t) => (
                        <button key={t} className={styles.chip} onClick={() => quickPick(t)}>
                            {t}
                        </button>
                        ))
                    )}
                </div>

                <div className={styles.quickListRow}>
                    <div className={styles.quickListLabel}>{t('analyze.recent')}</div>
                    {recentTickers.length === 0 ? (
                        <span className={styles.small}>{t('analyze.noRecent')}</span>
                    ) : (
                        recentTickers.map((t) => (
                        <button key={t} className={styles.chipGhost} onClick={() => quickPick(t)}>
                            {t}
                        </button>
                        ))
                    )}
                </div>
            </div>

            <div className={styles.card}>
                <div className={styles.row}>
                    {/* ticker + fav */}
                    <div className={styles.tickerInputRow}>
                        <input className={styles.input} value={ticker} onChange={(e) => setTicker(e.target.value)} placeholder={t('analyze.tickerPlaceholder')} />

                        <button className={isFav ? styles.starOn : styles.star} onClick={onToggleFav} title={isFav ? t('analyze.removeFromFavorites') : t('analyze.addToFavorites')} >
                            {isFav ? "★" : "☆"}
                        </button>
                    </div>

                    <select className={styles.input} value={market} onChange={(e) => setMarket(e.target.value)}>
                        <option value="US">US</option>
                        <option value="KR">KR</option>
                    </select>

                    <input className={styles.input} type="number" value={horizonDays} onChange={(e) => setHorizonDays(e.target.value)} />

                    <select className={styles.input} value={riskProfile} onChange={(e) => setRiskProfile(e.target.value)}>
                        <option value="balanced">{t('analyze.balanced')}</option>
                        <option value="conservative">{t('analyze.conservative')}</option>
                        <option value="aggressive">{t('analyze.aggressive')}</option>
                    </select>

                    <button className={styles.button} onClick={submit}>{t('analyze.run')}</button>

                    <button className={styles.button} onClick={() => loadInsights(false)} disabled={loadingInsights}>
                        {loadingInsights ? t('loading') : t('analyze.loadInsights')}
                    </button>

                    <button className={styles.button} onClick={() => loadInsights(true)} disabled={loadingInsights} title={t('analyze.updateTooltip')}>
                        {t('analyze.update')}
                    </button>

                    <button className={styles.button} disabled={!insights} onClick={openInsightDetail} title={t('analyze.openDetailTooltip')}>
                        {t('analyze.openDetail')}
                    </button>

                    {/* 템플릿 선택 */}
                    <div className={styles.inlineBlock}>
                        <span className={styles.small}>{t('analyze.reportTemplate')}</span>

                        <select className={styles.input} value={reportTemplate} onChange={(e) => setReportTemplate(e.target.value)}>
                            <option value="short">{t('analyze.templateShort')}</option>
                            <option value="base">{t('analyze.templateBase')}</option>
                            <option value="detail">{t('analyze.templateDetail')}</option>
                        </select>
                    </div>

                    {/* 보고서 기능 ON/OFF 토글 */}
                    <div className={styles.reportToggleBlock} title={t('analyze.reportToggleTooltip')}>
                        <span className={styles.small}>{t('analyze.aiReport')}</span>

                        <label className={`${styles.toggle} ${reportEnabled ? styles.toggleOn : styles.toggleOff}`}>
                            <input type="checkbox" checked={reportEnabled} onChange={(e) => setReportEnabled(e.target.checked)} />

                            <span className={styles.knob} />
                            <span className={styles.toggleText}>{reportEnabled ? t('analyze.on') : t('analyze.off')}</span>
                        </label>
                    </div>
                </div>

                {/* 검색창 */}
                <div className={styles.row}>
                    <input className={styles.input} value={keywords} onChange={(e) => setKeywords(e.target.value)} placeholder={t('analyze.searchPlaceholder')}/>

                    {loadingSearch && <span className={styles.loading}><span className={styles.spinner} />{t('analyze.searching')}</span>}
                </div>

                {suggestions.length > 0 && (
                    <div className={styles.suggestBox}>
                        {suggestions.map((s) => (
                            <div key={s.symbol} className={styles.item} onClick={() => pickSuggestion(s)}>
                                <div>
                                    <b>{s.symbol}</b> — {s.name} <span className={styles.small}>({s.region})</span>
                                </div>

                                <div className={styles.small}>{t('analyze.matchScore')}: {s.matchScore}</div>
                            </div>
                        ))}
                    </div>
                )}

                {err && <div className={styles.error}>{err}</div>}

                {insights && (
                    <div className={styles.insightsSection}>
                        <h3 className={styles.insightsHeader}>{t('analyze.insights')}</h3>

                        {/* 최근 주가 + 차트 */}
                        <div className={styles.small}>
                            {quote?.latestTradingDay ? `${t('analyze.latest')}: ${quote.latestTradingDay}` : null}
                        </div>

                        <div className={styles.priceLine}>
                            <b>{t('analyze.price')}</b>: {quote?.price != null ? `$${Number(quote.price).toFixed(2)}` : t('messages.noData')}{" "}

                            <span className={styles.small}>
                                ({t('analyze.change')}: {quote?.change != null ? Number(quote.change).toFixed(2) : t('messages.noData')} / {quote?.changePercent ?? t('messages.noData')})
                            </span>
                        </div>

                        <div className={styles.chartWrap}>
                            <LineChartCanvas points={insights?.prices?.points || []} valueKey="close" labelKey="date" height={220} />
                        </div>

                        {/* Technicals (있을 때만 표시) */}
                        {tech ? (
                            <div className={styles.techBox}>
                                <h4 className={styles.techTitle}>{t('analyze.technicals')}</h4>

                                <div className={styles.techGrid}>
                                    <div>RSI14: {tech.rsi14?.toFixed?.(2) ?? (tech.rsi14 ?? t('messages.noData'))}</div>
                                    <div>MA50: {tech.ma50 != null ? Number(tech.ma50).toFixed(2) : t('messages.noData')}</div>
                                    <div>MA200: {tech.ma200 != null ? Number(tech.ma200).toFixed(2) : t('messages.noData')}</div>
                                    <div>{t('analyze.aboveMA200')}: {tech.priceAboveMa200 == null ? t('messages.noData') : String(tech.priceAboveMa200)}</div>
                                    <div>{t('analyze.week52High')}: {tech.week52High != null ? Number(tech.week52High).toFixed(2) : t('messages.noData')}</div>
                                    <div>{t('analyze.week52Low')}: {tech.week52Low != null ? Number(tech.week52Low).toFixed(2) : t('messages.noData')}</div>
                                </div>
                            </div>
                        ) : null}

                        {/* 재무 요약 */}
                        <div className={styles.sectionBox}>
                            <h4 className={styles.sectionSubtitle}>{t('analyze.fundamentals')}</h4>

                            <div className={styles.small}>
                                {t('analyze.sector')}: {fund?.sector ?? t('messages.noData')} / {t('analyze.industry')}: {fund?.industry ?? t('messages.noData')}
                            </div>

                            <div>PER: {fund?.pe ?? t('messages.noData')}</div>
                            <div>{t('analyze.revenueYoY')}: {fund?.revYoY != null ? `${(fund.revYoY * 100).toFixed(1)}%` : t('messages.noData')}</div>
                            <div>{t('analyze.marketCap')}: {fund?.marketCap ?? t('messages.noData')}</div>
                        </div>

                        {/* 뉴스 요약 */}
                        <div className={styles.sectionBox}>
                            <h4 className={styles.sectionSubtitle}>{t('analyze.news')}</h4>

                            <div>
                                {t('analyze.positiveRatio')}:{" "}
                                {news?.positiveRatio != null ? `${(news.positiveRatio * 100).toFixed(0)}%` : t('messages.noData')}
                            </div>

                            {Array.isArray(news?.items) && news.items.length > 0 ? (
                                <ul>
                                {news.items.map((it, idx) => {
                                    const title = toDisplayText(it?.title, t('analyze.untitled'));
                                    const sourceLabel = toDisplayText(it?.source, "");
                                    const url = toHttpUrl(it?.url) || toHttpUrl(it?.source?.url);

                                    // url이 없으면 검색 링크 fallback
                                    const searchUrl =
                                    market === "KR"
                                        ? `https://search.naver.com/search.naver?query=${encodeURIComponent(title)}`
                                        : `https://www.google.com/search?q=${encodeURIComponent(title)}`;

                                    const href = url || searchUrl;

                                    return (
                                    <li key={idx}>
                                        <a href={href} target="_blank" rel="noreferrer noopener">
                                            {title}
                                        </a>
                                        {sourceLabel ? <span className={styles.small}> {" "}({sourceLabel})</span> : null}
                                    </li>
                                    );
                                })}
                                </ul>
                            ) : Array.isArray(news?.headlines) && news.headlines.length > 0 ? (
                                <ul>
                                    {news.headlines.map((h, idx) => (
                                        <li key={idx}>{toDisplayText(h, t('analyze.untitled'))}</li>
                                    ))}
                                </ul>
                            ) : null}
                        </div>

                        {/* 추천 이유 텍스트 + 점수 */}
                        <div className={styles.sectionBox}>
                            <h4 className={styles.sectionSubtitle}>{t('analyze.recommendation')}</h4>

                            <div className={styles.recBox}>
                                <b>{rec?.action ?? t('messages.noData')}</b> / {t('analyze.score')}: {rec?.score ?? t('messages.noData')} / {t('analyze.confidence')}:{" "}
                                {rec?.confidence ?? t('messages.noData')}
                            </div>

                            <div className={styles.recText}>{rec?.text}</div>

                            {Array.isArray(rec?.reasons) && rec.reasons.length > 0 && (
                                <ul>
                                    {rec.reasons.map((r, idx) => (
                                        <li key={idx}>{toDisplayText(r, t('messages.noData'))}</li>
                                    ))}
                                </ul>
                            )}
                        </div>

                        {/* 점수 breakdown 표 */}
                        {Array.isArray(rec?.breakdown) && rec.breakdown.length > 0 && (
                        <div className={styles.sectionBox}>
                            <h4 className={styles.sectionSubtitle}>Score Breakdown</h4>

                            <table className={styles.table}>
                                <thead>
                                    <tr>
                                        <th>Metric</th>
                                        <th>Value</th>
                                        <th>Weight</th>
                                        <th>Points</th>
                                        <th>Rule</th>
                                        <th>Evidence</th>
                                    </tr>
                                </thead>

                                <tbody>
                                    {rec.breakdown.map((b, i) => {
                                        const v =
                                            b.valueType === "pct" ? pct(b.value) :
                                            b.valueType === "number" ? num(b.value) :
                                            (b.value ?? "N/A");

                                        return (
                                            <tr key={i}>
                                                <td>{b.metric}</td>
                                                <td>{v}</td>
                                                <td>{b.weight}</td>
                                                <td><b>{b.points}</b></td>
                                                <td className={styles.small}>{b.rule}</td>
                                                <td className={styles.small}>{b.evidence}</td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>

                            <div className={styles.small}>
                                baseScore: {rec.baseScore ?? "N/A"} / totalScore: {rec.score ?? "N/A"}
                            </div>
                        </div>
                        )}
                    </div>
                )}

                {run && (
                    <div className={styles.runResult}>
                        <div className={styles.small}>runId: {run.runId}</div>

                        {decision && (
                            <div className={styles.decisionBox}>
                                <b>Decision:</b> {decision.action} / confidence: {decision.confidence}

                                {Array.isArray(decision.reasons) && decision.reasons.length > 0 && (
                                    <ul>
                                        {decision.reasons.map((r, idx) => <li key={idx}>{toDisplayText(r, t('messages.noData'))}</li>)}
                                    </ul>
                                )}
                            </div>
                        )}

                        <pre>{JSON.stringify(run.result, null, 2)}</pre>
                    </div>
                )}
            </div>

            <div className={`${styles.grid} ${styles.gridSection}`}>
                <div className={styles.card}>
                    <h3>History</h3>

                    {history.map((h) => (
                        <div key={h.id} className={styles.item} onClick={() => openDetail(h.id)}>
                            <div><b>{h.ticker}</b> ({h.market}) — {h.action ?? "N/A"} ({h.confidence ?? "N/A"})</div>
                            <div className={styles.small}>{h.createdAt}</div>
                        </div>
                    ))}
                </div>

                <div className={styles.card}>
                    <h3>Detail</h3>

                    {detail ? <pre>{JSON.stringify(detail, null, 2)}</pre> : <div className={styles.small}>Select a history item.</div>}
                </div>
            </div>
        </div>
    );
}
