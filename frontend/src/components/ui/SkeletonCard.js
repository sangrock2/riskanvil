import styles from "../../css/SkeletonCard.module.css";

/**
 * 카드 스켈레톤 컴포넌트
 * @param {object} props
 * @param {"default" | "news" | "analysis" | "chart"} props.variant - 카드 타입
 * @param {number} props.count - 반복 횟수
 */
export default function SkeletonCard({ variant = "default", count = 1 }) {
  function renderSkeleton() {
    switch (variant) {
      case "news":
        return (
          <div className={styles.card}>
            <div className={styles.skeletonTitle} style={{ width: "80%" }} />
            <div className={styles.skeletonText} style={{ width: "60%" }} />
            <div className={styles.skeletonText} style={{ width: "50%" }} />
            <div style={{ display: "flex", gap: "8px", marginTop: "12px" }}>
              <div className={styles.skeletonBadge} style={{ width: "60px" }} />
              <div className={styles.skeletonBadge} style={{ width: "80px" }} />
            </div>
          </div>
        );

      case "analysis":
        return (
          <div className={styles.card}>
            <div className={styles.headerRow}>
              <div className={styles.skeletonTitle} style={{ width: "40%" }} />
              <div className={styles.skeletonBadge} style={{ width: "60px" }} />
            </div>
            <div className={styles.skeletonBox} style={{ height: "200px", marginTop: "16px" }} />
            <div className={styles.metaRow}>
              <div className={styles.skeletonText} style={{ width: "30%" }} />
              <div className={styles.skeletonText} style={{ width: "40%" }} />
            </div>
          </div>
        );

      case "chart":
        return (
          <div className={styles.card}>
            <div className={styles.skeletonTitle} style={{ width: "30%" }} />
            <div className={styles.skeletonBox} style={{ height: "220px", marginTop: "12px" }} />
          </div>
        );

      default:
        return (
          <div className={styles.card}>
            <div className={styles.skeletonTitle} />
            <div className={styles.skeletonText} />
            <div className={styles.skeletonText} />
            <div className={styles.skeletonText} style={{ width: "60%" }} />
          </div>
        );
    }
  }

  const items = Array.from({ length: count }, (_, i) => (
    <div key={i} className={styles.wrapper}>
      {renderSkeleton()}
    </div>
  ));

  return count === 1 ? items[0] : <div className={styles.grid}>{items}</div>;
}
