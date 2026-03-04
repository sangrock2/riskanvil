import { useMemo } from 'react';
import styles from '../css/MultiTimeframeChart.module.css';

/**
 * Displays multi-timeframe momentum analysis (20d, 60d, 120d, 200d)
 * @param {Object} props - Component props
 * @param {Object} props.multiMomentum - Multi-timeframe momentum data from backend
 * @param {number} props.multiMomentum.mom20 - 20-day momentum percentage
 * @param {number} props.multiMomentum.mom60 - 60-day momentum percentage
 * @param {number} props.multiMomentum.mom120 - 120-day momentum percentage
 * @param {number} props.multiMomentum.mom200 - 200-day momentum percentage
 * @param {number} props.multiMomentum.composite - Weighted composite momentum
 * @param {string} props.multiMomentum.trend_strength - Trend strength classification
 */
export default function MultiTimeframeChart({ multiMomentum }) {
  const timeframes = useMemo(() => {
    if (!multiMomentum) return [];
    return [
      { period: '20일', value: multiMomentum.mom20 || 0, weight: '40%' },
      { period: '60일', value: multiMomentum.mom60 || 0, weight: '30%' },
      { period: '120일', value: multiMomentum.mom120 || 0, weight: '20%' },
      { period: '200일', value: multiMomentum.mom200 || 0, weight: '10%' }
    ];
  }, [multiMomentum]);

  if (!multiMomentum) return null;

  const composite = multiMomentum.composite || 0;
  const trendStrength = multiMomentum.trend_strength || 'neutral';

  return (
    <div className={styles.container}>
      <h3 className={styles.title}>다중 타임프레임 모멘텀 분석</h3>
      <p className={styles.description}>
        여러 기간의 가격 변동을 가중 평균하여 종합적인 추세 강도를 평가합니다.
      </p>

      <div className={styles.timeframes}>
        {timeframes.map(({ period, value, weight }) => (
          <div key={period} className={styles.timeframe}>
            <div className={styles.label}>
              <span className={styles.period}>{period}</span>
              <span className={styles.weight}>가중치: {weight}</span>
            </div>
            <div className={styles.barContainer}>
              <div
                className={`${styles.bar} ${value >= 0 ? styles.positive : styles.negative}`}
                style={{ width: `${Math.min(Math.abs(value), 100)}%` }}
                title={`${value.toFixed(2)}%`}
              >
                <span className={styles.barValue}>{value.toFixed(1)}%</span>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className={styles.composite}>
        <h4 className={styles.compositeTitle}>종합 모멘텀</h4>
        <div className={styles.compositeValue}>
          <span className={composite >= 0 ? styles.up : styles.down}>
            {composite >= 0 ? '↑' : '↓'} {Math.abs(composite).toFixed(2)}%
          </span>
        </div>
        <div className={styles.trend}>
          <span className={styles.trendLabel}>추세 강도:</span>
          <span className={`${styles.trendValue} ${styles[`trend_${trendStrength.toLowerCase()}`]}`}>
            {trendStrength === 'strong_bullish' && '강한 상승세'}
            {trendStrength === 'bullish' && '상승세'}
            {trendStrength === 'neutral' && '중립'}
            {trendStrength === 'bearish' && '하락세'}
            {trendStrength === 'strong_bearish' && '강한 하락세'}
          </span>
        </div>
      </div>
    </div>
  );
}
