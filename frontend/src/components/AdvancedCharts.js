import { useMemo } from "react";
import {
  ComposedChart,
  Bar,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  Cell,
} from "recharts";
import styles from "../css/AdvancedCharts.module.css";

// Candlestick Chart using Recharts
export function CandlestickChart({ ohlcData }) {
  const data = useMemo(() => {
    if (!Array.isArray(ohlcData) || ohlcData.length === 0) return [];

    return ohlcData.map(item => ({
      date: item.date,
      open: Number(item.open || 0),
      high: Number(item.high || 0),
      low: Number(item.low || 0),
      close: Number(item.close || 0),
      volume: Number(item.volume || 0),
      // Candlestick body
      body: [
        Math.min(item.open, item.close),
        Math.max(item.open, item.close),
      ],
      // Wick (high-low range)
      wick: [item.low, item.high],
      // Color based on direction
      isUp: item.close >= item.open,
    }));
  }, [ohlcData]);

  if (data.length === 0) return null;

  const CustomCandlestick = (props) => {
    const { x, y, width, payload } = props;

    if (!payload) return null;

    const isUp = payload.isUp;
    const color = isUp ? "#10b981" : "#ef4444";
    const bodyColor = isUp ? "#10b981" : "#ef4444";
    const wickColor = "#6b7280";

    // Calculate positions
    const bodyHeight = Math.abs(
      ((payload.close - payload.open) / (payload.high - payload.low)) * 100
    );
    const wickX = x + width / 2;

    return (
      <g>
        {/* Wick line */}
        <line
          x1={wickX}
          y1={y}
          x2={wickX}
          y2={y + 100}
          stroke={wickColor}
          strokeWidth={1}
        />

        {/* Body rectangle */}
        <rect
          x={x}
          y={y + (isUp ? (100 - bodyHeight) : 0)}
          width={width}
          height={bodyHeight}
          fill={bodyColor}
          stroke={bodyColor}
          strokeWidth={1}
        />
      </g>
    );
  };

  return (
    <div className={styles.chartCard}>
      <h4 className={styles.chartTitle}>Candlestick Chart</h4>
      <ResponsiveContainer width="100%" height={400}>
        <ComposedChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
          <XAxis
            dataKey="date"
            tick={{ fontSize: 12 }}
            tickFormatter={(value) => {
              if (typeof value === 'string' && value.length > 10) {
                return value.slice(5, 10); // MM-DD
              }
              return value;
            }}
          />
          <YAxis domain={['dataMin - 5', 'dataMax + 5']} />
          <Tooltip
            content={({ active, payload }) => {
              if (!active || !payload || !payload[0]) return null;
              const data = payload[0].payload;
              return (
                <div className={styles.customTooltip}>
                  <div className={styles.tooltipDate}>{data.date}</div>
                  <div>Open: <b>${data.open.toFixed(2)}</b></div>
                  <div>High: <b className={styles.high}>${data.high.toFixed(2)}</b></div>
                  <div>Low: <b className={styles.low}>${data.low.toFixed(2)}</b></div>
                  <div>Close: <b className={data.isUp ? styles.up : styles.down}>${data.close.toFixed(2)}</b></div>
                  <div>Volume: <b>{data.volume.toLocaleString()}</b></div>
                </div>
              );
            }}
          />
          <Line type="monotone" dataKey="close" stroke="#3b82f6" dot={false} strokeWidth={2} />
          <Bar dataKey="high" fill="transparent" />
        </ComposedChart>
      </ResponsiveContainer>
      <div className={styles.legend}>
        <span className={styles.legendItemUp}>● Bullish (Up)</span>
        <span className={styles.legendItemDown}>● Bearish (Down)</span>
      </div>
    </div>
  );
}

// Volume Chart
export function VolumeChart({ ohlcData }) {
  const data = useMemo(() => {
    if (!Array.isArray(ohlcData) || ohlcData.length === 0) return [];

    return ohlcData.map(item => ({
      date: item.date,
      volume: Number(item.volume || 0),
      isUp: (item.close || 0) >= (item.open || 0),
    }));
  }, [ohlcData]);

  if (data.length === 0) return null;

  return (
    <div className={styles.chartCard}>
      <h4 className={styles.chartTitle}>Trading Volume</h4>
      <ResponsiveContainer width="100%" height={250}>
        <ComposedChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
          <XAxis
            dataKey="date"
            tick={{ fontSize: 12 }}
            tickFormatter={(value) => {
              if (typeof value === 'string' && value.length > 10) {
                return value.slice(5, 10);
              }
              return value;
            }}
          />
          <YAxis
            tickFormatter={(value) => {
              if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`;
              if (value >= 1000) return `${(value / 1000).toFixed(1)}K`;
              return value;
            }}
          />
          <Tooltip
            content={({ active, payload }) => {
              if (!active || !payload || !payload[0]) return null;
              const data = payload[0].payload;
              return (
                <div className={styles.customTooltip}>
                  <div className={styles.tooltipDate}>{data.date}</div>
                  <div>Volume: <b>{data.volume.toLocaleString()}</b></div>
                </div>
              );
            }}
          />
          <Bar dataKey="volume" radius={[4, 4, 0, 0]}>
            {data.map((entry, index) => (
              <Cell
                key={`cell-${index}`}
                fill={entry.isUp ? "#10b981" : "#ef4444"}
              />
            ))}
          </Bar>
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

// Combined OHLC + Volume Chart
export function OHLCVolumeChart({ ohlcData }) {
  if (!Array.isArray(ohlcData) || ohlcData.length === 0) return null;

  return (
    <div className={styles.chartContainer}>
      <CandlestickChart ohlcData={ohlcData} />
      <VolumeChart ohlcData={ohlcData} />
    </div>
  );
}
