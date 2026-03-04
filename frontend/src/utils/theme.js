/**
 * 다크 모드 테마 관리 유틸리티
 */

const THEME_KEY = "stock-ai-theme";

/**
 * 현재 테마 가져오기
 * @returns {"light" | "dark"}
 */
export function getTheme() {
  if (typeof window === "undefined") return "light";

  const stored = localStorage.getItem(THEME_KEY);
  if (stored === "dark" || stored === "light") {
    return stored;
  }

  // 시스템 설정 확인
  if (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches) {
    return "dark";
  }

  return "light";
}

/**
 * 테마 설정하기
 * @param {"light" | "dark"} theme
 */
export function setTheme(theme) {
  if (typeof window === "undefined") return;

  localStorage.setItem(THEME_KEY, theme);
  document.documentElement.setAttribute("data-theme", theme);

  // Dispatch custom event for theme change synchronization
  window.dispatchEvent(new CustomEvent("themechange", { detail: { theme } }));
}

/**
 * 테마 토글하기
 * @returns {"light" | "dark"} 새로운 테마
 */
export function toggleTheme() {
  const current = getTheme();
  const next = current === "dark" ? "light" : "dark";
  setTheme(next);
  return next;
}

/**
 * 초기 테마 적용 (앱 시작 시 호출)
 */
export function initTheme() {
  const theme = getTheme();
  setTheme(theme);

  // 시스템 테마 변경 감지
  if (typeof window !== "undefined" && window.matchMedia) {
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    mediaQuery.addEventListener("change", (e) => {
      // localStorage에 저장된 테마가 없을 때만 시스템 테마 따라가기
      if (!localStorage.getItem(THEME_KEY)) {
        setTheme(e.matches ? "dark" : "light");
      }
    });
  }

  return theme;
}
