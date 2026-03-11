import React, { useEffect, useState, useMemo, useRef } from "react";
import { Link } from "react-router-dom";
import { pushRecentTicker } from "../api/userLists";
import { apiFetch } from "../api/http";
import { ssePost } from "../api/sseFetch";
import { fetchOHLC } from "../api/ai";
import { exportReportJson, exportReportPdf, exportReportTxt } from "../api/report";
import { useRealtimeQuote } from "../hooks/useRealtimeQuote";
import LineChartCanvas from "../components/LineChartCanvas";
import EvidenceTabs from "../components/EvidenceTabs";
import QuantPanel from "../components/QuantPanel";
import InteractiveChart from "../components/InteractiveChart";
import MultiTimeframeChart from "../components/MultiTimeframeChart";
import { OHLCVolumeChart } from "../components/AdvancedCharts";
import {
  ScoreBreakdownChart,
  RiskGaugeChart,
  MetricsBarChart,
  SentimentChart,
} from "../components/InsightCharts";
import { ConfidenceDetails } from "../components/InsightConfidence";
import ETFInfo from "../components/ETFInfo";
import styles from "../css/InsightDetail.module.css";
import { toDisplayText, toHttpUrl } from "../utils/formatters";

function pct(x) {
    if (x === null || x === undefined || Number.isNaN(Number(x))) return "N/A";
    return `${(Number(x) * 100).toFixed(1)}%`;
}

function num(x, d = 2) {
    if (x === null || x === undefined || Number.isNaN(Number(x))) return "N/A";
    return Number(x).toFixed(d);
}

function fmtDate(iso) {
    if (!iso) return "";

    try {
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) return "";
        return d.toISOString().slice(0, 10);
    } catch {
        return "";
    }
}

function sentimentClass(styles, s) {
    const label = s?.label;
    if (label === "positive") return `${styles.badge} ${styles.badgePos}`;
    if (label === "negative") return `${styles.badge} ${styles.badgeNeg}`;
    return `${styles.badge} ${styles.badgeNeu}`;
}

function toPreText(value) {
    if (value === null || value === undefined) return "";
    if (typeof value === "string") return value;

    try {
        return JSON.stringify(value, null, 2);
    } catch {
        return String(value);
    }
}

export default function InsightDetail() {
    const params = useMemo(() => new URLSearchParams(window.location.search), []);
    const ticker = (params.get("ticker") || "").trim();
    const market = (params.get("market") || "US").trim();
    const test = params.get("test") === "true";

    const [err, setErr] = useState("");
    const [loading, setLoading] = useState(false);
    const [insights, setInsights] = useState(null);

    const [reportText, setReportText] = useState("");
    const [loadingReport, setLoadingReport] = useState(false);

    const [reportHistory, setReportHistory] = useState(null);
    const [histErr, setHistErr] = useState("");
    const [histLoading, setHistLoading] = useState(false);
    const [selectedHistId, setSelectedHistId] = useState(null);
    const [showCompare, setShowCompare] = useState(false);

    const [valuation, setValuation] = useState(null);
    const [valErr, setValErr] = useState("");

    const [ohlcData, setOhlcData] = useState(null);
    const [ohlcLoading, setOhlcLoading] = useState(false);

    const reportConnRef = useRef(null);
    const rafRef = useRef(0);
    const bufRef = useRef("");

    const rec = insights?.recommendation;
    const quote = insights?.quote;
    const realtimeQuote = useRealtimeQuote(market === "US" ? ticker : null);
    const livePrice = realtimeQuote?.price ?? quote?.price;
    const fund = insights?.fundamentals;
    const news = insights?.news;
    const cacheMeta = insights?._cache;

    const momentum = useMemo(() => {
        const pts = insights?.prices?.points;
        if (!Array.isArray(pts) || pts.length < 2) return null;

        const a = Number(pts[0]?.close);
        const b = Number(pts[pts.length - 1]?.close);

        if (!Number.isFinite(a) || !Number.isFinite(b) || a === 0) return null;

        return (b - a) / a;
    }, [insights]);

    const reportEnabled = useMemo(() => {
        const qp = params.get("report");
        if (qp === "true") return true;
        if (qp === "false") return false;
        return localStorage.getItem("reportEnabled") === "1";
    }, [params]);

    async function loadReportHistory() {
        if (!ticker) return;

        setHistErr("");
        setHistLoading(true);

        try {
            const d = await apiFetch(`/api/market/report-history?ticker=${encodeURIComponent(ticker)}&market=${encodeURIComponent(market)}&test=${test}&days=90&newsLimit=20&limit=20`);

            setReportHistory(d);
            setSelectedHistId(null);
        } catch (e) {
            setHistErr(e.message || String(e));
            setReportHistory(null);
        } finally {
            setHistLoading(false);
        }
    }

    async function loadValuation(refresh = false) {
        setValErr("");

        try {
            const v = await apiFetch(`/api/market/valuation?test=${test}&refresh=${refresh}`, {
                method: "POST",
                body: JSON.stringify({ ticker, market, days: 90, newsLimit: 20 }),
            });

            setValuation(v);
        } catch (e) {
            setValErr(e.message || String(e));
            setValuation(null);
        }
    }

    async function loadOHLC() {
        if (!ticker) return;

        setOhlcLoading(true);
        try {
            const data = await fetchOHLC(ticker, market, 90);
            setOhlcData(data?.data || []);
        } catch (e) {
            console.error("Failed to load OHLC data:", e);
            setOhlcData(null);
        } finally {
            setOhlcLoading(false);
        }
    }

    async function load(refresh = false) {
        if (!ticker) {
            setErr("ticker is missing in URL. ex) /insight-detail?ticker=AAPL&market=US");
            return;
        }

        setErr("");
        setLoading(true);

        try {
            const data = await apiFetch(`/api/market/insights?test=${test}&refresh=${refresh}`, {
                method: "POST",
                body: JSON.stringify({
                    ticker,
                    market,
                    days: 90,
                    newsLimit: 20,
                }),
            });
            setInsights(data);
        } catch (e) {
            setErr(e.message || String(e));
            setInsights(null);
        } finally {
            setLoading(false);
        }
    }

    async function generateReportStream() {
        
        if (!ticker) return;

        setErr("");
        setLoadingReport(true);
        setReportText("");

        bufRef.current = "";
        reportConnRef.current?.close?.();

        
        try {
            const { close, done } = ssePost(`/api/market/report/stream?test=${test}&web=true&refresh=true`, {
                ticker,
                market,
                days: 90,
                newsLimit: 20,
            }, {
                onOpen: (res) => {
                    console.log("SSE open status=", res.status);
                    console.log("content-type=", res.headers.get("content-type"));
                },

                onMessage: ({ event, data }) => {
                    // 서버 이벤트 설계에 따라 분기
                    // 추천: event=delta(본문), event=done(완료), event=error(오류)
                    if (event === "done") {
                        close();
                        return;
                    }

                    if (event === "error") {
                        // 서버가 data에 에러 메시지를 넣어줄 수 있음
                        setErr(data || "report stream error");
                        close();
                        return;
                    }

                    // 연결 확인/하트비트 이벤트는 본문에서 제외
                    if (event === "open" || event === "ping" || event === "heartbeat") {
                        return;
                    }

                    // 본문 이벤트만 누적
                    if (event !== "delta" && event !== "message") {
                        return;
                    }

                    bufRef.current += data;

                    // 매 chunk마다 setState 하면 렌더가 너무 잦아져서
                    // requestAnimationFrame으로 1프레임에 한 번만 반영
                    if (!rafRef.current) {
                        rafRef.current = requestAnimationFrame(() => {
                            const chunk = bufRef.current;
                            bufRef.current = "";

                            if (chunk) {
                                setReportText((prev) => prev + chunk);
                            }
                            
                            rafRef.current = 0;
                        });
                    }
                },

                onError: (e) => {
                    setErr(e?.message || String(e));
                },
            });

            reportConnRef.current = { close };

            await done;
        } catch (e) {
            setErr(e?.message || String(e));
        } finally {
            setLoadingReport(false);
        }
    }

    async function exportReport(format) {
        if (!ticker) return;

        try {
            const body = {
                ticker,
                market,
                days: 90,
                newsLimit: 20,
                test
            };

            if (format === 'txt') {
                await exportReportTxt(body);
            } else if (format === 'json') {
                await exportReportJson(body);
            } else if (format === 'pdf') {
                await exportReportPdf(body);
            }
        } catch (error) {
            console.error('Export failed:', error);
            setErr('Export failed: ' + error.message);
        }
    }

    useEffect(() => {
        if (ticker) pushRecentTicker(ticker);
    }, [ticker]);

    useEffect(() => {
        return () => {
            reportConnRef.current?.close?.();
            if (rafRef.current) cancelAnimationFrame(rafRef.current);
        };
    }, []);

    useEffect(() => {
        (async () => {
            await load(false);
            await loadValuation(false);
            await loadReportHistory();
            await loadOHLC();
        })();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);


    return (
        <div className={styles.container}>
            {/* Top Bar */}
            <div className={styles.topbar}>
                {/* 제목 및 캐시 */}
                <div>
                    <div className={styles.title}>Insight Detail</div>

                    <div className={styles.sub}>
                        {ticker || "N/A"} / {market} {test ? "(TEST MODE)" : ""}
                    </div>

                    {/* ✅ 캐시 상태 표시 */}
                    {cacheMeta && (
                        <div className={styles.metaRow}>
                            <span className={`${styles.badge} ${cacheMeta.cached ? styles.badgeCached : styles.badgeLive}`}>
                                {cacheMeta.cached ? "CACHED(DB)" : "FRESH(Updated)"}
                            </span>
                            
                            <div>
                                <div>
                                {cacheMeta.insightsUpdatedAt && (
                                    <span className={styles.metaText}>insightsUpdatedAt: {String(cacheMeta.insightsUpdatedAt).slice(0, 19)}</span>
                                )}
                                </div>

                                <div>
                                {cacheMeta.reportUpdatedAt && (
                                    <span className={styles.metaTextSpaced}>reportUpdatedAt: {String(cacheMeta.reportUpdatedAt).slice(0, 19)}</span>
                                )}
                                </div>
                            </div>
                        </div>
                    )}
                </div>
                
                {/* 액션 버튼 */}
                <div className={styles.topActionsSpaced}>
                    <Link className={styles.linkBtn} to={`/analyze?test=${test}`}>
                        Back
                    </Link>

                    <button className={styles.btn} onClick={() => load(false)} disabled={loading}>
                        {loading ? "Loading..." : "Reload (Use Cache)"}
                    </button>

                    {/* ✅ 업데이트 버튼: refresh=true */}
                    <button className={styles.btnPrimary} onClick={() => load(true)} disabled={loading}>
                        Update
                    </button>

                    {/* ✅ REPORT: ON일 때만 버튼 노출 */}
                    {reportEnabled && (
                        <button className={styles.btnPrimary} onClick={generateReportStream} disabled={loadingReport} title="웹 검색으로보고서를 생성합니다.">
                            {loadingReport ? "Generating..." : "AI Report"}
                        </button>
                    )}

                    {/* REPORT: 생성 중지 버튼 */}
                    {reportEnabled && (
                        <button className={styles.btn} onClick={() => reportConnRef.current?.close?.()} disabled={!loadingReport}>
                            Stop
                        </button>
                    )}
                </div>
            </div>

            {err && <div className={styles.error}>{err}</div>}

            {!insights ? (
                <div className={styles.card}>
                    <div className={styles.small}>No data yet.</div>
                </div>
            ) : (
                <>
                {/* ETF Information (if applicable) */}
                <ETFInfo ticker={ticker} market={market} />

                {/* Recommendation */}
                <div className={styles.card}>
                    <div className={styles.h3}>Recommendation</div>

                    <div className={styles.kvRow}>
                        <div className={styles.kv}>
                            <div className={styles.k}>Action</div>
                            <div className={styles.v}><b>{toDisplayText(rec?.action, "N/A")}</b></div>
                        </div>

                        <div className={styles.kv}>
                            <div className={styles.k}>Score</div>
                            <div className={styles.v}>{rec?.score ?? "N/A"}</div>
                        </div>

                        <div className={styles.kv}>
                            <div className={styles.k}>Confidence</div>
                            <div className={styles.v}>{rec?.confidence ?? "N/A"}</div>
                        </div>

                        <div className={styles.kv}>
                            <div className={styles.k}>Momentum(90d)</div>
                            <div className={styles.v}>{momentum == null ? "N/A" : pct(momentum)}</div>
                        </div>
                    </div>

                    <div className={styles.blockText}>{toDisplayText(rec?.text, "")}</div>

                    {Array.isArray(rec?.reasons) && rec.reasons.length > 0 && (
                        <ul className={styles.list}>
                            {rec.reasons.map((r, idx) => (
                                <li key={idx}>{toDisplayText(r, "N/A")}</li>
                            ))}
                        </ul>
                    )}
                </div>

                {/* Quant Signal */}
                <QuantPanel quant={insights?._quant} />

                {/* Valuation */}
                <div className={styles.cardSpaced}>
                    <div className={styles.h3}>Valuation</div>

                    {valErr && <div className={styles.error}>{valErr}</div>}

                    {!valuation ? (
                        <div className={styles.small}>No valuation yet.</div>
                    ) : (
                        <>
                        <div className={styles.kvRow}>
                            <div className={styles.kv}>
                                <div className={styles.k}>Label</div>
                                <div className={styles.v}><b>{toDisplayText(valuation.label, "N/A")}</b></div>
                            </div>

                            <div className={styles.kv}>
                                <div className={styles.k}>Score</div>
                                <div className={styles.v}><b>{valuation.score}</b> / 100</div>
                            </div>

                            <div className={styles.kv}>
                                <div className={styles.k}>P/E</div>
                                <div className={styles.v}>{valuation.pe ?? "N/A"}</div>
                            </div>

                            <div className={styles.kv}>
                                <div className={styles.k}>P/S</div>
                                <div className={styles.v}>{valuation.ps ?? "N/A"}</div>
                            </div>

                            <div className={styles.kv}>
                                <div className={styles.k}>P/B</div>
                                <div className={styles.v}>{valuation.pb ?? "N/A"}</div>
                            </div>
                        </div>

                        <div className={`${styles.small} ${styles.infoText}`}>
                            computedAt: {String(valuation.computedAt).slice(0, 19)}
                        </div>

                        {(valuation.rationales || []).length > 0 && (
                            <ul className={`${styles.list} ${styles.infoText}`}>
                                {valuation.rationales.map((x, i) => <li key={i}>{toDisplayText(x, "N/A")}</li>)}
                            </ul>
                        )}
                        </>
                    )}
                </div>

                {/* ✅ 점수 breakdown 표 */}
                {Array.isArray(rec?.breakdown) && rec.breakdown.length > 0 && (
                    <div className={styles.card}>
                        <div className={styles.h3}>Score Breakdown</div>

                        <div className={styles.tableWrap}>
                            <table className={styles.table}>
                                <thead>
                                    <tr>
                                        <th>Metric</th>
                                        <th>Value</th>
                                        <th>Points</th>
                                        <th>Weight</th>
                                        <th>Evidence</th>
                                    </tr>
                                </thead>


                                <tbody>
                                    {rec.breakdown.map((b, i) => (
                                        <tr key={i}>
                                            <td><b>{toDisplayText(b.metric, "metric")}</b></td>
                                            <td>{b.valueType === "pct" ? pct(b.value) : b.valueType === "number" ? num(b.value) : toDisplayText(b.value, "—")}</td>
                                            <td><b>{toDisplayText(b.points, "—")}</b></td>
                                            <td>{toDisplayText(b.weight, "—")}</td>
                                            <td className={styles.small}>{toDisplayText(b.evidence || b.rule, "")}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>

                        <div className={`${styles.small} ${styles.infoText}`}>
                            baseScore: {rec.baseScore ?? "N/A"} / totalScore: {rec.score ?? "N/A"}
                        </div>
                    </div>
                )}

                {/* ✅ Insight Visualizations */}
                {rec && (
                    <div className={styles.visualGrid}>
                        <RiskGaugeChart score={rec.score} />
                        {Array.isArray(rec.breakdown) && rec.breakdown.length > 0 && (
                            <>
                                <ScoreBreakdownChart breakdown={rec.breakdown} />
                                <MetricsBarChart breakdown={rec.breakdown} />
                            </>
                        )}
                        {news && <SentimentChart news={news} />}
                    </div>
                )}

                {/* ✅ AI Confidence Analysis */}
                {rec && (
                    <ConfidenceDetails recommendation={rec} />
                )}

                {/* ✅ REPORT: A4 보고서 카드 (버튼은 topbar에 있고, 출력은 여기) */}
                <div className={styles.card}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                        <div className={styles.h3}>AI Report (A4)</div>
                        {(reportText || insights) && (
                            <div style={{ display: 'flex', gap: '8px' }}>
                                <button
                                    className={styles.btn}
                                    onClick={() => exportReport('pdf')}
                                    title="Download professional PDF report"
                                >
                                    📕 PDF
                                </button>
                                <button
                                    className={styles.btn}
                                    onClick={() => exportReport('txt')}
                                    title="Download report as TXT"
                                >
                                    📄 TXT
                                </button>
                                <button
                                    className={styles.btn}
                                    onClick={() => exportReport('json')}
                                    title="Download report with full data as JSON"
                                >
                                    📊 JSON
                                </button>
                            </div>
                        )}
                    </div>

                    {!reportEnabled ? (
                        <div className={styles.small}>
                            메인 Analyze 화면에서 <b>AI 보고서 토글을 ON</b>으로 바꾸면, 이 페이지에서 보고서 생성 버튼이 나타납니다.
                        </div>
                    ) : !reportText ? (
                        <div className={styles.reportPlaceholder}>
                            우측 상단 <b>Generate AI Report (A4)</b> 버튼을 눌러 생성하세요. (OpenAI + 웹 검색 사용, 토큰 소모 큼)
                        </div>
                    ) : (
                        <div className={styles.a4}>
                            <div className={styles.a4Title}>{ticker} 종합 분석 리포트</div>
                            <div className={styles.a4Body}>{reportText}</div>
                        </div>
                    )}
                </div>

                <EvidenceTabs insights={insights} report={reportText} />

                {/* Multi-Timeframe Momentum Analysis */}
                {insights?.technical?.multi_momentum && (
                    <MultiTimeframeChart multiMomentum={insights.technical.multi_momentum} />
                )}

                {/* Price + Chart */}
                <div className={styles.cardSmallSpaced}>
                    <div className={styles.h3}>Price</div>

                    <div className={styles.small}>
                        {quote?.latestTradingDay ? `latest: ${quote.latestTradingDay}` : null}
                    </div>

                    <div className={styles.priceLine}>
                        <b>{livePrice != null ? `$${Number(livePrice).toFixed(2)}` : "N/A"}</b>
                        {realtimeQuote && <span className={styles.small} title="Real-time"> *</span>}
                        <span className={styles.small}>
                            {" "}
                            (change: {quote?.change ?? "N/A"} / {quote?.changePercent ?? "N/A"})
                        </span>
                    </div>

                    <div className={styles.chartWrap}>
                        <LineChartCanvas points={insights?.prices?.points || []} valueKey="close" labelKey="date" height={260} />
                    </div>
                </div>

                {/* Interactive Chart with zoom/pan */}
                {insights?.prices?.points && insights.prices.points.length > 0 && (
                    <InteractiveChart
                        points={insights.prices.points}
                        valueKey="close"
                        labelKey="date"
                    />
                )}

                {/* OHLC + Volume Charts */}
                {ohlcData && Array.isArray(ohlcData) && ohlcData.length > 0 && (
                    <OHLCVolumeChart ohlcData={ohlcData} />
                )}

                {ohlcLoading && (
                    <div className={styles.card}>
                        <div className={styles.small}>Loading OHLC data...</div>
                    </div>
                )}

                <div className={styles.grid}>
                    {/* Fundamentals */}
                    <div className={styles.card}>
                        <div className={styles.h3}>Fundamentals</div>
                        <div className={styles.small}>
                            sector: {toDisplayText(fund?.sector, "N/A")} / industry: {toDisplayText(fund?.industry, "N/A")}
                        </div>

                        <div className={styles.kvCol}>
                            <div><b>PER</b>: {fund?.pe ?? "N/A"}</div>

                            <div>
                                <b>Revenue YoY</b>: {fund?.revYoY != null ? pct(fund.revYoY) : "N/A"}

                                {fund?.revYoYMeta?.latestQuarter && fund?.revYoYMeta?.compareQuarter ? (
                                    <span className={styles.small}>
                                        {" "}
                                        ({fund.revYoYMeta.latestQuarter} vs {fund.revYoYMeta.compareQuarter})
                                    </span>
                                ) : null}
                            </div>

                            {fund?.revYoYMeta?.source ? (
                                <div className={styles.small}>
                                    source: {toDisplayText(fund.revYoYMeta.source, "N/A")}
                                </div>
                            ) : null}

                            <div><b>MarketCap</b>: {fund?.marketCap ?? "N/A"}</div>
                        </div>
                    </div>

                    {/* News */}
                    <div className={styles.card}>
                        <div className={styles.h3}>News</div>

                        <div>
                            positiveRatio(heuristic):{" "}
                            {news?.positiveRatio != null ? `${Math.round(news.positiveRatio * 100)}%` : "N/A"}
                        </div>

                        {Array.isArray(news?.items) && news.items.length > 0 ? (
                            <ul className={styles.newsList}>
                                {news.items.map((it, idx) => {
                                    const title = toDisplayText(it?.title, "untitled");
                                    const url = toHttpUrl(it?.url) || toHttpUrl(it?.source?.url);

                                    // url이 없으면 검색 링크 fallback
                                    const searchUrl = market === "KR"
                                        ? `https://search.naver.com/search.naver?query=${encodeURIComponent(title)}`
                                        : `https://www.google.com/search?q=${encodeURIComponent(title)}`;

                                    const href = url || searchUrl;

                                    const d = fmtDate(it?.publishedAt);
                                    const src = toDisplayText(it?.source, "");

                                    return (
                                        <li key={idx} className={styles.newsItem}>
                                            <div className={styles.newsTop}>
                                                <a href={href} target="_blank" rel="noreferrer noopener" className={styles.newsLink}>
                                                    {title}
                                                </a>

                                                {it?.sentiment ? (
                                                    <span className={sentimentClass(styles, it.sentiment)} title={`score: ${it.sentiment.score}`}>
                                                        {it.sentiment.label}
                                                    </span>
                                                ) : null}
                                            </div>

                                            <div className={styles.newsMeta}>
                                                {d ? <span>{d}</span> : null}
                                                {d && src ? <span className={styles.dot}>•</span> : null}
                                                {src ? <span>{src}</span> : null}
                                            </div>
                                        </li>
                                    );
                                })}
                            </ul>
                        ) : Array.isArray(news?.headlines) && news.headlines.length > 0 ? (
                            <ul className={styles.newsList}>
                                {news.headlines.map((h, idx) => (
                                    <li key={idx}>{toDisplayText(h, "untitled")}</li>
                                ))}
                            </ul>
                        ) : null}
                    </div>
                </div>

                {/* Report History */}
                <div className={styles.card}>
                    <div className={styles.h3}>Report History</div>

                    <div className={styles.historyTopRow}>
                        <button className={styles.btn} onClick={loadReportHistory} disabled={histLoading}>
                            {histLoading ? "Loading..." : "Reload History"}
                        </button>

                        <label className={styles.historyToggle}>
                            <input type="checkbox" checked={showCompare} onChange={(e) => setShowCompare(e.target.checked)} />
                            <span className={styles.small}>Compare with current</span>
                        </label>
                    </div>

                    {histErr && <div className={styles.error}>{histErr}</div>}

                    {!reportHistory ? (
                        <div className={styles.small}>No history data.</div>
                    ) : (
                        <>
                        <div className={styles.small}>
                            current updatedAt:{" "}
                            {reportHistory.current?.reportUpdatedAt ? reportHistory.current.reportUpdatedAt : "N/A"}
                        </div>

                        <div className={styles.historyGrid}>
                            <div className={styles.historyList}>
                                <div className={styles.historyItemHeader}>Versions</div>

                                {(reportHistory.items || []).length === 0 ? (
                                    <div className={styles.small}>No previous versions yet.</div>
                                ) : (
                                    (reportHistory.items || []).map((it) => {
                                        const active = selectedHistId === it.id;
                                        const reportTextPreview = toPreText(it.report);
                                        const preview = reportTextPreview.slice(0, 80).replace(/\n/g, " ");

                                        return (
                                            <button key={it.id} className={`${styles.historyItem} ${active ? styles.historyItemActive : ""}`} onClick={() => setSelectedHistId(it.id)} title={it.createdAt} >
                                                <div className={styles.historyMeta}>{it.createdAt}</div>
                                                <div className={styles.historyPreview}>{preview}{reportTextPreview.length > 80 ? "…" : ""}</div>
                                            </button>
                                        );
                                    })
                                )}
                            </div>

                            <div className={styles.historyBody}>
                                {!selectedHistId ? (
                                    <div className={styles.small}>Select a version to view.</div>
                                ) : (() => {
                                    const selected = (reportHistory.items || []).find((x) => x.id === selectedHistId);
                                    if (!selected) return <div className={styles.small}>Selected version not found.</div>;

                                    if (!showCompare) {
                                        return (
                                            <>
                                            <div className={styles.historyBodyTitle}>Selected</div>
                                            <pre className={styles.reportPre}>{toPreText(selected.report)}</pre>
                                            </>
                                        );
                                    }

                                    return (
                                    <div className={styles.compareCols}>
                                        <div className={styles.compareCol}>
                                            <div className={styles.historyBodyTitle}>Current</div>
                                            <pre className={styles.reportPre}>{toPreText(reportHistory.current?.report)}</pre>
                                        </div>

                                        <div className={styles.compareCol}>
                                            <div className={styles.historyBodyTitle}>Selected</div>
                                            <pre className={styles.reportPre}>{toPreText(selected.report)}</pre>
                                        </div>
                                    </div>
                                    );
                                })()}
                            </div>
                        </div>

                        <div className={styles.small}>
                            * Update Report(리포트 갱신)를 수행할 때마다 이전 버전이 자동 저장됩니다.
                        </div>
                        </>
                    )}
                </div>

                {/* Raw JSON */}
                <div className={styles.card}>
                    <details>
                        <summary className={styles.summary}>Raw JSON (debug)</summary>
                        <pre className={styles.pre}>{JSON.stringify(insights, null, 2)}</pre>
                    </details>
                </div>
                </>
            )}
        </div>
    );
}
