import { useState, useEffect, useRef, useCallback } from "react";
import styles from "../../css/VirtualList.module.css";

/**
 * VirtualList - Virtualized list component for rendering large datasets efficiently
 *
 * Only renders visible items plus a buffer, dramatically improving performance
 * for lists with hundreds or thousands of items.
 *
 * @param {Array} items - Array of items to render
 * @param {Function} renderItem - Function to render each item: (item, index) => ReactNode
 * @param {number} itemHeight - Fixed height of each item in pixels
 * @param {number} height - Container height in pixels
 * @param {number} overscan - Number of items to render outside visible area (default: 3)
 * @param {string} className - Additional CSS class for container
 */
export default function VirtualList({
  items = [],
  renderItem,
  itemHeight = 80,
  height = 400,
  overscan = 3,
  className = "",
}) {
  const [scrollTop, setScrollTop] = useState(0);
  const containerRef = useRef(null);

  // Calculate which items are visible
  const visibleRange = useCallback(() => {
    const startIndex = Math.max(0, Math.floor(scrollTop / itemHeight) - overscan);
    const endIndex = Math.min(
      items.length - 1,
      Math.ceil((scrollTop + height) / itemHeight) + overscan
    );

    return { startIndex, endIndex };
  }, [scrollTop, itemHeight, height, items.length, overscan]);

  const { startIndex, endIndex } = visibleRange();

  // Total height for scrollbar
  const totalHeight = items.length * itemHeight;

  // Offset for visible items
  const offsetY = startIndex * itemHeight;

  // Handle scroll event with throttling
  const handleScroll = useCallback((e) => {
    const scrollTop = e.target.scrollTop;
    setScrollTop(scrollTop);
  }, []);

  // Render only visible items
  const visibleItems = [];
  for (let i = startIndex; i <= endIndex; i++) {
    if (i < items.length) {
      visibleItems.push(
        <div
          key={i}
          className={styles.item}
          style={{
            position: "absolute",
            top: i * itemHeight,
            height: itemHeight,
            width: "100%",
          }}
        >
          {renderItem(items[i], i)}
        </div>
      );
    }
  }

  return (
    <div
      ref={containerRef}
      className={`${styles.container} ${className}`}
      style={{ height }}
      onScroll={handleScroll}
    >
      <div
        className={styles.content}
        style={{ height: totalHeight, position: "relative" }}
      >
        {visibleItems}
      </div>
    </div>
  );
}
