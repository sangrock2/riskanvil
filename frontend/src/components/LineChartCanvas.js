import { useEffect, useMemo, useRef, useState, memo, useCallback } from "react";
import { throttle } from "../utils/debounce";
import styles from "../css/LineChartCanvas.module.css";

function clamp(n, a, b) {
    return Math.max(a, Math.min(b, n));
}

function safeNum(x) {
    const v = Number(x);
    return Number.isFinite(v) ? v : null;
}

const LineChartCanvas = memo(function LineChartCanvas({ points = [], valueKey = "close", labelKey = "date", height = 220, yTicks = 5, xTicks = 5,}) {
    const wrapRef = useRef(null);
    const canvasRef = useRef(null);
    const resizeObserverRef = useRef(null);

    const [hover, setHover] = useState(null);
    const [needsRedraw, setNeedsRedraw] = useState(0);

    const data = useMemo(() => {
        const pts = Array.isArray(points) ? points : [];
        const values = pts.map((p) => safeNum(p?.[valueKey])).filter((v) => v !== null);
        const minV = values.length ? Math.min(...values) : 0;
        const maxV = values.length ? Math.max(...values) : 1;

        return { pts, minV, maxV };
    }, [points, valueKey]);

    useEffect(() => {
        const canvas = canvasRef.current;
        const wrap = wrapRef.current;

        if (!canvas || !wrap) return;

        const { pts, minV, maxV } = data;
        const ctx = canvas.getContext("2d");
        const dpr = window.devicePixelRatio || 1;

        const cssW = wrap.clientWidth;
        const cssH = height;

        canvas.width = Math.max(1, Math.floor(cssW * dpr));
        canvas.height = Math.max(1, Math.floor(cssH * dpr));
        canvas.style.height = `${cssH}px`;

        const w = canvas.width;
        const h = canvas.height;

        // Get theme colors from CSS variables
        const styles = getComputedStyle(document.documentElement);
        const textColor = styles.getPropertyValue('--color-text').trim() || '#111827';
        const textSecondary = styles.getPropertyValue('--color-text-secondary').trim() || '#6b7280';
        const primaryColor = styles.getPropertyValue('--color-primary').trim() || '#3b82f6';
        const isDark = document.documentElement.getAttribute('data-theme') === 'dark';

        // Adjust opacity for dark mode
        const gridOpacity = isDark ? 0.15 : 0.08;
        const hoverOpacity = isDark ? 0.35 : 0.25;

        // padding (dpr 반영)
        const padL = 56 * dpr;
        const padR = 16 * dpr;
        const padT = 14 * dpr;
        const padB = 32 * dpr;

        ctx.clearRect(0, 0, w, h);

        // font
        ctx.font = `${12 * dpr}px system-ui, -apple-system, Segoe UI, Roboto, sans-serif`;
        ctx.fillStyle = textColor;
        ctx.strokeStyle = textColor;
        ctx.lineWidth = 1 * dpr;

        // 축
        const x0 = padL;
        const y0 = h - padB;
        const x1 = w - padR;
        const y1 = padT;

        // axis lines
        ctx.beginPath();
        ctx.moveTo(x0, y1);
        ctx.lineTo(x0, y0);
        ctx.lineTo(x1, y0);
        ctx.stroke();

        if (!pts || pts.length < 2) {
            ctx.fillStyle = textSecondary;
            ctx.font = `${14 * dpr}px system-ui, -apple-system, Segoe UI, Roboto, sans-serif`;
            ctx.fillText("No data available", x0 + 10 * dpr, y1 + 20 * dpr);
            return;
        }

        const range = maxV - minV || 1;

        const xScale = (i) => {
            const n = pts.length - 1;
            return x0 + (i * (x1 - x0)) / Math.max(1, n);
        };

        const yScale = (v) => {
            const t = (v - minV) / range;
            return y0 - t * (y0 - y1);
        };

        // y ticks
        const yt = Math.max(2, yTicks);
        for (let k = 0; k < yt; k++) {
            const t = k / (yt - 1);
            const v = maxV - t * range;
            const y = yScale(v);

            // grid
            ctx.strokeStyle = `rgba(${isDark ? '255,255,255' : '0,0,0'},${gridOpacity})`;
            ctx.beginPath();
            ctx.moveTo(x0, y);
            ctx.lineTo(x1, y);
            ctx.stroke();

            // label
            ctx.fillStyle = textSecondary;
            ctx.strokeStyle = textColor;
            const label = `${v.toFixed(2)}`;
            ctx.fillText(label, 6 * dpr, y + 4 * dpr);
        }

        // x ticks (date labels)
        const xt = Math.max(2, xTicks);
        for (let k = 0; k < xt; k++) {
            const i = Math.round((k * (pts.length - 1)) / (xt - 1));
            const x = xScale(i);
            const raw = pts[i]?.[labelKey];
            const label = typeof raw === "string" ? raw : "";

            ctx.strokeStyle = `rgba(${isDark ? '255,255,255' : '0,0,0'},${gridOpacity * 1.5})`;
            ctx.beginPath();
            ctx.moveTo(x, y0);
            ctx.lineTo(x, y0 + 6 * dpr);
            ctx.stroke();

            ctx.fillStyle = textSecondary;
            // 너무 길면 잘라서 표시
            const short = label.length > 10 ? label.slice(5) : label; // YYYY-MM-DD -> MM-DD
            const tw = ctx.measureText(short).width;
            ctx.fillText(short, x - tw / 2, h - 10 * dpr);
        }

        // line - use primary color for better visibility in dark mode
        ctx.strokeStyle = primaryColor;
        ctx.lineWidth = 2.5 * dpr;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';

        // Add subtle shadow for depth
        ctx.shadowColor = primaryColor;
        ctx.shadowBlur = isDark ? 4 * dpr : 2 * dpr;
        ctx.shadowOffsetX = 0;
        ctx.shadowOffsetY = 0;

        ctx.beginPath();

        pts.forEach((p, i) => {
            const v = safeNum(p?.[valueKey]);
            if (v === null) return;
            const x = xScale(i);
            const y = yScale(v);
            if (i === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
        });

        ctx.stroke();

        // Reset shadow
        ctx.shadowColor = 'transparent';
        ctx.shadowBlur = 0;

        // hover marker
        if (hover && Number.isFinite(hover.idx)) {
            const i = clamp(hover.idx, 0, pts.length - 1);
            const v = safeNum(pts[i]?.[valueKey]);

            if (v !== null) {
                const x = xScale(i);
                const y = yScale(v);

                // vertical line
                ctx.strokeStyle = `rgba(${isDark ? '255,255,255' : '0,0,0'},${hoverOpacity})`;
                ctx.lineWidth = 1.5 * dpr;
                ctx.setLineDash([5 * dpr, 5 * dpr]);
                ctx.beginPath();
                ctx.moveTo(x, y1);
                ctx.lineTo(x, y0);
                ctx.stroke();
                ctx.setLineDash([]);

                // point - outer ring
                ctx.strokeStyle = primaryColor;
                ctx.fillStyle = isDark ? '#111827' : '#ffffff';
                ctx.lineWidth = 2.5 * dpr;
                ctx.beginPath();
                ctx.arc(x, y, 5 * dpr, 0, Math.PI * 2);
                ctx.fill();
                ctx.stroke();

                // point - inner dot
                ctx.fillStyle = primaryColor;
                ctx.beginPath();
                ctx.arc(x, y, 2.5 * dpr, 0, Math.PI * 2);
                ctx.fill();
            }
        }
    }, [data, height, hover, yTicks, xTicks, labelKey, valueKey, needsRedraw]);

    // Throttled resize handler
    useEffect(() => {
        const wrap = wrapRef.current;
        if (!wrap) return;

        const handleResize = throttle(() => {
            setNeedsRedraw((prev) => prev + 1);
        }, 150);

        resizeObserverRef.current = new ResizeObserver(handleResize);
        resizeObserverRef.current.observe(wrap);

        return () => {
            if (resizeObserverRef.current) {
                resizeObserverRef.current.disconnect();
            }
        };
    }, []);

    const onMove = useCallback((e) => {
        const wrap = wrapRef.current;
        const canvas = canvasRef.current;

        if (!wrap || !canvas) return;

        const rect = wrap.getBoundingClientRect();
        const xCss = e.clientX - rect.left;
        const yCss = e.clientY - rect.top;

        const pts = data.pts;

        if (!pts || pts.length < 2) return;

        // padding과 동일한 값(css 기준)
        const padL = 56;
        const padR = 16;

        const x0 = padL;
        const x1 = rect.width - padR;

        const x = clamp(xCss, x0, x1);
        const t = (x - x0) / Math.max(1, x1 - x0);
        const idx = Math.round(t * (pts.length - 1));

        setHover({ idx, xCss, yCss });
    }, [data]);

    const onLeave = useCallback(() => {
        setHover(null);
    }, []);

    const hoverInfo = useMemo(() => {
        if (!hover) return null;

        const pts = data.pts;

        if (!pts?.length) return null;

        const i = clamp(hover.idx, 0, pts.length - 1);
        const p = pts[i];
        const date = p?.[labelKey];
        const v = safeNum(p?.[valueKey]);

        return { i, date, v };
    }, [hover, data, labelKey, valueKey]);

    return (
        <div ref={wrapRef} className={styles.wrap} style={{ height }}>
            <canvas ref={canvasRef} className={styles.canvas} onMouseMove={onMove} onMouseLeave={onLeave} />

            {hoverInfo && hoverInfo.v !== null && (
                <div className={styles.tooltip} style={{ left: clamp(hover.xCss + 12, 8, (wrapRef.current?.clientWidth || 0) - 160), top: clamp(hover.yCss - 12, 8, height - 60), }}>
                    <div className={styles.tipTitle}>{String(hoverInfo.date ?? "")}</div>
                    <div className={styles.tipValue}>{hoverInfo.v.toFixed(2)}</div>
                </div>
            )}
        </div>
    );
});

export default LineChartCanvas;