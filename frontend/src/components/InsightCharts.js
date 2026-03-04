import { useMemo } from "react";
import {
  PieChart,
  Pie,
  Cell,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  RadialBarChart,
  RadialBar,
  Legend,
} from "recharts";
import styles from "../css/InsightCharts.module.css";

const COLORS = {
  good: "#10b981",
  neutral: "#f59e0b",
  bad: "#ef4444",
  primary: "#3b82f6",
  secondary: "#8b5cf6",
  info: "#06b6d4",
};

// Score Breakdown Pie Chart
export function ScoreBreakdownChart({ breakdown }) {
  const data = useMemo(() => {
    if (!Array.isArray(breakdown) || breakdown.length === 0) return [];

    return breakdown
      .filter(item => item.contribution != null && item.contribution > 0)
      .map(item => ({
        name: item.metric,
        value: Number(item.contribution || 0),
        rawScore: item.rawScore || item.points,
      }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 8); // Top 8 contributors
  }, [breakdown]);

  if (data.length === 0) return null;

  return (
    <div className={styles.chartCard}>
      <h4 className={styles.chartTitle}>Score Contribution</h4>
      <ResponsiveContainer width="100%" height={300}>
        <PieChart>
          <Pie
            data={data}
            cx="50%"
            cy="50%"
            labelLine={false}
            label={(entry) => `${entry.name}: ${entry.value.toFixed(1)}`}
            outerRadius={80}
            fill="#8884d8"
            dataKey="value"
          >
            {data.map((entry, index) => (
              <Cell key={`cell-${index}`} fill={Object.values(COLORS)[index % Object.values(COLORS).length]} />
            ))}
          </Pie>
          <Tooltip formatter={(value) => value.toFixed(2)} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}

// Risk Level Gauge
export function RiskGaugeChart({ score }) {
  const scoreNum = Number(score || 50);

  const data = useMemo(() => {
    let level, color, range;

    if (scoreNum >= 70) {
      level = "Low Risk";
      color = COLORS.good;
      range = "70-100";
    } else if (scoreNum >= 50) {
      level = "Medium Risk";
      color = COLORS.neutral;
      range = "50-70";
    } else if (scoreNum >= 30) {
      level = "High Risk";
      color = COLORS.bad;
      range = "30-50";
    } else {
      level = "Very High Risk";
      color = "#dc2626";
      range = "0-30";
    }

    return [
      {
        name: level,
        value: scoreNum,
        fill: color,
        range,
      },
    ];
  }, [scoreNum]);

  return (
    <div className={styles.chartCard}>
      <h4 className={styles.chartTitle}>Risk Level</h4>
      <ResponsiveContainer width="100%" height={200}>
        <RadialBarChart
          cx="50%"
          cy="50%"
          innerRadius="60%"
          outerRadius="90%"
          barSize={20}
          data={data}
          startAngle={180}
          endAngle={0}
        >
          <RadialBar
            minAngle={15}
            background
            clockWise
            dataKey="value"
          />
          <Legend
            iconSize={0}
            layout="vertical"
            verticalAlign="middle"
            align="center"
            content={({ payload }) => {
              if (!payload || !payload[0]) return null;
              const item = payload[0].payload;
              return (
                <div className={styles.gaugeLabel}>
                  <div className={styles.gaugeName}>{item.name}</div>
                  <div className={styles.gaugeValue} style={{ color: item.fill }}>
                    {item.value.toFixed(0)}
                  </div>
                  <div className={styles.gaugeRange}>{item.range}</div>
                </div>
              );
            }}
          />
          <Tooltip />
        </RadialBarChart>
      </ResponsiveContainer>
    </div>
  );
}

// Metrics Comparison Bar Chart
export function MetricsBarChart({ breakdown }) {
  const data = useMemo(() => {
    if (!Array.isArray(breakdown) || breakdown.length === 0) return [];

    return breakdown
      .filter(item => item.rawScore != null || item.points != null)
      .map(item => ({
        metric: item.metric,
        score: Number(item.rawScore || item.points || 0),
      }))
      .sort((a, b) => b.score - a.score)
      .slice(0, 10);
  }, [breakdown]);

  if (data.length === 0) return null;

  return (
    <div className={styles.chartCard}>
      <h4 className={styles.chartTitle}>Individual Metric Scores</h4>
      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={data} layout="vertical">
          <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
          <XAxis type="number" domain={[0, 100]} />
          <YAxis dataKey="metric" type="category" width={150} style={{ fontSize: "12px" }} />
          <Tooltip />
          <Bar dataKey="score" fill={COLORS.primary} radius={[0, 4, 4, 0]}>
            {data.map((entry, index) => (
              <Cell
                key={`cell-${index}`}
                fill={
                  entry.score >= 70
                    ? COLORS.good
                    : entry.score >= 40
                    ? COLORS.neutral
                    : COLORS.bad
                }
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

// Sentiment Distribution
export function SentimentChart({ news }) {
  const data = useMemo(() => {
    if (!news || !Array.isArray(news.items)) return [];

    const sentiments = news.items.reduce((acc, item) => {
      const label = item.sentimentLabel?.toLowerCase() || item.sentiment?.label?.toLowerCase() || "neutral";

      if (label.includes("positive") || label.includes("bullish")) {
        acc.positive += 1;
      } else if (label.includes("negative") || label.includes("bearish")) {
        acc.negative += 1;
      } else {
        acc.neutral += 1;
      }

      return acc;
    }, { positive: 0, neutral: 0, negative: 0 });

    return [
      { name: "Positive", value: sentiments.positive, fill: COLORS.good },
      { name: "Neutral", value: sentiments.neutral, fill: COLORS.neutral },
      { name: "Negative", value: sentiments.negative, fill: COLORS.bad },
    ].filter(item => item.value > 0);
  }, [news]);

  if (data.length === 0) return null;

  return (
    <div className={styles.chartCard}>
      <h4 className={styles.chartTitle}>News Sentiment</h4>
      <ResponsiveContainer width="100%" height={250}>
        <PieChart>
          <Pie
            data={data}
            cx="50%"
            cy="50%"
            labelLine={false}
            label={(entry) => `${entry.name}: ${entry.value}`}
            outerRadius={80}
            fill="#8884d8"
            dataKey="value"
          >
            {data.map((entry, index) => (
              <Cell key={`cell-${index}`} fill={entry.fill} />
            ))}
          </Pie>
          <Tooltip />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
