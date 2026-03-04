import React, { useRef, useEffect } from "react";

export default function EquityCurveChart() {
    const ref = useRef(null);

    useEffect(() => {
        const canvas = ref.current;

        if (!canvas) return;

        const ctx = canvas.getContext("2d");

        ctx.clearRect(0, 0, width, height);

        if (!points || points.length < 2) {
            ctx.fillText("No data", 10, 20);
            return;
        }

        const pad = 28;
        const xs = points.map((_, i) => i);
        const ys = points.map(p => p.equity);

        const minY = Math.min(...ys);
        const maxY = Math.max(...ys);

        const xScale = (i) => pad + (i / (points.length - 1)) * (width - pad * 2);
        const yScale = (v) => {
            if (maxY === minY) return height - pad;

            return height - pad - ((v - minY) / (maxY - minY)) * (height - pad * 2);
        };

        // axis
        ctx.beginPath();
        ctx.moveTo(pad, pad);
        ctx.lineTo(pad, height - pad);
        ctx.lineTo(width - pad, height - pad);
        ctx.stroke();

        // line
        ctx.beginPath();
        ctx.moveTo(xScale(0), yScale(ys[0]));

        for (let i = 1; i < points.length; i++) {
            ctx.lineTo(xScale(i), yScale(ys[i]));
        }
        
        ctx.stroke();

        // labels (min/max)
        ctx.fillText(`min: ${minY.toFixed(0)}`, pad, pad - 8);
        ctx.fillText(`max: ${maxY.toFixed(0)}`, pad + 120, pad - 8);
    }, [points, width, height]);

    return <canvas ref={ref} width={width} height={height} style={{ border: "1px solid #ddd", borderRadius: 8 }} />;
}