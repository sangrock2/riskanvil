import { createContext, useContext, useState, useCallback, useEffect, useRef } from "react";
import { useTranslation } from "../../hooks/useTranslation";
import styles from "../../css/Toast.module.css";

const ToastContext = createContext(null);

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used within ToastProvider");
  }
  return context;
}

let toastId = 0;

export function ToastProvider({ children }) {
  const { t } = useTranslation();
  const [toasts, setToasts] = useState([]);
  const timeoutMapRef = useRef(new Map());

  const removeToast = useCallback((id) => {
    const timeoutId = timeoutMapRef.current.get(id);
    if (timeoutId) {
      clearTimeout(timeoutId);
      timeoutMapRef.current.delete(id);
    }
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  useEffect(() => {
    const timeoutMap = timeoutMapRef.current;
    return () => {
      timeoutMap.forEach((timeoutId) => clearTimeout(timeoutId));
      timeoutMap.clear();
    };
  }, []);

  const addToast = useCallback((message, type = "info", duration = 5000) => {
    const id = ++toastId;
    const safeDuration = Number.isFinite(Number(duration)) ? Math.max(0, Number(duration)) : 5000;
    const toast = { id, message, type, duration: safeDuration };

    setToasts((prev) => [...prev, toast]);

    if (safeDuration > 0) {
      const timeoutId = setTimeout(() => {
        removeToast(id);
      }, safeDuration);
      timeoutMapRef.current.set(id, timeoutId);
    }

    return id;
  }, [removeToast]);

  const toast = {
    success: (message, duration) => addToast(message, "success", duration),
    error: (message, duration) => addToast(message, "error", duration),
    warning: (message, duration) => addToast(message, "warning", duration),
    info: (message, duration) => addToast(message, "info", duration),
  };

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div className={styles.container}>
        {toasts.map((toastItem) => (
          <div
            key={toastItem.id}
            className={`${styles.toast} ${styles[toastItem.type]}`}
            onClick={() => removeToast(toastItem.id)}
            role="alert"
            aria-live={toastItem.type === "error" ? "assertive" : "polite"}
            style={{ "--toast-duration": `${toastItem.duration}ms` }}
          >
            <div className={styles.icon}>
              {toastItem.type === "success" && "✓"}
              {toastItem.type === "error" && "✕"}
              {toastItem.type === "warning" && "⚠"}
              {toastItem.type === "info" && "ℹ"}
            </div>
            <div className={styles.message}>{toastItem.message}</div>
            <button
              className={styles.closeBtn}
              onClick={(e) => {
                e.stopPropagation();
                removeToast(toastItem.id);
              }}
              aria-label={t("common.close")}
            >
              ✕
            </button>
            {toastItem.duration > 0 && <span className={styles.progress} aria-hidden="true" />}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
