import React, { useRef, useEffect, useState, useMemo } from "react";
import styles from "../css/MultiLineChartCanvas.module.css";

function clamp(x, a, b) {
    return Math.max(a, Math.min(b, x));
}

function hash(s) {
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
    return h;
}

export default function MultiLineChartCanvas({ series = [], height = 280, showLegend = true,}) {
    const canvasRef = useRef(null);
    const wrapRef = useRef(null);
    const [hover, setHover] = useState(null); // {x, y, idx, date, items:[{name,value}]}

    // Color palette - works well in both light and dark modes
    const palette = ["#3b82f6", "#22c55e", "#f59e0b", "#a855f7", "#ef4444", "#06b6d4"];

    function onMove(e) {
        const canvas = canvasRef.current;
        if (!canvas) return;

        const rect = canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;

        const dates = prepared.dates;
        if (!dates || dates.length < 2) return;

        const padL = 52;
        const padR = 18;

        const usableW = rect.width - padL - padR;
        if (usableW <= 10) return;

        // x -> idx
        const rel = (x - padL) / usableW;
        const i = clamp(Math.round(rel * (dates.length - 1)), 0, dates.length - 1);

        const date = dates[i];
        const items = prepared.series.map((s) => ({
            name: s.name,
            color: s.color,
            value: s.points[i]?.value,
        }));

        setHover({
            idx: i,
            date,
            x: e.clientX - rect.left,
            y: e.clientY - rect.top,
            items,
        });
    }

    function onLeave() {
        setHover(null);
    }

    const prepared = useMemo(() => {
        const clean = (series || []).filter((s) => s && s.name && Array.isArray(s.points) && s.points.length >= 2).map((s) => ({
            name: s.name,
            color: s.color || palette[Math.abs(hash(s.name)) % palette.length],
            points: s.points.filter((p) => p && p.date && Number.isFinite(Number(p.value))).map((p) => ({
                date: p.date, value: Number(p.value) 
            })),
        })).filter((s) => s.points.length >= 2);

        if (clean.length === 0) return { series: [], dates: [], min: 0, max: 0 };

        // 공통 날짜(intersection)로 정렬/정합
        const dateSets = clean.map((s) => new Set(s.points.map((p) => p.date)));
        let common = [...dateSets[0]];

        for (let i = 1; i < dateSets.length; i++) {
            const set = dateSets[i];
            common = common.filter((d) => set.has(d));
        }

        common.sort();

        // 공통이 너무 적으면(휴장/데이터 차이) -> "가장 짧은 길이 기준 인덱스 정렬"
        if (common.length < 10) {
            const minLen = Math.min(...clean.map((s) => s.points.length));
            const dates = clean[0].points.slice(-minLen).map((p) => p.date);
            const aligned = clean.map((s) => ({
                ...s,
                points: s.points.slice(-minLen).map((p, idx) => ({ date: dates[idx], value: p.value })),
            }));
            
            const vals = aligned.flatMap((s) => s.points.map((p) => p.value));

            return { series: aligned, dates, min: Math.min(...vals), max: Math.max(...vals) };
        }

        const aligned = clean.map((s) => {
            const map = new Map(s.points.map((p) => [p.date, p.value]));

            return {
                ...s,
                points: common.map((d) => ({ date: d, value: map.get(d) })),
            };
        });

        const vals = aligned.flatMap((s) => s.points.map((p) => p.value));

        return { series: aligned, dates: common, min: Math.min(...vals), max: Math.max(...vals) };
    }, [series]);

    useEffect(() => {
        const canvas = canvasRef.current;

        if (!canvas) return;

        const ctx = canvas.getContext("2d");
        const dpr = window.devicePixelRatio || 1;

        const resizeAndDraw = () => {
            const cssW = canvas.clientWidth;
            const cssH = height;

            canvas.width = Math.floor(cssW * dpr);
            canvas.height = Math.floor(cssH * dpr);
            canvas.style.height = `${cssH}px`;

            const w = canvas.width;
            const h = canvas.height;

            // Get theme colors
            const styles = getComputedStyle(document.documentElement);
            const textColor = styles.getPropertyValue('--color-text').trim() || '#111827';
            const textSecondary = styles.getPropertyValue('--color-text-secondary').trim() || '#6b7280';
            const borderColor = styles.getPropertyValue('--color-border').trim() || '#e5e7eb';
            const isDark = document.documentElement.getAttribute('data-theme') === 'dark';

            ctx.clearRect(0, 0, w, h);
            ctx.lineWidth = 2 * dpr;
            ctx.font = `${12 * dpr}px system-ui, -apple-system, Segoe UI, Roboto`;

            const { series: S, dates, min, max } = prepared;

            if (!S.length || dates.length < 2) {
                ctx.fillStyle = textSecondary;
                ctx.font = `${14 * dpr}px system-ui, -apple-system, Segoe UI, Roboto`;
                ctx.fillText("No chart data", 12 * dpr, 18 * dpr);
                return;
            }

            const padL = 52 * dpr;
            const padR = 18 * dpr;
            const padT = 14 * dpr;
            const padB = 34 * dpr;

            const xScale = (i) => padL + (i * (w - padL - padR)) / (dates.length - 1);
            const yScale = (v) => {
                if (max === min) return (h - padB + padT) / 2;
                return h - padB - ((v - min) * (h - padT - padB)) / (max - min);
            };

            // grid + y labels (3개)
            const gridOpacity = isDark ? 0.15 : 0.08;
            ctx.strokeStyle = `rgba(${isDark ? '255,255,255' : '0,0,0'},${gridOpacity})`;
            ctx.lineWidth = 1 * dpr;

            for (let t = 0; t <= 2; t++) {
                const yv = min + ((max - min) * t) / 2;
                const y = yScale(yv);

                ctx.beginPath();
                ctx.moveTo(padL, y);
                ctx.lineTo(w - padR, y);
                ctx.stroke();

                ctx.fillStyle = textSecondary;
                ctx.textAlign = "right";
                ctx.textBaseline = "middle";
                ctx.fillText(yv.toFixed(1), padL - 10 * dpr, y);
            }

            // x labels (start/mid/end)
            const idxs = [0, Math.floor((dates.length - 1) / 2), dates.length - 1];

            ctx.fillStyle = textSecondary;
            ctx.textAlign = "center";
            ctx.textBaseline = "top";

            idxs.forEach((i) => {
                const x = xScale(i);
                const label = dates[i];
                ctx.fillText(label, x, h - padB + 10 * dpr);
            });

            // lines
            S.forEach((s) => {
                ctx.strokeStyle = s.color;
                ctx.lineWidth = 2.5 * dpr;
                ctx.lineCap = 'round';
                ctx.lineJoin = 'round';

                // Add subtle shadow for better visibility
                ctx.shadowColor = s.color;
                ctx.shadowBlur = isDark ? 3 * dpr : 1.5 * dpr;

                ctx.beginPath();

                s.points.forEach((p, i) => {
                    const x = xScale(i);
                    const y = yScale(p.value);

                    if (i === 0) ctx.moveTo(x, y);
                    else ctx.lineTo(x, y);
                });

                ctx.stroke();

                // Reset shadow
                ctx.shadowColor = 'transparent';
                ctx.shadowBlur = 0;
            });

            // hover crosshair
            if (hover?.idx != null) {
                const i = hover.idx;
                const x = xScale(i);

                const hoverOpacity = isDark ? 0.35 : 0.25;
                ctx.strokeStyle = `rgba(${isDark ? '255,255,255' : '0,0,0'},${hoverOpacity})`;
                ctx.lineWidth = 1.5 * dpr;
                ctx.setLineDash([5 * dpr, 5 * dpr]);

                ctx.beginPath();
                ctx.moveTo(x, padT);
                ctx.lineTo(x, h - padB);
                ctx.stroke();
                ctx.setLineDash([]);

                // points dot with ring
                prepared.series.forEach((s) => {
                    const y = yScale(s.points[i].value);

                    // Outer ring
                    ctx.strokeStyle = s.color;
                    ctx.fillStyle = isDark ? '#111827' : '#ffffff';
                    ctx.lineWidth = 2 * dpr;
                    ctx.beginPath();
                    ctx.arc(x, y, 4.5 * dpr, 0, Math.PI * 2);
                    ctx.fill();
                    ctx.stroke();

                    // Inner dot
                    ctx.fillStyle = s.color;
                    ctx.beginPath();
                    ctx.arc(x, y, 2.5 * dpr, 0, Math.PI * 2);
                    ctx.fill();
                });
            }
        };

        resizeAndDraw();
        const ro = new ResizeObserver(resizeAndDraw);
        ro.observe(canvas);

        window.addEventListener("resize", resizeAndDraw);

        return () => {
            ro.disconnect();
            window.removeEventListener("resize", resizeAndDraw);
        };
    }, [prepared, hover, height]);

    return (
        <div ref={wrapRef} className={styles.wrap}>
            {showLegend && prepared.series.length > 0 && (
                <div className={styles.legend}>
                    {prepared.series.map((s) => (
                        <div key={s.name} className={styles.legendItem}>
                            <span className={styles.legendDot} style={{ background: s.color }} />
                            <span>{s.name}</span>
                        </div>
                    ))}
                </div>
            )}

            <canvas
                ref={canvasRef}
                className={styles.canvas}
                style={{ height }}
                onMouseMove={onMove}
                onMouseLeave={onLeave}
            />

            {hover && (
                <div
                    className={styles.tooltip}
                    style={{
                        left: clamp(hover.x + 12, 12, 9999),
                        top: clamp(hover.y + 12, 12, 9999),
                    }}
                >
                    <div className={styles.tooltipDate}>{hover.date}</div>

                    {hover.items.map((it) => (
                        <div key={it.name} className={styles.tooltipRow}>
                            <span className={styles.tooltipName} style={{ color: it.color }}>
                                {it.name}
                            </span>
                            <span className={styles.tooltipValue}>
                                {Number.isFinite(it.value) ? it.value.toFixed(2) : "N/A"}
                            </span>
                        </div>
                    ))}

                    <div className={styles.tooltipNote}>정규화 지수(시작=100)</div>
                </div>
            )}
        </div>
    );
}