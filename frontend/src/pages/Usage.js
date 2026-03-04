import React, { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { apiFetch } from "../api/http";
import { useToast } from "../components/ui/Toast";
import { useTranslation } from "../hooks/useTranslation";
import MultiLineChartCanvas from "../components/MultiLineChartCanvas";
import styles from "../css/Usage.module.css";

function toSeries(daily, key, name) {
    const points = (daily || []).map((d) => ({
        date: d.date,
        value: Number(d[key] ?? 0),
    }));
    return { name, points };
}

export default function Usage() {
    const { t } = useTranslation();
    const nav = useNavigate();
    const location = useLocation();
    const params = useMemo(() => new URLSearchParams(location.search), [location.search]);
    const toast = useToast();

    const [err, setErr] = useState("");
    const [loading, setLoading] = useState(false);
    const [data, setData] = useState(null);
    const [days, setDays] = useState(Number(params.get("days") || 30));
    const [exportFormat, setExportFormat] = useState("json");

    const test = params.get("test") === "true";
    const daily = data?.daily || [];

    const totals = data?.totals || {};
    const tokensIn = Number(totals.tokensIn ?? 0);
    const tokensOut = Number(totals.tokensOut ?? 0);
    const estCost = totals.estimatedCostUsd;

    async function load() {
        setErr("");
        setLoading(true);

        try {
            const d = await apiFetch(`/api/usage/dashboard?test=${test}&days=${days}`);
            setData(d);
        } catch (e) {
            try {
                const d2 = await apiFetch(`/api/usage/summary?test=${test}`);
                setData(d2);
            } catch (e2) {
                setErr(e2.message || String(e2));
                setData(null);
            }
        } finally {
            setLoading(false);
        }
    }

    async function exportData() {
        try {
            const exportData = {
                exportedAt: new Date().toISOString(),
                period: { days, test },
                data: data,
                summary: {
                    totalCalls: data?.totals?.totalCalls || 0,
                    cachedCalls: data?.totals?.cachedCalls || 0,
                    tokensIn: data?.totals?.tokensIn || 0,
                    tokensOut: data?.totals?.tokensOut || 0,
                    estimatedCostUsd: data?.totals?.estimatedCostUsd || 0,
                },
            };

            const blob = new Blob(
                [exportFormat === "json" ? JSON.stringify(exportData, null, 2) : convertToCSV(exportData)],
                { type: exportFormat === "json" ? "application/json;charset=utf-8" : "text/csv;charset=utf-8" }
            );

            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = `usage_export_${new Date().toISOString().slice(0, 10)}.${exportFormat}`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);

            toast.success(t("usage.exportSuccess"));
        } catch (e) {
            toast.error(t("usage.exportFailed"));
            console.error(e);
        }
    }

    function convertToCSV(exportData) {
        const rows = [];

        // Header
        rows.push("Date,Total Calls,Cached Calls,Refresh Calls,Web Calls");

        // Daily data
        if (exportData.data?.daily) {
            exportData.data.daily.forEach(day => {
                rows.push([
                    day.date || "",
                    day.total || 0,
                    day.cached || 0,
                    day.refresh || 0,
                    day.web || 0,
                ].join(","));
            });
        }

        return rows.join("\n");
    }

    async function deleteOldData() {
        if (!window.confirm(t("usage.confirmDelete90d"))) {
            return;
        }

        try {
            await apiFetch("/api/usage/cleanup?olderThanDays=90", { method: "DELETE" });
            await load();
            toast.success(t("usage.deleteSuccess"));
        } catch (e) {
            toast.error(e.message || t("usage.deleteFailed"));
        }
    }

    function renderBlock(title, block) {
        if (!block) return null;

        const hit = block.cacheHitRate ?? 0;
        const hitPct = `${Math.round(hit * 100)}%`;

        return (
            <div className={styles.card}>
                <div className={styles.h3}>{title}</div>

                <div className={styles.kpis}>
                    <div className={styles.kpi}>
                        <div className={styles.k}>{t("usage.total")}</div>
                        <div className={styles.v}>{block.totalCalls ?? 0}</div>
                    </div>

                    <div className={styles.kpi}>
                        <div className={styles.k}>{t("usage.cached")}</div>
                        <div className={styles.v}>{block.cachedCalls ?? 0}</div>
                    </div>

                    <div className={styles.kpi}>
                        <div className={styles.k}>{t("usage.refresh")}</div>
                        <div className={styles.v}>{block.refreshCalls ?? 0}</div>
                    </div>

                    <div className={styles.kpi}>
                        <div className={styles.k}>{t("usage.webOn")}</div>
                        <div className={styles.v}>{block.webOnCalls ?? 0}</div>
                    </div>

                    <div className={styles.kpi}>
                        <div className={styles.k}>{t("usage.cacheHit")}</div>
                        <div className={styles.v}>{hitPct}</div>
                    </div>
                </div>

                <div className={styles.barWrap}>
                    <div className={styles.barLabel}>{t("usage.cacheHitRate")}</div>

                    <div className={styles.barBg}>
                        <div className={styles.barFg} style={{ width: `${Math.round(hit * 100)}%` }} />
                    </div>
                </div>

                <table className={styles.table}>
                    <thead>
                        <tr>
                            <th>{t("usage.endpoint")}</th>
                            <th>{t("usage.total")}</th>
                            <th>{t("usage.cached")}</th>
                            <th>{t("usage.refresh")}</th>
                            <th>{t("usage.web")}</th>
                            <th className={styles.small}>{t("usage.alphaCalls")}</th>
                            <th className={styles.small}>{t("usage.openaiCalls")}</th>
                        </tr>
                    </thead>

                    <tbody>
                        {(block.byEndpoint || []).map((r, i) => (
                            <tr key={i}>
                                <td><b>{r.endpoint}</b></td>
                                <td>{r.total}</td>
                                <td>{r.cached}</td>
                                <td>{r.refresh}</td>
                                <td>{r.web}</td>
                                <td className={styles.small}>{r.alphaCalls}</td>
                                <td className={styles.small}>{r.openaiCalls}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        );
    }

    const chartSeries = useMemo(() => {
        return [
            toSeries(daily, "total", "Total"),
            toSeries(daily, "cached", "Cached"),
            toSeries(daily, "refresh", "Refresh"),
            toSeries(daily, "web", "Web ON"),
        ];
    }, [daily]);

    useEffect(() => {
        load();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [test, days, location.search]);

    return (
        <div className={styles.container}>
            <div className={styles.topbar}>
                <div>
                    <div className={styles.title}>{t("usage.title")}</div>
                    <div className={styles.sub}>{test ? t("usage.testMode") : t("usage.liveMode")}</div>
                </div>

                <div className={styles.topActions}>
                    <select className={styles.select} value={days} onChange={(e) => setDays(Number(e.target.value))} disabled={loading} title={t("usage.period")}>
                        <option value={7}>{t("usage.days7")}</option>
                        <option value={30}>{t("usage.days30")}</option>
                        <option value={90}>{t("usage.days90")}</option>
                        <option value={180}>{t("usage.days180")}</option>
                        <option value={365}>{t("usage.days365")}</option>
                    </select>

                    <button className={styles.btn} onClick={load} disabled={loading}>
                        {loading ? t("loading") : t("usage.reload")}
                    </button>
                </div>
            </div>

            {err && <div className={styles.error}>{err}</div>}

            {/* Data Management */}
            <div className={styles.card}>
                <div className={styles.h3}>{t("usage.dataManagement")}</div>

                <div className={styles.manageRow}>
                    <div className={styles.exportGroup}>
                        <label className={styles.label}>{t("usage.exportFormat")}:</label>
                        <select
                            className={styles.selectSmall}
                            value={exportFormat}
                            onChange={(e) => setExportFormat(e.target.value)}
                        >
                            <option value="json">JSON</option>
                            <option value="csv">CSV</option>
                        </select>

                        <button className={styles.btnPrimary} onClick={exportData} disabled={!data}>
                            {t("usage.exportData")}
                        </button>
                    </div>

                    <button className={styles.btnDanger} onClick={deleteOldData}>
                        {t("usage.deleteOldData")}
                    </button>
                </div>

                <div className={styles.helpText}>
                    {t("usage.exportHelpText")}
                </div>
            </div>

            {!data ? (
                <div className={styles.card}>
                    <div className={styles.small}>{t("usage.noData")}</div>
                </div>
            ) : (
                <>
                <div className={styles.grid2}>
                    {renderBlock(t("usage.today"), data.today)}
                    {renderBlock(t("usage.thisMonth"), data.month)}
                </div>

                <div className={styles.card}>
                    <div className={styles.h3}>{t("usage.dailyTrend")}</div>

                    <div className={styles.small}>
                        {data?.range?.from} ~ {data?.range?.to} ({data?.range?.days} {t("usage.daysLabel")})
                    </div>

                    <div className={styles.chartWrap}>
                        <MultiLineChartCanvas series={chartSeries} height={280} />
                    </div>

                    <div className={styles.metaRow}>
                        <div className={styles.metaPill}>
                            {t("usage.tokensIn")}: <b>{tokensIn}</b>
                        </div>

                        <div className={styles.metaPill}>
                            {t("usage.tokensOut")}: <b>{tokensOut}</b>
                        </div>

                        {estCost != null ? (
                            <div className={styles.metaPill}>
                                {t("usage.estCost")}: <b>{estCost}</b>
                            </div>
                        ) : null}
                    </div>

                    <div className={styles.small}>
                        {t("usage.trendNote")}
                    </div>
                </div>

                <div className={styles.grid2}>
                    <div className={styles.card}>
                        <div className={styles.h3}>{t("usage.topTickers")}</div>

                        <table className={styles.table}>
                            <thead>
                                <tr>
                                    <th>{t("usage.ticker")}</th>
                                    <th>{t("usage.total")}</th>
                                    <th>{t("usage.cached")}</th>
                                    <th className={styles.small}>{t("usage.alphaCalls")}</th>
                                    <th className={styles.small}>{t("usage.openaiCalls")}</th>
                                </tr>
                            </thead>

                            <tbody>
                                {(data.topTickers || []).map((r, i) => (
                                    <tr key={i}>
                                        <td><b>{r.ticker}</b></td>
                                        <td>{r.totalCalls}</td>
                                        <td>{r.cachedCalls}</td>
                                        <td className={styles.small}>{r.alphaCalls}</td>
                                        <td className={styles.small}>{r.openaiCalls}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    <div className={styles.card}>
                        <div className={styles.h3}>{t("usage.topErrors")}</div>

                        {(data.topErrors || []).length === 0 ? (
                            <div className={styles.small}>{t("usage.noErrors")}</div>
                        ) : (
                            <table className={styles.table}>
                                <thead>
                                    <tr>
                                        <th>{t("usage.error")}</th>
                                        <th style={{ width: 90 }}>{t("usage.count")}</th>
                                    </tr>
                                </thead>

                                <tbody>
                                    {(data.topErrors || []).map((r, i) => (
                                        <tr key={i}>
                                            <td className={styles.errCell}>{r.errorText}</td>
                                            <td><b>{r.count}</b></td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        )}

                        <div className={styles.small}>
                            {t("usage.errorGroupingNote")}
                        </div>
                    </div>
                </div>
            </>
        )}
        </div>
    );
}