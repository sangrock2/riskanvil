import { useState, useEffect, useRef } from "react";
import styles from "../../css/LazyImage.module.css";
import { useTranslation } from "../../hooks/useTranslation";

/**
 * LazyImage - Image component with lazy loading using Intersection Observer
 *
 * @param {string} src - Image source URL
 * @param {string} alt - Alternative text for accessibility
 * @param {string} placeholder - Placeholder image (base64 or low-res URL)
 * @param {string} className - Additional CSS class
 * @param {number} threshold - Intersection observer threshold (0-1)
 * @param {string} rootMargin - Margin around the viewport for early loading
 */
export default function LazyImage({
  src,
  alt = "",
  placeholder = null,
  className = "",
  threshold = 0.01,
  rootMargin = "50px",
  ...props
}) {
  const { t } = useTranslation();
  const [isLoaded, setIsLoaded] = useState(false);
  const [isInView, setIsInView] = useState(false);
  const [hasError, setHasError] = useState(false);
  const imgRef = useRef(null);

  useEffect(() => {
    if (!imgRef.current) return;

    // Use Intersection Observer to detect when image enters viewport
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setIsInView(true);
            observer.unobserve(entry.target);
          }
        });
      },
      {
        threshold,
        rootMargin,
      }
    );

    observer.observe(imgRef.current);

    return () => {
      if (imgRef.current) {
        observer.unobserve(imgRef.current);
      }
    };
  }, [threshold, rootMargin]);

  const handleLoad = () => {
    setIsLoaded(true);
  };

  const handleError = () => {
    setHasError(true);
  };

  return (
    <div
      ref={imgRef}
      className={`${styles.container} ${className}`}
      {...props}
    >
      {/* Placeholder shown before loading */}
      {!isLoaded && !hasError && (
        <div className={styles.placeholder}>
          {placeholder ? (
            <img src={placeholder} alt="" className={styles.placeholderImg} />
          ) : (
            <div className={styles.spinner} />
          )}
        </div>
      )}

      {/* Error state */}
      {hasError && (
        <div className={styles.error}>
          <span className={styles.errorIcon}>🖼️</span>
          <span className={styles.errorText}>{t("common.imageLoadFailed")}</span>
        </div>
      )}

      {/* Actual image - only load when in view */}
      {isInView && !hasError && (
        <img
          src={src}
          alt={alt}
          className={`${styles.image} ${isLoaded ? styles.loaded : ""}`}
          onLoad={handleLoad}
          onError={handleError}
          loading="lazy"
        />
      )}
    </div>
  );
}
