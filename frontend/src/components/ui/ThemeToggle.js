import { useState, useEffect } from "react";
import { toggleTheme, initTheme } from "../../utils/theme";
import { useTranslation } from "../../hooks/useTranslation";
import styles from "../../css/NavBar.module.css";

/**
 * 다크 모드 토글 버튼
 * 다른 네비게이션 버튼들과 통일된 디자인
 */
export default function ThemeToggle() {
  const { t } = useTranslation();
  const [theme, setThemeState] = useState("light");
  const [mounted, setMounted] = useState(false);

  useEffect(function onMount() {
    const currentTheme = initTheme();
    setThemeState(currentTheme);
    setMounted(true);

    // Listen for theme changes from other components (e.g., Settings page)
    const handleThemeChange = (e) => {
      setThemeState(e.detail.theme);
    };

    window.addEventListener("themechange", handleThemeChange);

    return () => {
      window.removeEventListener("themechange", handleThemeChange);
    };
  }, []);

  function handleToggle() {
    const newTheme = toggleTheme();
    setThemeState(newTheme);
  }

  // hydration 불일치 방지
  if (!mounted) {
    return (
      <button
        type="button"
        className={`${styles.btn} ${styles.themeToggleBtn}`}
        aria-label={t("settings.toggleTheme")}
        disabled
      >
        <span className={styles.themeIconWrapper} aria-hidden="true">🌓</span>
        <span className={styles.themeText}>{t("settings.theme")}</span>
      </button>
    );
  }

  return (
    <button
      type="button"
      className={`${styles.btn} ${styles.themeToggleBtn}`}
      onClick={handleToggle}
      aria-label={theme === "dark" ? t("settings.switchToLight") : t("settings.switchToDark")}
      aria-pressed={theme === "dark"}
      title={theme === "dark" ? t("settings.switchToLight") : t("settings.switchToDark")}
    >
      <span
        className={styles.themeIconWrapper}
        aria-hidden="true"
        style={{
          display: "inline-block",
          transition: "transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1)",
          transform: theme === "dark" ? "rotate(180deg)" : "rotate(0deg)",
        }}
      >
        {theme === "dark" ? "🌙" : "☀️"}
      </span>
      <span className={styles.themeText}>
        {theme === "dark" ? t("settings.themeDark") : t("settings.themeLight")}
      </span>
    </button>
  );
}
