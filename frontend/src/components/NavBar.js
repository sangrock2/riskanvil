import { useMemo, useState, useEffect } from "react";
import { useLocation, Link, useNavigate } from "react-router-dom";
import { getToken, getRefreshToken, clearAllTokens } from "../auth/token";
import { apiFetch } from "../api/http";
import { useTranslation } from "../hooks/useTranslation";
import ThemeToggle from "./ui/ThemeToggle";
import SearchAutocomplete from "./SearchAutocomplete";
import styles from "../css/NavBar.module.css";

export default function NavBar() {
  const nav = useNavigate();
  const loc = useLocation();
  const loggedIn = !!getToken();
  const { t } = useTranslation();
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  const active = useMemo(function getActivePath() {
    return loc.pathname;
  }, [loc.pathname]);

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 18);
    };

    handleScroll();
    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  useEffect(() => {
    setMenuOpen(false);
  }, [loc.pathname]);

  async function logout() {
    const refreshToken = getRefreshToken();

    // Revoke refresh token on server
    if (refreshToken) {
      try {
        await apiFetch("/api/auth/logout", {
          method: "POST",
          body: JSON.stringify({ refreshToken }),
          retry: 0, // Don't retry logout
        });
      } catch (error) {
        // Ignore errors - clear tokens anyway
        console.error("Logout error:", error);
      }
    }

    // Clear tokens from localStorage (triggers multi-tab sync)
    clearAllTokens();
    nav("/login");
  }

  function getLinkClass(path) {
    return active === path ? `${styles.link} ${styles.active}` : styles.link;
  }

  return (
    <header className={`${styles.header} ${scrolled ? styles.headerScrolled : ""}`}>
      <nav className={styles.inner} aria-label={t("nav.mainNavigation")}>
        {/* 첫 줄: 브랜드, 검색, 액션 버튼 */}
        <div className={styles.top}>
          <div className={styles.left}>
            <Link to="/" className={styles.brand} aria-label={t("nav.homeAria")}>
              Stock-AI
            </Link>
          </div>

          <div className={styles.center}>
            <SearchAutocomplete className={styles.search} />
          </div>

          <div className={styles.right}>
            <button
              type="button"
              className={`${styles.btn} ${styles.mobileMenuToggle}`}
              onClick={() => setMenuOpen((prev) => !prev)}
              aria-expanded={menuOpen}
              aria-controls="main-nav-links"
            >
              {menuOpen ? t("nav.closeMenu") : t("nav.menu")}
            </button>

            <ThemeToggle />

            {loggedIn ? (
              <>
                <Link
                  className={styles.btn}
                  to="/settings"
                  aria-label={t("nav.settings")}
                >
                  {t("nav.settings")}
                </Link>
                <button
                  className={`${styles.btn} ${styles.btnDanger}`}
                  onClick={logout}
                  aria-label={t("nav.logout")}
                >
                  {t("nav.logout")}
                </button>
              </>
            ) : (
              <div className={styles.auth}>
                <Link className={styles.btn} to="/login">
                  {t("auth.login")}
                </Link>
                <Link className={styles.btn} to="/register">
                  {t("auth.register")}
                </Link>
              </div>
            )}
          </div>
        </div>

        {/* 둘째 줄: 네비게이션 링크들 */}
        <div id="main-nav-links" className={`${styles.nav} ${menuOpen ? styles.navOpen : ""}`} role="menubar">
            <Link
              className={getLinkClass("/dashboard")}
              to="/dashboard"
              role="menuitem"
              aria-current={active === "/dashboard" ? "page" : undefined}
            >
              {t("nav.dashboard")}
            </Link>
            <Link
              className={getLinkClass("/analyze")}
              to="/analyze"
              role="menuitem"
              aria-current={active === "/analyze" ? "page" : undefined}
            >
              {t("nav.analyze")}
            </Link>
            <Link
              className={getLinkClass("/watchlist")}
              to="/watchlist"
              role="menuitem"
              aria-current={active === "/watchlist" ? "page" : undefined}
            >
              {t("nav.watchlist")}
            </Link>
            <Link
              className={getLinkClass("/compare")}
              to="/compare"
              role="menuitem"
              aria-current={active === "/compare" ? "page" : undefined}
            >
              {t("nav.compare")}
            </Link>
            <Link
              className={getLinkClass("/portfolio")}
              to="/portfolio"
              role="menuitem"
              aria-current={active === "/portfolio" ? "page" : undefined}
            >
              {t("nav.portfolio")}
            </Link>
            <Link
              className={getLinkClass("/dividends")}
              to="/dividends"
              role="menuitem"
              aria-current={active === "/dividends" ? "page" : undefined}
            >
              {t("nav.dividends")}
            </Link>
            <Link
              className={getLinkClass("/earnings")}
              to="/earnings"
              role="menuitem"
              aria-current={active === "/earnings" ? "page" : undefined}
            >
              {t("nav.earnings")}
            </Link>
            <Link
              className={getLinkClass("/risk-dashboard")}
              to="/risk-dashboard"
              role="menuitem"
              aria-current={active === "/risk-dashboard" ? "page" : undefined}
            >
              {t("nav.riskDashboard")}
            </Link>
            <Link
              className={getLinkClass("/screener")}
              to="/screener"
              role="menuitem"
              aria-current={active === "/screener" ? "page" : undefined}
            >
              {t("nav.screener")}
            </Link>
            <Link
              className={getLinkClass("/correlation")}
              to="/correlation"
              role="menuitem"
              aria-current={active === "/correlation" ? "page" : undefined}
            >
              {t("nav.correlation")}
            </Link>
            <Link
              className={getLinkClass("/paper-trading")}
              to="/paper-trading"
              role="menuitem"
              aria-current={active === "/paper-trading" ? "page" : undefined}
            >
              {t("nav.paperTrading")}
            </Link>
            <Link
              className={getLinkClass("/system-map")}
              to="/system-map"
              role="menuitem"
              aria-current={active === "/system-map" ? "page" : undefined}
            >
              {t("nav.systemMap")}
            </Link>
            <Link
              className={getLinkClass("/chatbot")}
              to="/chatbot"
              role="menuitem"
              aria-current={active === "/chatbot" ? "page" : undefined}
            >
              {t("nav.chatbot")}
            </Link>
            <Link
              className={getLinkClass("/usage")}
              to="/usage"
              role="menuitem"
              aria-current={active === "/usage" ? "page" : undefined}
            >
              {t("nav.usage")}
            </Link>
            <Link
              className={getLinkClass("/glossary")}
              to="/glossary"
              role="menuitem"
              aria-current={active === "/glossary" ? "page" : undefined}
            >
              {t("nav.glossary")}
            </Link>
            <Link
              className={getLinkClass("/learn")}
              to="/learn"
              role="menuitem"
              aria-current={active === "/learn" ? "page" : undefined}
            >
              {t("nav.learn")}
            </Link>
        </div>
      </nav>
    </header>
  );
}
