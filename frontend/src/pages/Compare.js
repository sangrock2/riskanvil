import React, { useState, useMemo } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../api/http";
import { STALE_TIMES } from "../api/queryClient";
import { useTranslation } from "../hooks/useTranslation";
import MultiLineChartCanvas from "../components/MultiLineChartCanvas";
import styles from "../css/Compare.module.css";


function pct(x) {
    if (x === null || x === undefined || Number.isNaN(Number(x))) return "N/A";
    return `${(Number(x) * 100).toFixed(1)}%`;
}

function num(x, d = 2) {
    if (x === null || x === undefined || Number.isNaN(Number(x))) return "N/A";
    return Number(x).toFixed(d);
}

function calcMomentum(points) {
    if (!Array.isArray(points) || points.length < 2) return null;

    const a = Number(points[0]?.close);
    const b = Number(points[points.length - 1]?.close);

    if (!Number.isFinite(a) || !Number.isFinite(b) || a === 0) return null;

    return (b - a) / a;
}

function normalizeTo100(points) {
    if (!Array.isArray(points) || points.length < 2) return [];

    const base = Number(points[0]?.close);

    if (!Number.isFinite(base) || base === 0) return [];

    return points.filter((p) => p?.date && Number.isFinite(Number(p.close))).map((p) => ({
        date: p.date,
        value: (Number(p.close) / base) * 100,
    }));
}

// results: [{ ticker, insights }] 형태라고 가정
function getMetricRow(ins) {
    const rec = ins?.recommendation;
    return {
        score: Number(rec?.score ?? NaN),
        momentum: Number(ins?.prices?._momentum90d ?? ins?._momentum90d ?? NaN), // 네가 쓰는 필드에 맞춰서
        pe: Number(ins?.fundamentals?.pe ?? NaN),
        revYoY: Number(ins?.fundamentals?.revYoY ?? NaN),
        pos: Number(ins?.news?.positiveRatio ?? NaN),
    };
}

function pickBestWorst(rows, key, higherIsBetter = true) {
    const valid = rows.filter(r => Number.isFinite(r[key]));
    if (!valid.length) return { best: null, worst: null };
    const sorted = [...valid].sort((a,b)=> (a[key]-b[key]));
    return higherIsBetter
        ? { best: sorted[sorted.length-1].ticker, worst: sorted[0].ticker }
        : { best: sorted[0].ticker, worst: sorted[sorted.length-1].ticker };
}

// breakdown union
function buildBreakdownMatrix(results) {
    const metrics = new Set();
    results.forEach(r => (r.insights?.recommendation?.breakdown || []).forEach(b => metrics.add(b.metric)));
    const metricList = Array.from(metrics);

    const matrix = metricList.map(m => {
        const row = { metric: m, cells: [] };

        results.forEach(r => {
            const b = (r.insights?.recommendation?.breakdown || []).find(x => x.metric === m);

            row.cells.push({
                ticker: r.ticker,
                points: b?.points ?? null,
                weight: b?.weight ?? null,
                value: b?.value ?? null,
                evidence: b?.evidence ?? null
            });
        });

        return row;
    });

    return { metricList, matrix };
}


export default function Compare() {
    const { t } = useTranslation();
    const nav = useNavigate();
    const location = useLocation();

    const params = useMemo(() => new URLSearchParams(window.location.search), [location.search]);

    const tickerParam = (params.get("ticker") || "").trim();
    const tickerStr = tickerParam.includes("%2C") ? decodeURIComponent(tickerParam) : tickerParam;
    const tickers = tickerStr.split(",").map(s => s.trim()).filter(Boolean);

    const market = (params.get("market") || "US").trim();
    const test = params.get("test") === "true";

    const [input, setInput] = useState(tickerParam || "AAPL,MSFT,TSLA");
    const [err, setErr] = useState("");
    const qc = useQueryClient();

    // React Query for comparison data
    const { data: rows = [], isLoading: loading, refetch } = useQuery({
        queryKey: ["compare", tickerStr, market, test],
        queryFn: async () => {
            if (!tickers.length) return [];
            const results = await Promise.all(
                tickers.map(async (t) => {
                    const data = await apiFetch(`/api/market/insights?test=${test}&refresh=false`, {
                        method: "POST",
                        body: JSON.stringify({ ticker: t, market, days: 90, newsLimit: 20 }),
                    });
                    const points = data?.prices?.points || [];
                    const rec = data?.recommendation || {};
                    const fund = data?.fundamentals || {};
                    const newsData = data?.news || {};
                    const quote = data?.quote || {};
                    return {
                        ticker: t,
                        insights: data,
                        _cache: data?._cache,
                        currentPrice: quote?.price ?? null,
                        momentum: calcMomentum(points),
                        pe: fund?.pe ?? null,
                        revYoY: fund?.revYoY ?? null,
                        positiveRatio: newsData?.positiveRatio ?? null,
                        score: rec?.score ?? null,
                        breakdown: Array.isArray(rec?.breakdown) ? rec.breakdown : [],
                        normalized: normalizeTo100(points),
                    };
                })
            );
            const map = new Map(results.map((r) => [r.ticker, r]));
            return tickers.map((t) => map.get(t)).filter(Boolean);
        },
        enabled: tickers.length > 0,
        staleTime: STALE_TIMES.INSIGHTS,
    });


    const breakdownMetrics = useMemo(() => {
        const set = new Set();
        rows.forEach((r) => (r.breakdown || []).forEach((b) => b?.metric && set.add(b.metric)));
        return [...set];
    }, [rows]);

    const normalizedSeries = useMemo(() => {
        return rows.filter((r) => Array.isArray(r.normalized) && r.normalized.length >= 2).map((r) => ({
            name: r.ticker,
            points: r.normalized,
        }));
    }, [rows]);

    const go = () => {
        const v = input.split(",").map((t) => t.trim()).filter(Boolean).join(",");
        nav(`/compare?ticker=${encodeURIComponent(v)}&market=${encodeURIComponent(market)}&test=${test}`);
    };

    // ✅ best/worst 강조용: rows -> [{ticker, score, momentum, pe, revYoY, pos}]
    const metricRows = useMemo(() => {
        return rows.map((r) => {
            const base = getMetricRow(r.insights);

            // momentum 필드가 응답에 없다면(=NaN) 기존 계산값으로 fallback
            const momentum = Number.isFinite(base.momentum) ? base.momentum : (Number.isFinite(r.momentum) ? r.momentum : NaN);

            return {
                ticker: r.ticker,
                score: base.score,
                momentum,
                pe: base.pe,
                revYoY: base.revYoY,
                pos: base.pos,
            };
        });
    }, [rows]);

    // ✅ 어떤 ticker가 best/worst인지 계산
    const bestWorst = useMemo(() => {
        return {
            score: pickBestWorst(metricRows, "score", true),
            momentum: pickBestWorst(metricRows, "momentum", true),
            pe: pickBestWorst(metricRows, "pe", false),         // 보통 PER은 낮을수록 "좋다"로 표시
            revYoY: pickBestWorst(metricRows, "revYoY", true),
            pos: pickBestWorst(metricRows, "pos", true),
        };
    }, [metricRows]);

    // ✅ breakdown matrix(점수 근거 표)
    const breakdown = useMemo(() => {
        const results = rows.map((r) => ({ ticker: r.ticker, insights: r.insights }));
        return buildBreakdownMatrix(results);
    }, [rows]);


    const refreshAll = () => {
        qc.invalidateQueries({ queryKey: ["compare"] });
        refetch();
    };

    return (
        <div className={styles.container}>
            <div className={styles.topbar}>
                <div>
                    <div className={styles.title}>{t("compare.title")}</div>

                    <div className={styles.sub}>
                        /compare?ticker=AAPL,MSFT,TSLA — {t("compare.market")}: <b>{market}</b> {test ? "(TEST)" : ""}
                    </div>
                </div>

                <div className={styles.actions}>
                    <a className={styles.linkBtn} href={`/analyze?test=${test}`}>{t("compare.backToAnalyze")}</a>

                    <button className={styles.btn} onClick={() => refetch()} disabled={loading}>
                        {loading ? t("loading") : t("compare.reload")}
                    </button>

                    <button className={styles.btnPrimary} onClick={refreshAll} disabled={loading}>
                        {t("compare.updateAll")}
                    </button>
                </div>
            </div>

            <div className={styles.card}>
                <div className={styles.row}>
                    <input className={styles.input} value={input} onChange={(e) => setInput(e.target.value)} placeholder="AAPL,MSFT,TSLA" />

                    <button className={styles.btn} onClick={go}>
                        {t("compare.go")}
                    </button>
                </div>

                <div className={styles.small}>
                    {t("compare.cacheNote")}
                </div>

                {err && <div className={styles.error}>{err}</div>}
            </div>

            {/* Normalized chart */}
            <div className={styles.card}>
                <div className={styles.h3}>{t("compare.normalizedPrice")}</div>

                <MultiLineChartCanvas series={normalizedSeries} height={300} />

                <div className={styles.small}>
                    {t("compare.normalizedPriceNote")}
                </div>
            </div>

            {/* Main comparison table */}
            <div className={styles.card}>
                <div className={styles.h3}>{t("compare.keyMetrics")}</div>

                <div className={styles.tableWrap}>
                    <table className={styles.table}>
                        <thead>
                            <tr>
                                <th>{t("compare.metric")}</th>

                                {rows.map((r) => (
                                    <th key={r.ticker} className={styles.thTicker}>
                                        <div className={styles.thTop}>
                                            <span>{r.ticker}</span>

                                            <button className={styles.miniBtn} onClick={refreshAll} disabled={loading} title={t("compare.updateThisTicker")}>
                                                {t("compare.update")}
                                            </button>
                                        </div>
                                        {r._cache?.cached != null && (
                                            <div className={styles.small}>
                                                {t("compare.cached")}: {String(r._cache.cached)}
                                                {r._cache.insightsUpdatedAt ? ` / ${r._cache.insightsUpdatedAt}` : ""}
                                            </div>
                                        )}
                                    </th>
                                ))}
                            </tr>
                        </thead>

                        <tbody>
                            <tr>
                                <td><strong>Current Price</strong></td>

                                {rows.map((r) => {
                                    return (
                                        <td key={r.ticker}>
                                            {r.currentPrice == null ? "N/A" : `$${num(r.currentPrice, 2)}`}
                                        </td>
                                    );
                                })}
                            </tr>

                            <tr>
                                <td>{t("compare.momentum90d")}</td>

                                {rows.map((r) => {
                                    const bw = bestWorst.momentum;
                                    const cls = r.ticker === bw.best ? styles.bestCell : r.ticker === bw.worst ? styles.worstCell : "";

                                    return (
                                        <td key={r.ticker} className={cls}>
                                            {r.momentum == null ? "N/A" : pct(r.momentum)}
                                        </td>
                                    );
                                })}
                            </tr>

                            <tr>
                                <td>{t("compare.pe")}</td>

                                {rows.map((r) => {
                                    const bw = bestWorst.pe;
                                    const cls = r.ticker === bw.best ? styles.bestCell : r.ticker === bw.worst ? styles.worstCell : "";

                                    return (
                                        <td key={r.ticker} className={cls}>
                                            {r.pe == null ? "N/A" : num(r.pe, 1)}
                                        </td>
                                    );
                                })}
                            </tr>

                            <tr>
                                <td>{t("compare.revenueYoY")}</td>

                                {rows.map((r) => {
                                    const bw = bestWorst.revYoY;
                                    const cls = r.ticker === bw.best ? styles.bestCell : r.ticker === bw.worst ? styles.worstCell : "";

                                    return (
                                        <td key={r.ticker} className={cls}>
                                            {r.revYoY == null ? "N/A" : pct(r.revYoY)}
                                        </td>
                                    );
                                })}
                            </tr>

                            <tr>
                                <td>{t("compare.newsPositiveRatio")}</td>

                                {rows.map((r) => {
                                    const bw = bestWorst.pos;
                                    const cls = r.ticker === bw.best ? styles.bestCell : r.ticker === bw.worst ? styles.worstCell : "";

                                    return (
                                        <td key={r.ticker} className={cls}>
                                            {r.pos == null ? "N/A" : `${Math.round(r.pos * 100)}%`}
                                        </td>
                                    );
                                })}
                            </tr>

                            <tr>
                                <td>{t("compare.score")}</td>

                                {rows.map((r) => {
                                    const bw = bestWorst.score;
                                    const cls = r.ticker === bw.best ? styles.bestCell : r.ticker === bw.worst ? styles.worstCell : "";

                                    return (
                                        <td key={r.ticker} className={cls}>
                                            {r.score == null ? "N/A" : num(r.score, 1)}
                                        </td>
                                    );
                                })}
                            </tr>
                        </tbody>
                    </table>
                </div>

                <div className={styles.small}>{t("compare.metricsNote")}</div>
            </div>

            {/* Breakdown comparison */}
            <div className={styles.card}>
                <div className={styles.h3}>{t("compare.scoreBreakdown")}</div>

                {breakdownMetrics.length === 0 ? (
                    <div className={styles.small}>{t("compare.noBreakdownData")}</div>
                ) : (
                    <div className={styles.tableWrap}>
                        <table className={styles.table}>
                            <thead>
                                <tr>
                                    <th>{t("compare.metric")}</th>

                                    {rows.map((r) => (
                                        <th key={r.ticker}>{r.ticker}</th>
                                    ))}
                                </tr>
                            </thead>

                            <tbody>
                                {breakdown.matrix.map((row) => (
                                    <tr key={row.metric}>
                                        <td>{row.metric}</td>

                                        {row.cells.map((c) => (
                                            <td key={c.ticker} title={c.evidence || ""}>
                                                {c.points == null ? "—" : <b>{c.points}</b>}

                                                {/* weight/value 같이 표시 */}
                                                <div className={styles.small}>
                                                    {c.weight != null ? `w: ${c.weight}` : "w: —"}
                                                    {" / "}
                                                    {c.value != null ? `v: ${String(c.value)}` : "v: —"}
                                                </div>
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                <div className={styles.small}>
                    {t("compare.breakdownTooltipNote")}
                </div>
            </div>
        </div>
    );

}