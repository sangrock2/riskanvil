import styles from "../../css/common.module.css";
import { useTranslation } from "../../hooks/useTranslation";

/**
 * 에러 메시지 컴포넌트
 * @param {object} props
 * @param {string} props.title - 에러 제목
 * @param {string} props.message - 에러 메시지
 * @param {function} props.onRetry - 재시도 콜백
 * @param {function} props.onDismiss - 닫기 콜백
 */
export default function ErrorMessage({ title, message, onRetry, onDismiss }) {
  const { t } = useTranslation();

  if (!message && !title) return null;

  return (
    <div className={styles.errorContainer} role="alert" aria-live="assertive">
      <div className={styles.errorIcon} aria-hidden="true">!</div>
      <div className={styles.errorContent}>
        {title && <div className={styles.errorTitle}>{title}</div>}
        <div className={styles.errorMessage}>{message}</div>
        {(onRetry || onDismiss) && (
          <div className={styles.errorActions}>
            {onRetry && (
              <button
                type="button"
                className={`${styles.btn} ${styles.btnSm}`}
                onClick={onRetry}
                aria-label={t("common.retry")}
              >
                {t("common.retry")}
              </button>
            )}
            {onDismiss && (
              <button
                type="button"
                className={`${styles.btnGhost} ${styles.btnSm}`}
                onClick={onDismiss}
                aria-label={t("common.close")}
              >
                {t("common.close")}
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
