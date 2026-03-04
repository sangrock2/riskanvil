import { useState, useRef, useEffect } from "react";
import styles from "../../css/Tooltip.module.css";

/**
 * Tooltip 컴포넌트
 * @param {object} props
 * @param {React.ReactNode} props.children - 호버할 요소
 * @param {string} props.content - 툴팁 내용
 * @param {"top" | "bottom" | "left" | "right"} props.position - 툴팁 위치
 * @param {number} props.delay - 표시 지연 (ms)
 */
export default function Tooltip({
  children,
  content,
  position = "top",
  delay = 200,
}) {
  const [visible, setVisible] = useState(false);
  const [coords, setCoords] = useState({ x: 0, y: 0 });
  const timeoutRef = useRef(null);
  const triggerRef = useRef(null);
  const tooltipRef = useRef(null);

  useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, []);

  const handleMouseEnter = () => {
    timeoutRef.current = setTimeout(() => {
      if (triggerRef.current) {
        const rect = triggerRef.current.getBoundingClientRect();
        setCoords({
          x: rect.left + rect.width / 2,
          y: rect.top,
        });
        setVisible(true);
      }
    }, delay);
  };

  const handleMouseLeave = () => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }
    setVisible(false);
  };

  if (!content) {
    return <>{children}</>;
  }

  return (
    <>
      <span
        ref={triggerRef}
        className={styles.trigger}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        onFocus={handleMouseEnter}
        onBlur={handleMouseLeave}
        tabIndex={0}
      >
        {children}
      </span>

      {visible && (
        <div
          ref={tooltipRef}
          className={`${styles.tooltip} ${styles[position]}`}
          style={{
            left: `${coords.x}px`,
            top: `${coords.y}px`,
          }}
          role="tooltip"
          aria-hidden={!visible}
        >
          <div className={styles.content}>{content}</div>
          <div className={styles.arrow} />
        </div>
      )}
    </>
  );
}
