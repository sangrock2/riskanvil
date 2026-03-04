import styles from "../../css/SkeletonTable.module.css";

/**
 * 테이블 스켈레톤 컴포넌트
 * @param {object} props
 * @param {number} props.rows - 행 개수
 * @param {number} props.columns - 열 개수
 */
export default function SkeletonTable({ rows = 5, columns = 4 }) {
  const headerCells = Array.from({ length: columns }, (_, i) => (
    <th key={i}>
      <div className={styles.skeletonHeader} />
    </th>
  ));

  const bodyCells = (rowIndex) =>
    Array.from({ length: columns }, (_, i) => (
      <td key={i}>
        <div
          className={styles.skeletonCell}
          style={{
            width: i === 0 ? "80%" : i === columns - 1 ? "50%" : "70%",
          }}
        />
      </td>
    ));

  const bodyRows = Array.from({ length: rows }, (_, i) => (
    <tr key={i}>{bodyCells(i)}</tr>
  ));

  return (
    <div className={styles.tableWrap}>
      <table className={styles.table}>
        <thead>
          <tr>{headerCells}</tr>
        </thead>
        <tbody>{bodyRows}</tbody>
      </table>
    </div>
  );
}
