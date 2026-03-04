import React, { useEffect, useRef } from "react";

function drawLine(canvas, points) {
    if (!canvas || !points || points.length < 2) return;

    const ctx = canvas.getContext("2d");
    const dpr = window.devicePixelRatio || 1;
    const w = (canvas.width = canvas.clientWidth * dpr);
    const h = (canvas.height = canvas.clientHeight * dpr);

    // Get theme colors
    const styles = getComputedStyle(document.documentElement);
    const primaryColor = styles.getPropertyValue('--color-primary').trim() || '#3b82f6';
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark';

    ctx.clearRect(0, 0, w, h);

    const values = points.map(p => p.close);
    const minV = Math.min(...values);
    const maxV = Math.max(...values);
    const pad = 20 * dpr;

    const xScale = (i) => pad + (i * (w - 2 * pad)) / (points.length - 1);
    const yScale = (v) => {
        if (maxV === minV) return h / 2;
        return h - pad - ((v - minV) * (h - 2 * pad)) / (maxV - minV);
    };

    // Use primary color for better visibility
    ctx.strokeStyle = primaryColor;
    ctx.lineWidth = 2.5 * dpr;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    // Add subtle shadow
    ctx.shadowColor = primaryColor;
    ctx.shadowBlur = isDark ? 3 * dpr : 1.5 * dpr;

    ctx.beginPath();

    points.forEach((p, i) => {
        const x = xScale(i);
        const y = yScale(p.close);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
    });

    ctx.stroke();
}

export default function PriceChart({ points }) {
    const ref = useRef(null);
    useEffect(() => drawLine(ref.current, points), [points]);

    return (
        <canvas
            ref={ref}
            style={{
                width: "100%",
                height: 220,
                border: "1px solid var(--color-border)",
                borderRadius: 8,
                background: "var(--color-bg)",
                cursor: "crosshair",
            }}
        />
    );
}