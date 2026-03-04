import styles from "../../css/common.module.css";

/**
 * 빈 상태 컴포넌트
 * @param {object} props
 * @param {string} props.icon - 아이콘 (이모지 또는 텍스트)
 * @param {string} props.title - 제목
 * @param {string} props.description - 설명
 * @param {React.ReactNode} props.action - 액션 버튼
 */
export default function EmptyState({ icon = "📭", title, description, action }) {
  return (
    <div className={styles.emptyContainer} role="status" aria-label={title || "데이터 없음"}>
      <div className={styles.emptyIcon} aria-hidden="true">{icon}</div>
      {title && <div className={styles.emptyTitle}>{title}</div>}
      {description && <div className={styles.emptyDescription}>{description}</div>}
      {action && <div className={styles.emptyActions}>{action}</div>}
    </div>
  );
}
