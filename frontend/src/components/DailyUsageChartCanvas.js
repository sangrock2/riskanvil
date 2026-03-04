import React, { useEffect, useRef, useState, useMemo } from "react";

function clamp(v, a, b) {
    return Math.max(a, Math.min(b, v));
}

export default function DailyUsageChartCanvas({ series = [], height = 300 }) {
    const wrapRef = useRef(null);
    const canvasRef = useRef(null);

    const [hover, setHover] = useState(null); // { idx }

    const dates = useMemo(() => {
        const first = series?.[0]?.points || [];
        return first.map((p) => p.date);
    }, [series]);

    function onMove(e) {
        const canvas = canvasRef.current;
        if (!canvas) return;

        const rect = canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;

        const n = dates.length;
        if (n < 2) return;

        const padL = 54;
        const padR = 14;
        const usable = Math.max(1, rect.width - padL - padR);
        const t = clamp((x - padL) / usable, 0, 1);
        const idx = Math.round(t * (n - 1));

        setHover({ idx });
    }

    useEffect(() => {
        const wrap = wrapRef.current;
        const canvas = canvasRef.current;
        if (!wrap || !canvas) return;

        const ctx = canvas.getContext("2d");
        const dpr = window.devicePixelRatio || 1;

        const w = (canvas.width = Math.floor(wrap.clientWidth * dpr));
        const h = (canvas.height = Math.floor(height * dpr));
        canvas.style.height = `${height}px`;

        ctx.clearRect(0, 0, w, h);

        const padL = 54 * dpr;
        const padR = 14 * dpr;
        const padT = 16 * dpr;
        const padB = 44 * dpr;

        const n = dates.length;
        if (n < 2) return;

        // y range
        const all = [];
        series.forEach((s) => (s.points || []).forEach((p) => {
                const v = Number(p.value);
                if (Number.isFinite(v)) all.push(v);
            })
        );

        const minV = 0;
        const maxV = Math.max(1, ...all);

        const xScale = (i) => padL + (i * (w - padL - padR)) / (n - 1);
        const yScale = (v) => h - padB - ((v - minV) * (h - padT - padB)) / (maxV - minV);

        // grid
        ctx.globalAlpha = 0.25;
        ctx.beginPath();
        const grids = 4;

        for (let g = 0; g <= grids; g++) {
            const vv = (maxV * g) / grids;
            const y = yScale(vv);

            ctx.moveTo(padL, y);
            ctx.lineTo(w - padR, y);
        }

        ctx.stroke();
        ctx.globalAlpha = 1;

        // axes
        ctx.beginPath();
        ctx.moveTo(padL, padT);
        ctx.lineTo(padL, h - padB);
        ctx.lineTo(w - padR, h - padB);
        ctx.stroke();

        // y labels
        ctx.font = `${12 * dpr}px system-ui, -apple-system, Segoe UI, Roboto`;
        ctx.textAlign = "right";
        ctx.textBaseline = "middle";

        for (let g = 0; g <= grids; g++) {
            const vv = Math.round((maxV * g) / grids);
            ctx.fillText(String(vv), padL - 8 * dpr, yScale(vv));
        }

        // x labels (sparse)
        ctx.textAlign = "center";
        ctx.textBaseline = "top";
        const step = Math.max(1, Math.floor(n / 6));

        for (let i = 0; i < n; i += step) {
            const label = dates[i];
            if (!label) continue;
            ctx.fillText(label.slice(5), xScale(i), h - padB + 10 * dpr); // MM-DD
        }

        // lines
        series.forEach((s) => {
            const pts = s.points || [];
            if (pts.length < 2) return;

            ctx.beginPath();

            pts.forEach((p, i) => {
                const v = Number(p.value);
                const x = xScale(i);
                const y = yScale(Number.isFinite(v) ? v : 0);
                if (i === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
            });

            ctx.stroke();
        });

        // hover
        if (hover?.idx != null) {
            const i = clamp(hover.idx, 0, n - 1);
            const x = xScale(i);

            ctx.globalAlpha = 0.35;

            ctx.beginPath();
            ctx.moveTo(x, padT);
            ctx.lineTo(x, h - padB);
            ctx.stroke();

            ctx.globalAlpha = 1;

            // tooltip text
            const title = dates[i] || "";
            const lines = series.map((s) => {
                const v = Number((s.points || [])[i]?.value);
                return `${s.name}: ${Number.isFinite(v) ? v.toFixed(0) : "N/A"}`;
            });

            ctx.font = `${12 * dpr}px system-ui, -apple-system, Segoe UI, Roboto`;

            const text = [title, ...lines];
            let maxW = 0;

            text.forEach((t) => (maxW = Math.max(maxW, ctx.measureText(t).width)));

            const padding = 10 * dpr;
            const lineH = 16 * dpr;
            const boxW = maxW + padding * 2;
            const boxH = text.length * lineH + padding * 2;

            const bx = clamp(x + 12 * dpr, padL, w - padR - boxW);
            const by = clamp(padT + 6 * dpr, padT, h - padB - boxH);

            ctx.globalAlpha = 0.92;
            ctx.fillRect(bx, by, boxW, boxH);
            ctx.globalAlpha = 1;

            ctx.fillStyle = "#fff";
            ctx.textAlign = "left";
            ctx.textBaseline = "top";
            text.forEach((t, k) => ctx.fillText(t, bx + padding, by + padding + k * lineH));

            ctx.fillStyle = "#000";
        }
    }, [series, height, hover, dates]);

    return (
        <div ref={wrapRef} style={{ width: "100%" }}>
            <canvas ref={canvasRef} style={{ width: "100%", height }} onMouseMove={onMove} onMouseLeave={() => setHover(null)} />
        </div>
    );
}