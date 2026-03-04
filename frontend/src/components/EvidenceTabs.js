import React, { useMemo, useState, memo, useCallback } from "react";
import styles from "../css/EvidenceTabs.module.css";

function sentimentClass(label) {
    const s = String(label || "").toLowerCase();
    if (s.includes("bullish")) return "pos";
    if (s.includes("bearish")) return "neg";
    if (s.includes("neutral")) return "neu";
    return "neu";
}

function sentimentLabelKr(label) {
    const c = sentimentClass(label);
    if (c === "pos") return "긍정";
    if (c === "neg") return "부정";
    return "중립";
}

function parseAlphaTimePublished(tp) {
    // AlphaVantage: "20260110T012345"
    const s = String(tp || "").trim();
    if (!s || s.length < 8) return "";
    const y = s.slice(0, 4);
    const m = s.slice(4, 6);
    const d = s.slice(6, 8);
    const hh = s.length >= 11 ? s.slice(9, 11) : "00";
    const mm = s.length >= 13 ? s.slice(11, 13) : "00";
    return `${y}-${m}-${d} ${hh}:${mm}`;
}

function money(x) {
    const n = Number(x);
    if (!Number.isFinite(n)) return "N/A";
    return n.toLocaleString();
}

function downloadText(filename, text, mime = "text/plain;charset=utf-8") {
    const blob = new Blob([text ?? ""], { type: mime });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");

    a.href = url;
    a.download = filename;

    document.body.appendChild(a);

    a.click();
    a.remove();

    URL.revokeObjectURL(url);
}

function toCsv(rows, headers) {
    const esc = (v) => {
        const s = v == null ? "" : String(v);
        if (/[",\n]/.test(s)) return `"${s.replaceAll('"', '""')}"`;
        return s;
    };

    const head = headers.map(esc).join(",");
    const body = rows.map(r => headers.map(h => esc(r[h])).join(",")).join("\n");
    return `${head}\n${body}\n`;
}

function parseReportSafe(report) {
    if (!report) return null;
    if (typeof report === "object") return report;

    const s = String(report).trim();
    if (!s) return null;

    try {
        return JSON.parse(s);
    } catch {
        // 그냥 텍스트로 내려오는 케이스
        return { text: s };
    }
}

function ymd() {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    return `${y}${m}${dd}`;
}

const EvidenceTabs = memo(function EvidenceTabs({ insights, report }) {
    const [tab, setTab] = useState("news"); // news | fundamentals | breakdown
    const [newsFilter, setNewsFilter] = useState("all"); // all | pos | neu | neg

    const fund = insights?.fundamentals || {};
    const revMeta = fund?.revYoYMeta || null;

    const ticker = insights?.ticker || insights?.quote?.symbol || insights?.symbol || "";
    const market = insights?.market || fund?.market || "";

    const parsedReport = useMemo(() => parseReportSafe(report), [report]);
    const reportText = parsedReport?.text ?? parsedReport?.reportText ?? parsedReport?.markdown ?? "";

    const newsItems = useMemo(() => {
        const items = insights?.news?.items || [];
        return Array.isArray(items) ? items : [];
    }, [insights]);

    const filteredNews = useMemo(() => {
        if (newsFilter === "all") return newsItems;
        return newsItems.filter((it) => sentimentClass(it?.sentimentLabel) === newsFilter);
    }, [newsItems, newsFilter]);

    const breakdown = useMemo(() => {
        const arr = insights?.recommendation?.breakdown || [];
        return Array.isArray(arr) ? arr : [];
    }, [insights]);

    const evidenceJsonPayload = useMemo(() => {
        return {
            meta: {
                generatedAt: new Date().toISOString(),
                ticker: ticker || null,
                market: market || null,
                newsFilter,
            },

            news: filteredNews.map((it) => ({
                title: it?.title ?? null,
                url: it?.url ?? null,
                source: it?.source ?? null,
                publishedAt: it?.publishedAt ? String(it.publishedAt) : parseAlphaTimePublished(it?.timePublished || it?.publishedAt),
                sentimentLabel: it?.sentimentLabel ?? null,
                sentimentClass: sentimentClass(it?.sentimentLabel),
            })),

            fundamentals: {
                sector: fund?.sector ?? null,
                industry: fund?.industry ?? null,
                pe: fund?.pe ?? null,
                revYoY: fund?.revYoY ?? null,
                marketCap: fund?.marketCap ?? null,
                revYoYMeta: revMeta ? {
                    latestQuarter: revMeta.latestQuarter ?? null,
                    latestRevenue: revMeta.latestRevenue ?? null,
                    compareQuarter: revMeta.compareQuarter ?? null,
                    compareRevenue: revMeta.compareRevenue ?? null,
                    source: revMeta.source ?? null,
                } : null,
            },

            breakdown: breakdown.map((b) => ({
                metric: b?.metric ?? null,
                value: b?.value ?? null,
                valueType: b?.valueType ?? null,
                points: b?.points ?? null,
                weight: b?.weight ?? null,
                rule: b?.rule ?? null,
                evidence: b?.evidence ?? null,
            })),

            report: parsedReport ? {
                    ticker: parsedReport?.ticker ?? ticker ?? null,
                    market: parsedReport?.market ?? market ?? null,
                    text: reportText || null,
                } : null,
            };
        }, [ticker, market, newsFilter, filteredNews, fund, revMeta, breakdown, parsedReport, reportText]
    );

    // ✅ Export handlers
    const exportEvidenceJson = useCallback(() => {
        const name = `evidence_${ticker || "unknown"}_${market || "NA"}_${ymd()}.json`;
        downloadText(name, JSON.stringify(evidenceJsonPayload, null, 2), "application/json;charset=utf-8");
    }, [ticker, market, evidenceJsonPayload]);

    const exportNewsCsv = useCallback(() => {
        const rows = filteredNews.map((it) => {
            const publishedAt = it?.publishedAt ? String(it.publishedAt) : parseAlphaTimePublished(it?.timePublished);
            const sClass = sentimentClass(it?.sentimentLabel);

            return {
                title: it?.title ?? "",
                source: it?.source ?? "",
                publishedAt: publishedAt ?? "",
                sentimentKr: sentimentLabelKr(it?.sentimentLabel),
                sentimentLabel: it?.sentimentLabel ?? "",
                sentimentClass: sClass,
                url: it?.url ?? "",
            };
        });

        const headers = ["title", "source", "publishedAt", "sentimentKr", "sentimentLabel", "sentimentClass", "url"];
        const csv = toCsv(rows, headers);

        const name = `news_${ticker || "unknown"}_${market || "NA"}_${newsFilter}_${ymd()}.csv`;
        downloadText(name, csv, "text/csv;charset=utf-8");
    }, [ticker, market, newsFilter, filteredNews]);

    const exportFundamentalsCsv = useCallback(() => {
        const row = {
            ticker: ticker || "",
            market: market || "",
            sector: fund?.sector ?? "",
            industry: fund?.industry ?? "",
            pe: fund?.pe ?? "",
            revYoY: fund?.revYoY ?? "",
            marketCap: fund?.marketCap ?? "",
            latestQuarter: revMeta?.latestQuarter ?? "",
            latestRevenue: revMeta?.latestRevenue ?? "",
            compareQuarter: revMeta?.compareQuarter ?? "",
            compareRevenue: revMeta?.compareRevenue ?? "",
            source: revMeta?.source ?? "",
        };

        const headers = [
            "ticker",
            "market",
            "sector",
            "industry",
            "pe",
            "revYoY",
            "marketCap",
            "latestQuarter",
            "latestRevenue",
            "compareQuarter",
            "compareRevenue",
            "source",
        ];

        const csv = toCsv([row], headers);
        const name = `fundamentals_${ticker || "unknown"}_${market || "NA"}_${ymd()}.csv`;
        downloadText(name, csv, "text/csv;charset=utf-8");
    }, [ticker, market, fund, revMeta]);

    const exportBreakdownCsv = useCallback(() => {
        const rows = breakdown.map((b) => ({
            metric: b?.metric ?? "",
            value: b?.value ?? "",
            valueType: b?.valueType ?? "",
            points: b?.points ?? "",
            weight: b?.weight ?? "",
            rule: b?.rule ?? "",
            evidence: b?.evidence ?? "",
        }));

        const headers = ["metric", "value", "valueType", "points", "weight", "rule", "evidence"];
        const csv = toCsv(rows, headers);

        const name = `breakdown_${ticker || "unknown"}_${market || "NA"}_${ymd()}.csv`;
        downloadText(name, csv, "text/csv;charset=utf-8");
    }, [ticker, market, breakdown]);

    const exportReportMd = useCallback(() => {
        if (!reportText || !String(reportText).trim()) {
            downloadText(`report_${ticker || "unknown"}_${market || "NA"}_${ymd()}.md`, "# (empty)\n", "text/markdown;charset=utf-8");
            return;
        }

        const md = `# AI Report\n\n` + `- Ticker: ${ticker || "N/A"}\n` + `- Market: ${market || "N/A"}\n` + `- GeneratedAt: ${new Date().toISOString()}\n\n` + `---\n\n` + `${reportText}\n`;
        const name = `report_${ticker || "unknown"}_${market || "NA"}_${ymd()}.md`;
        downloadText(name, md, "text/markdown;charset=utf-8");
    }, [ticker, market, reportText]);

    const exportReportJson = useCallback(() => {
        const name = `report_${ticker || "unknown"}_${market || "NA"}_${ymd()}.json`;
        downloadText(name, JSON.stringify(parsedReport ?? { text: reportText ?? "" }, null, 2), "application/json;charset=utf-8");
    }, [ticker, market, parsedReport, reportText]);

    return (
        <div className={styles.card}>
            <div className={styles.headRow}>
                <div className={styles.h3}>Evidence</div>

                {/* ✅ Export UI */}
                <div className={styles.exportRow}>
                    <button className={styles.exportBtn} onClick={exportEvidenceJson} title="뉴스/재무/브레이크다운/리포트를 한 번에 JSON으로">
                        Export JSON
                    </button>

                    <button className={styles.exportBtnGhost} onClick={exportNewsCsv} title="현재 뉴스 필터 기준으로 CSV 다운로드">
                        News CSV
                    </button>

                    <button className={styles.exportBtnGhost} onClick={exportFundamentalsCsv}>
                        Fundamentals CSV
                    </button>

                    <button className={styles.exportBtnGhost} onClick={exportBreakdownCsv}>
                        Breakdown CSV
                    </button>

                    <div className={styles.exportDivider} />

                    <button className={styles.exportBtn} onClick={exportReportMd} title="AI 보고서를 Markdown으로">
                        Report .md
                    </button>

                    <button className={styles.exportBtnGhost} onClick={exportReportJson} title="AI 보고서 원본(JSON)을 그대로 저장">
                        Report JSON
                    </button>
                </div>
            </div>


            <div className={styles.tabRow}>
                <button className={tab === "news" ? styles.tabActive : styles.tab} onClick={() => setTab("news")}>
                    News
                </button>

                <button className={tab === "fundamentals" ? styles.tabActive : styles.tab} onClick={() => setTab("fundamentals")}>
                    Fundamentals
                </button>

                <button className={tab === "breakdown" ? styles.tabActive : styles.tab} onClick={() => setTab("breakdown")} >
                    Score Breakdown
                </button>
            </div>

            {tab === "news" && (
                <>
                <div className={styles.filterRow}>
                    <div className={styles.small}>필터</div>

                    <div className={styles.pills}>
                        {[ ["all", "전체"], ["pos", "긍정"], ["neu", "중립"], ["neg", "부정"],].map(([k, label]) => (
                            <button key={k} className={newsFilter === k ? styles.pillActive : styles.pill} onClick={() => setNewsFilter(k)} >
                                {label}
                            </button>
                        ))}
                    </div>
                </div>

                {filteredNews.length === 0 ? (
                    <div className={styles.empty}>뉴스 데이터가 없습니다.</div>
                ) : (
                    <div className={styles.newsGrid}>
                        {filteredNews.map((it, idx) => {
                            const c = sentimentClass(it?.sentimentLabel);
                            const publishedAt = it?.publishedAt || it?.timePublished; // 둘 다 대응
                            const timeText = it?.publishedAt ? String(it.publishedAt) : parseAlphaTimePublished(publishedAt);

                            return (
                                <a key={`${it?.url || idx}`} className={styles.newsCard} href={it?.url || "#"} target="_blank" rel="noreferrer">
                                    <div className={styles.newsTitle}>{it?.title || "(no title)"}</div>

                                    <div className={styles.metaRow}>
                                        <span className={styles.meta}>
                                            <b>Source</b>: {it?.source || "N/A"}
                                        </span>
                                        
                                        <span className={styles.meta}>
                                            <b>Published</b>: {timeText || "N/A"}
                                        </span>
                                    </div>

                                    <div className={styles.badgeRow}>
                                        <span className={`${styles.badge} ${styles[`badge_${c}`]}`}>
                                            {sentimentLabelKr(it?.sentimentLabel)}{" "}

                                            <span className={styles.badgeSub}>
                                                ({String(it?.sentimentLabel || "N/A")})
                                            </span>
                                        </span>
                                    </div>
                                </a>
                            );
                        })}
                    </div>
                )}
                </>
            )}

            {tab === "fundamentals" && (
                <>
                <div className={styles.block}>
                    <div className={styles.row}>
                        <div className={styles.k}>PE</div>

                        <div className={styles.v}>
                            {fund?.pe == null ? "N/A" : Number(fund.pe).toFixed(2)}
                        </div>
                    </div>

                    <div className={styles.row}>
                        <div className={styles.k}>Revenue YoY</div>
                        
                        <div className={styles.v}>
                            {fund?.revYoY == null ? "N/A" : `${(Number(fund.revYoY) * 100).toFixed(2)}%`}
                        </div>
                    </div>
                </div>

                <div className={styles.block}>
                    <div className={styles.h4}>Revenue YoY 근거(분기 비교)</div>

                    {!revMeta ? (
                        <div className={styles.empty}>revYoYMeta 데이터가 없습니다.</div>
                    ) : (
                        <div className={styles.metaBox}>
                            <div className={styles.metaLine}>
                                <b>Latest Quarter</b>: {revMeta.latestQuarter || "N/A"} /{" "}
                                <b>Revenue</b>: {money(revMeta.latestRevenue)}
                            </div>

                            <div className={styles.metaLine}>
                                <b>Compare Quarter</b>: {revMeta.compareQuarter || "N/A"} /{" "}
                                <b>Revenue</b>: {money(revMeta.compareRevenue)}
                            </div>

                            <div className={styles.metaLine}>
                                <b>Source</b>: {revMeta.source || "N/A"}
                            </div>
                        </div>
                    )}

                    <div className={styles.small}>
                        * “어느 분기 vs 어느 분기”를 화면에 고정 노출해서 검증 가능하게 만듭니다.
                    </div>
                </div>
                </>
            )}

            {tab === "breakdown" && (
                <>
                {breakdown.length === 0 ? (
                    <div className={styles.empty}>breakdown 데이터가 없습니다.</div>
                ) : (
                    <div className={styles.breakdownList}>
                        {breakdown.map((b, i) => {
                            const metric = b?.metric || `metric-${i}`;
                            const points = b?.points;
                            const weight = b?.weight;
                            const value = b?.value;
                            const valueType = b?.valueType;
                            const evidence = b?.evidence || b?.rule || "";

                            const valueText = value == null ? "N/A" : valueType === "pct"
                                ? `${(Number(value) * 100).toFixed(2)}%` : valueType === "number"
                                ? Number(value).toFixed(4) : String(value);

                            return (
                                <details key={`${metric}-${i}`} className={styles.disclosure}>
                                    <summary className={styles.disclosureSum}>
                                        <div className={styles.sumLeft}>
                                            <div className={styles.metric}>{metric}</div>

                                            <div className={styles.sumSmall}>
                                                points: <b>{points ?? "—"}</b>
                                                {weight != null ? (
                                                    <>
                                                    {" "}
                                                    / weight: <b>{weight}</b>
                                                    </>
                                                ) : null}
                                            </div>
                                        </div>

                                        <div className={styles.sumRight}>자세히</div>
                                    </summary>

                                    <div className={styles.disclosureBody}>
                                        <div className={styles.row}>
                                            <div className={styles.k}>계산값(value)</div>
                                            <div className={styles.v}>{valueText}</div>
                                        </div>

                                        <div className={styles.rowTop}>
                                            <div className={styles.k}>근거(evidence)</div>
                                            <div className={styles.evidenceText}>
                                                {evidence ? evidence : "N/A"}
                                            </div>
                                        </div>

                                        <div className={styles.small}>
                                            * points는 스코어에 반영되는 점수, value/evidence는 그 점수가 나온 계산 근거입니다.
                                        </div>
                                    </div>
                                </details>
                            );
                        })}
                    </div>
                )}
                </>
            )}
        </div>
    );
});

export default EvidenceTabs;