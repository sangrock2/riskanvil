import styles from "../../css/common.module.css";
import { useTranslation } from "../../hooks/useTranslation";

/**
 * 로딩 스피너 컴포넌트
 * @param {object} props
 * @param {"sm" | "md" | "lg"} props.size - 스피너 크기
 * @param {string} props.text - 로딩 텍스트
 * @param {boolean} props.center - 중앙 정렬 여부
 */
export default function Spinner({ size = "md", text, center = false }) {
  const { t } = useTranslation();
  const sizeClass = size === "sm" ? styles.spinnerSm : size === "lg" ? styles.spinnerLg : "";

  if (center) {
    return (
      <div className={styles.loadingContainer}>
        <div className={`${styles.spinner} ${sizeClass}`} role="status" aria-label={t("common.loading")} />
        {text && <span className={styles.loadingText}>{text}</span>}
      </div>
    );
  }

  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}>
      <span className={`${styles.spinner} ${sizeClass}`} role="status" aria-label={t("common.loading")} />
      {text && <span className={styles.loadingText}>{text}</span>}
    </span>
  );
}
