import { useEffect, useState } from "react";
import styles from "../../css/ProgressBar.module.css";

/**
 * Progress Bar 컴포넌트
 * @param {object} props
 * @param {number} props.value - 진행률 (0-100)
 * @param {boolean} props.indeterminate - 불확정 진행률
 * @param {string} props.label - 라벨 텍스트
 * @param {boolean} props.showPercentage - 퍼센트 표시 여부
 * @param {"primary" | "success" | "warning" | "danger"} props.color - 색상
 * @param {"sm" | "md" | "lg"} props.size - 크기
 */
export default function ProgressBar({
  value = 0,
  indeterminate = false,
  label,
  showPercentage = true,
  color = "primary",
  size = "md",
}) {
  const [displayValue, setDisplayValue] = useState(0);

  // Smooth animation for value changes
  useEffect(() => {
    if (indeterminate) return;

    const clampedValue = Math.max(0, Math.min(100, value));
    const timer = setTimeout(() => {
      setDisplayValue(clampedValue);
    }, 50);

    return () => clearTimeout(timer);
  }, [value, indeterminate]);

  const sizeClass = size === "sm" ? styles.progressSm : size === "lg" ? styles.progressLg : styles.progressMd;
  const colorClass = styles[`progress${color.charAt(0).toUpperCase()}${color.slice(1)}`] || styles.progressPrimary;

  return (
    <div className={styles.container}>
      {(label || showPercentage) && (
        <div className={styles.header}>
          {label && <span className={styles.label}>{label}</span>}
          {showPercentage && !indeterminate && (
            <span className={styles.percentage}>{Math.round(displayValue)}%</span>
          )}
        </div>
      )}
      <div className={`${styles.track} ${sizeClass}`} role="progressbar" aria-valuenow={displayValue} aria-valuemin="0" aria-valuemax="100">
        <div
          className={`${styles.fill} ${colorClass} ${indeterminate ? styles.indeterminate : ""}`}
          style={{ width: indeterminate ? "100%" : `${displayValue}%` }}
        />
      </div>
    </div>
  );
}
