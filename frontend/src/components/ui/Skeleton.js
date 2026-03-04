import styles from "../../css/common.module.css";

/**
 * 스켈레톤 로딩 컴포넌트
 * @param {object} props
 * @param {"text" | "title" | "box" | "circle"} props.variant - 스켈레톤 타입
 * @param {string} props.width - 너비
 * @param {string} props.height - 높이
 * @param {number} props.count - 반복 횟수
 */
export default function Skeleton({ variant = "text", width, height, count = 1 }) {
  function getClassName() {
    switch (variant) {
      case "title":
        return styles.skeletonTitle;
      case "box":
        return styles.skeletonBox;
      case "circle":
        return styles.skeleton;
      default:
        return styles.skeletonText;
    }
  }

  const baseStyle = {};
  if (width) baseStyle.width = width;
  if (height) baseStyle.height = height;
  if (variant === "circle") {
    baseStyle.borderRadius = "50%";
    baseStyle.width = width || "40px";
    baseStyle.height = height || "40px";
  }

  const items = Array.from({ length: count }, (_, i) => (
    <div
      key={i}
      className={getClassName()}
      style={baseStyle}
      aria-hidden="true"
    />
  ));

  return count === 1 ? items[0] : <div>{items}</div>;
}
