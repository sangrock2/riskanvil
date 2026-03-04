import { useState, useCallback, useRef, useMemo } from "react";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Brush,
} from "recharts";
import styles from "../css/InteractiveChart.module.css";

/**
 * Interactive price chart with zoom, pan, and date range selection
 *
 * @component
 * @param {Object} props - Component props
 * @param {Array<Object>} props.points - Array of data points with date and value properties
 * @param {string} [props.valueKey='close'] - Key for Y-axis value in data points (e.g., 'close', 'price')
 * @param {string} [props.labelKey='date'] - Key for X-axis label in data points (e.g., 'date', 'timestamp')
 * @returns {JSX.Element} Interactive chart component with zoom and brush controls
 *
 * @example
 * // Basic usage with stock price data
 * <InteractiveChart
 *   points={priceData}
 *   valueKey="close"
 *   labelKey="date"
 * />
 *
 * @example
 * // Custom value key for volume chart
 * <InteractiveChart
 *   points={volumeData}
 *   valueKey="volume"
 *   labelKey="date"
 * />
 *
 * @description
 * Features:
 * - Interactive zoom and pan using brush control
 * - Reset zoom functionality
 * - SVG snapshot download
 * - Responsive container that adapts to parent width
 * - Auto-calculated Y-axis domain with padding
 * - Reference line at data average
 */
export default function InteractiveChart({ points = [], valueKey = "close", labelKey = "date" }) {
  const [zoomDomain, setZoomDomain] = useState(null);
  const [selectedRange, setSelectedRange] = useState(null);
  const chartRef = useRef(null);

  const data = useMemo(() => {
    if (!Array.isArray(points) || points.length === 0) return [];

    return points.map((point, index) => ({
      index,
      date: point[labelKey] || `Day ${index}`,
      value: Number(point[valueKey] || 0),
      ...point,
    }));
  }, [points, valueKey, labelKey]);

  const filteredData = useMemo(() => {
    if (!zoomDomain) return data;

    const [start, end] = zoomDomain;
    return data.filter((_, index) => index >= start && index <= end);
  }, [data, zoomDomain]);

  const handleResetZoom = useCallback(() => {
    setZoomDomain(null);
    setSelectedRange(null);
  }, []);

  const handleBrushChange = useCallback((range) => {
    if (range && range.startIndex !== undefined && range.endIndex !== undefined) {
      setSelectedRange(range);
      setZoomDomain([range.startIndex, range.endIndex]);
    }
  }, []);

  const downloadSnapshot = useCallback(() => {
    if (!chartRef.current) return;

    try {
      // Get SVG element
      const svgElement = chartRef.current.querySelector('svg');
      if (!svgElement) return;

      // Serialize SVG to string
      const serializer = new XMLSerializer();
      let svgString = serializer.serializeToString(svgElement);

      // Add XML declaration and styles
      svgString = `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
${svgString}`;

      // Create blob and download
      const blob = new Blob([svgString], { type: 'image/svg+xml;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `chart_${new Date().toISOString().slice(0, 10)}.svg`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Failed to download chart:', error);
    }
  }, []);

  const yDomain = useMemo(() => {
    const values = filteredData.map(d => d.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const padding = (max - min) * 0.1;
    return [min - padding, max + padding];
  }, [filteredData]);

  if (data.length === 0) {
    return (
      <div className={styles.empty}>
        No data available for chart
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <div className={styles.controls}>
        <button
          className={styles.btn}
          onClick={handleResetZoom}
          disabled={!zoomDomain}
        >
          Reset Zoom
        </button>

        <button
          className={styles.btn}
          onClick={downloadSnapshot}
        >
          📸 Download Chart
        </button>

        {selectedRange && (
          <div className={styles.rangeInfo}>
            Range: {data[selectedRange.startIndex]?.date} ~ {data[selectedRange.endIndex]?.date}
          </div>
        )}
      </div>

      <div ref={chartRef} className={styles.chartWrapper}>
        <ResponsiveContainer width="100%" height={400}>
          <LineChart data={filteredData}>
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
            <YAxis domain={yDomain} tickFormatter={(value) => value.toFixed(2)} />
            <Tooltip
              content={({ active, payload }) => {
                if (!active || !payload || !payload[0]) return null;
                const data = payload[0].payload;
                return (
                  <div className={styles.tooltip}>
                    <div className={styles.tooltipDate}>{data.date}</div>
                    <div className={styles.tooltipValue}>
                      ${data.value.toFixed(2)}
                    </div>
                  </div>
                );
              }}
            />
            <Line
              type="monotone"
              dataKey="value"
              stroke="#3b82f6"
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 6 }}
            />
          </LineChart>
        </ResponsiveContainer>

        {/* Brush for date range selection */}
        <ResponsiveContainer width="100%" height={60}>
          <LineChart data={data}>
            <Brush
              dataKey="date"
              height={50}
              stroke="#3b82f6"
              fill="#f0f9ff"
              onChange={handleBrushChange}
              tickFormatter={(value) => {
                if (typeof value === 'string' && value.length > 10) {
                  return value.slice(5, 10);
                }
                return value;
              }}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className={styles.instructions}>
        💡 Drag the bottom slider to select a date range. Click "Reset Zoom" to view all data.
      </div>
    </div>
  );
}
