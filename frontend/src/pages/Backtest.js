import { useEffect, useRef, useState } from "react";
import { useTranslation } from "../hooks/useTranslation";
import { apiFetch } from "../api/http";
import { useBacktestHistory, useBacktestDetail, useBacktestMutation } from "../hooks/queries";
import styles from "../css/Backtest.module.css";

function drawEquity(canvas, curve, benchmarkCurve) {
    if (!canvas || !curve || curve.length < 2) return;

    const dpr = window.devicePixelRatio || 1;
    const ctx = canvas.getContext("2d");

    const cssW = canvas.clientWidth;
    const cssH = canvas.clientHeight;

    canvas.width = Math.max(1, Math.floor(cssW * dpr));
    canvas.height = Math.max(1, Math.floor(cssH * dpr));

    const w = canvas.width;
    const h = canvas.height;

    ctx.clearRect(0, 0, w, h);

    const allCurves = [curve, benchmarkCurve].filter(Boolean);
    const allValues = allCurves.flatMap((c) => c.map((p) => Number(p.equity))).filter((v) => Number.isFinite(v));
    if (allValues.length < 2) return;

    const minV = Math.min(...allValues);
    const maxV = Math.max(...allValues);

    const pad = 20 * dpr;

    const yScale = (v) => {
        if (maxV === minV) return h / 2;
        return h - pad - ((v - minV) * (h - 2 * pad)) / (maxV - minV);
    };

    const drawLine = (c, color) => {
        const xScale = (i) => pad + (i * (w - 2 * pad)) / (c.length - 1);
        ctx.strokeStyle = color;
        ctx.lineWidth = 2 * dpr;
        ctx.beginPath();
        c.forEach((p, i) => {
            const x = xScale(i);
            const y = yScale(Number(p.equity));
            if (i === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
        });
        ctx.stroke();
    };

    if (benchmarkCurve && benchmarkCurve.length >= 2) {
        drawLine(benchmarkCurve, "#888888");
    }
    drawLine(curve, "#4a9eff");
}

function normalizeHistoryResponse(res, fallbackPage, fallbackSize) {
    if (Array.isArray(res)) {
        return { items: res, page: null };
    }

    if (res && Array.isArray(res.items)) {
        return { items: res.items, page: res.page ?? null };
    }

    if (res && Array.isArray(res.content)) {
        const page = {
            page: res.number ?? fallbackPage,
            size: res.size ?? fallbackSize,
            totalElements: res.totalElements ?? null,
            totalPages: res.totalPages ?? null,
            hasNext: typeof res.last === "boolean" ? !res.last : null,
        };

        return { items: res.content, page };
    }

    return { items: [], page: null };
}


export default function Backtest() {
    const { t } = useTranslation();
    const [ticker, setTicker] = useState("AAPL");
    const [market, setMarket] = useState("US");
    const [strategy, setStrategy] = useState("SMA_CROSS");
    const [start, setStart] = useState("");
    const [end, setEnd] = useState("");
    const [initialCapital, setInitialCapital] = useState(1000000);
    const [feeBps, setFeeBps] = useState(5);

    const [err, setErr] = useState("");
    const [run, setRun] = useState(null);
    const [selectedId, setSelectedId] = useState(null);

    const [page, setPage] = useState(0);
    const [size, setSize] = useState(20);
    const [sort, setSort] = useState("createdAt,desc");

    const [filterTicker, setFilterTicker] = useState("");
    const [filterStrategy, setFilterStrategy] = useState("");

    const canvasRef = useRef(null);

    // React Query hooks
    const filters = { ticker: filterTicker.trim(), strategy: filterStrategy.trim() };
    const { data: historyData } = useBacktestHistory(page, size, sort, filters);
    const { data: detail } = useBacktestDetail(selectedId);
    const backtestMutation = useBacktestMutation();

    const normalizedHistory = historyData ? normalizeHistoryResponse(historyData, page, size) : { items: [], page: null };
    const history = normalizedHistory.items;
    const pageMeta = normalizedHistory.page;

    const activeResult = run?.result ?? detail;
    const summary = activeResult?.summary;
    const curve = activeResult?.equityCurve;
    const benchmarkCurve = activeResult?.benchmarkCurve;

    const hasNext = pageMeta?.hasNext ?? (typeof pageMeta?.totalPages === "number" ? page + 1 < pageMeta.totalPages : null);

    const submit = async () => {
        setErr("");
        setRun(null);
        setSelectedId(null);

        try {
            const data = await backtestMutation.mutateAsync({
                ticker: ticker.trim(),
                market,
                strategy,
                start: start || null,
                end: end || null,
                initialCapital: Number(initialCapital),
                feeBps: Number(feeBps),
            });
            setRun(data);
            setPage(0);
        } catch (e) {
            setErr(e.message);
        }
    };

    const openDetail = (id) => {
        setErr("");
        setSelectedId(id);
    };

    useEffect(() => {
        drawEquity(canvasRef.current, curve, benchmarkCurve);
    }, [run, detail]);

    return (
        <div className={styles.container}>
            <h2>{t('backtest.title')}</h2>

            <div className={styles.card}>
                <div className={styles.row}>
                    <input className={styles.input} value={ticker} onChange={(e) => setTicker(e.target.value)} />

                    <select className={styles.input} value={market} onChange={(e) => setMarket(e.target.value)}>
                        <option value="US">US</option>
                        <option value="KR">KR</option>
                        <option value="CRYPTO">CRYPTO</option>
                    </select>

                    <select className={styles.input} value={strategy} onChange={(e) => setStrategy(e.target.value)}>
                        <option value="SMA_CROSS">SMA Cross (20/60)</option>
                        <option value="RSI_REVERSAL">RSI Reversal (14)</option>
                        <option value="MACD_CROSS">MACD Cross (12/26/9)</option>
                        <option value="BOLLINGER_BAND">Bollinger Band (20/2σ)</option>
                    </select>

                    <input className={styles.input} type="date" value={start} onChange={(e) => setStart(e.target.value)} />
                    <input className={styles.input} type="date" value={end} onChange={(e) => setEnd(e.target.value)} />
                    <input className={styles.input} type="number" value={initialCapital} onChange={(e) => setInitialCapital(e.target.value)} />
                    <input className={styles.input} type="number" value={feeBps} onChange={(e) => setFeeBps(e.target.value)} />

                    <button className={styles.button} onClick={submit}>{t('backtest.run')}</button>
                </div>

                {err && <div className={styles.error}>{err}</div>}

                <canvas ref={canvasRef} />

                {summary && (
                    <div className={styles.summary}>
                        <div className={styles.benchmarkLegend}>
                            <span className={styles.legendStrategy}>— {t('backtest.strategy')}</span>
                            {summary.benchmark && <span className={styles.legendBenchmark}>— {t('backtest.buyAndHold')}</span>}
                        </div>
                        <table className={styles.compareTable}>
                            <thead>
                                <tr>
                                    <th></th>
                                    <th>{t('backtest.strategy')}</th>
                                    {summary.benchmark && <th>{t('backtest.buyAndHold')}</th>}
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td><b>{t('backtest.totalReturn')}</b></td>
                                    <td>{typeof summary.totalReturn === "number" ? (summary.totalReturn * 100).toFixed(2) + "%" : summary.totalReturn}</td>
                                    {summary.benchmark && <td>{typeof summary.benchmark.totalReturn === "number" ? (summary.benchmark.totalReturn * 100).toFixed(2) + "%" : "-"}</td>}
                                </tr>
                                <tr>
                                    <td><b>{t('backtest.cagr')}</b></td>
                                    <td>{typeof summary.cagr === "number" ? (summary.cagr * 100).toFixed(2) + "%" : summary.cagr}</td>
                                    {summary.benchmark && <td>{typeof summary.benchmark.cagr === "number" ? (summary.benchmark.cagr * 100).toFixed(2) + "%" : "-"}</td>}
                                </tr>
                                <tr>
                                    <td><b>{t('backtest.maxDrawdown')}</b></td>
                                    <td>{typeof summary.maxDrawdown === "number" ? (summary.maxDrawdown * 100).toFixed(2) + "%" : summary.maxDrawdown}</td>
                                    {summary.benchmark && <td>{typeof summary.benchmark.maxDrawdown === "number" ? (summary.benchmark.maxDrawdown * 100).toFixed(2) + "%" : "-"}</td>}
                                </tr>
                                <tr>
                                    <td><b>{t('backtest.sharpeRatio')}</b></td>
                                    <td>{typeof summary.sharpe === "number" ? summary.sharpe.toFixed(3) : summary.sharpe}</td>
                                    {summary.benchmark && <td>{typeof summary.benchmark.sharpe === "number" ? summary.benchmark.sharpe.toFixed(3) : "-"}</td>}
                                </tr>
                                <tr>
                                    <td><b>{t('backtest.numTrades')}</b></td>
                                    <td>{summary.numTrades}</td>
                                    {summary.benchmark && <td>—</td>}
                                </tr>
                            </tbody>
                        </table>
                        <div className={styles.small}>{summary.note}</div>
                    </div>
                )}

            </div>

            <div className={`${styles.grid} ${styles.gridSection}`}>
                <div className={styles.card}>
                    <h3>{t('backtest.history')}</h3>

                    {/* 필터/정렬/페이지 */}
                    <div className={`${styles.row} ${styles.filterRow}`}>
                        <input className={styles.input} placeholder={t('backtest.filterTicker')} value={filterTicker} onChange={(e) => setFilterTicker(e.target.value)}/>

                        <select className={styles.input} value={filterStrategy} onChange={(e) => setFilterStrategy(e.target.value)}>
                            <option value="">{t('backtest.allStrategies')}</option>
                            <option value="SMA_CROSS">SMA Cross</option>
                            <option value="RSI_REVERSAL">RSI Reversal</option>
                            <option value="MACD_CROSS">MACD Cross</option>
                            <option value="BOLLINGER_BAND">Bollinger Band</option>
                        </select>

                        <select className={styles.input} value={sort} onChange={(e) => setSort(e.target.value)}>
                            <option value="createdAt,desc">{t('backtest.sortCreatedDesc')}</option>
                            <option value="createdAt,asc">{t('backtest.sortCreatedAsc')}</option>
                            <option value="totalReturn,desc">{t('backtest.sortReturnDesc')}</option>
                            <option value="maxDrawdown,asc">{t('backtest.sortDrawdownAsc')}</option>
                        </select>

                        <select className={styles.input} value={size} onChange={(e) => setSize(Number(e.target.value))}>
                            <option value={10}>10</option>
                            <option value={20}>20</option>
                            <option value={50}>50</option>
                        </select>

                        <button className={styles.button} onClick={() => setPage(0)}>{t('backtest.apply')}</button>
                    </div>

                    {/* 페이징 버튼 */}
                    <div className={`${styles.row} ${styles.paginationRow}`}>
                        <button className={styles.button} disabled={page <= 0} onClick={() => setPage(p => p - 1)}>{t('backtest.prev')}</button>
                        <button className={styles.button} disabled={hasNext === false} onClick={() => setPage(p => p + 1)}>{t('backtest.next')}</button>

                        <div className={styles.small}>
                            {t('backtest.page')}: {page}
                            {pageMeta?.totalPages != null ? ` / ${pageMeta.totalPages - 1}` : ""}
                            {pageMeta?.totalElements != null ? ` (${t('backtest.total')}: ${pageMeta.totalElements})` : ""}
                        </div>
                    </div>

                    {history.map(h => (
                        <div
                            key={h.id}
                            className={selectedId === h.id ? styles.itemSelected : styles.item}
                            onClick={() => openDetail(h.id)}
                        >
                            <div>
                                <b>{h.ticker}</b> ({h.market}) {h.strategy} — TR: {h.totalReturn ?? t('messages.noData')} MDD: {h.maxDrawdown ?? t('messages.noData')}
                            </div>

                            <div className={styles.small}>{h.createdAt}</div>
                        </div>
                    ))}
                </div>

                <div className={styles.card}>
                    <h3>{t('backtest.detail')}</h3>

                    {!detail ? (
                        <div className={styles.small}>{t('backtest.selectHistory')}</div>
                    ) : (
                        <>
                        <div className={styles.small}>{t('backtest.selectedId')}: {selectedId}</div>

                        <div className={styles.detailHeader}>
                            <b>{detail.summary?.ticker}</b> ({detail.summary?.market}) {detail.summary?.strategy}
                        </div>

                        <div className={styles.detailBody}>
                            <div className={styles.summaryRow}><b>{t('backtest.totalReturn')}</b>: {detail.summary?.totalReturn}</div>
                            <div className={styles.summaryRow}><b>{t('backtest.cagr')}</b>: {detail.summary?.cagr}</div>
                            <div className={styles.summaryRow}><b>{t('backtest.maxDrawdown')}</b>: {detail.summary?.maxDrawdown}</div>
                            <div className={styles.summaryRow}><b>{t('backtest.sharpeRatio')}</b>: {detail.summary?.sharpe}</div>
                            <div className={styles.summaryRow}><b>{t('backtest.numTrades')}</b>: {detail.summary?.numTrades}</div>
                            <div className={styles.small}>{detail.summary?.note}</div>
                        </div>

                        </>
                    )}
                </div>
            </div>
        </div>
    );
}
