import { lazy, Suspense, useEffect, useMemo, useRef } from "react";
import { BrowserRouter, Routes, Route, Navigate, useLocation } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./api/queryClient";
import NavBar from "./components/NavBar";
import ErrorBoundary from "./components/ErrorBoundary";
import OfflineIndicator from "./components/OfflineIndicator";
import { ToastProvider } from "./components/ui/Toast";
import { PreferencesProvider } from "./context/PreferencesContext";
import { LanguageProvider } from "./context/LanguageContext";
import { Spinner } from "./components/ui/Loading";
import { useAuthSync } from "./hooks/useAuthSync";
import { useActivityDetection } from "./hooks/useActivityDetection";
import { useTranslation } from "./hooks/useTranslation";
import styles from "./App.module.css";

// Lazy load pages for code splitting
const Login = lazy(() => import("./pages/Login"));
const Register = lazy(() => import("./pages/Register"));
const Dashboard = lazy(() => import("./pages/Dashboard"));
const Analyze = lazy(() => import("./pages/Analyze"));
const Backtest = lazy(() => import("./pages/Backtest"));
const InsightDetail = lazy(() => import("./pages/InsightDetail"));
const Watchlist = lazy(() => import("./pages/Watchlist"));
const Compare = lazy(() => import("./pages/Compare"));
const Usage = lazy(() => import("./pages/Usage"));
const Glossary = lazy(() => import("./pages/Glossary"));
const Learn = lazy(() => import("./pages/Learn"));
const Portfolio = lazy(() => import("./pages/Portfolio"));
const Settings = lazy(() => import("./pages/Settings"));
const Screener = lazy(() => import("./pages/Screener"));
const Correlation = lazy(() => import("./pages/Correlation"));
const Chatbot = lazy(() => import("./pages/Chatbot"));
const DividendCalendar = lazy(() => import("./pages/DividendCalendar"));
const EarningsCalendar = lazy(() => import("./pages/EarningsCalendar"));
const RiskDashboard = lazy(() => import("./pages/RiskDashboard"));
const PaperTrading = lazy(() => import("./pages/PaperTrading"));
const SystemMap = lazy(() => import("./pages/SystemMap"));
const Landing = lazy(() => import("./pages/Landing"));
const ProtectedRoute = lazy(() => import("./components/ProtectedRoute"));

function getTopSegment(pathname) {
  const parts = String(pathname || "/").split("/").filter(Boolean);
  return parts[0] || "";
}

function AppContent() {
  const { t } = useTranslation();

  // Multi-tab authentication synchronization
  useAuthSync();

  // User activity detection (for token refresh eligibility)
  useActivityDetection();

  const loc = useLocation();
  const isLanding = loc.pathname === "/";
  const prevPathRef = useRef(loc.pathname);

  const routeTransitionClass = useMemo(() => {
    const prevPath = prevPathRef.current;
    const prevTop = getTopSegment(prevPath);
    const nextTop = getTopSegment(loc.pathname);

    if (loc.pathname === "/" || prevPath === "/") return styles.routeSceneHero;
    if (nextTop === "insight-detail") return styles.routeSceneFocus;
    if (prevTop && prevTop === nextTop) return styles.routeSceneQuick;
    return styles.routeSceneStandard;
  }, [loc.pathname]);

  useEffect(() => {
    prevPathRef.current = loc.pathname;
  }, [loc.pathname]);

  return (
    <>
      {!isLanding && <NavBar />}
      <Suspense fallback={<Spinner center text={t("common.loading")} />}>
        <div className={`${styles.routeViewport} ${isLanding ? styles.routeViewportLanding : styles.routeViewportApp}`}>
          <div key={loc.pathname} className={`${styles.routeScene} ${routeTransitionClass}`}>
            <Routes location={loc}>
            <Route path="/" element={<Landing />} />

            {/* Public routes */}
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/glossary" element={<Glossary />} />
            <Route path="/learn" element={<Learn />} />

            {/* Protected routes - require authentication */}
            <Route path="/dashboard" element={
              <ProtectedRoute>
                <Dashboard />
              </ProtectedRoute>
            } />

            <Route path="/analyze" element={
              <ProtectedRoute>
                <Analyze />
              </ProtectedRoute>
            } />

            <Route path="/backtest" element={
              <ProtectedRoute>
                <Backtest />
              </ProtectedRoute>
            } />

            <Route path="/insight-detail" element={
              <ProtectedRoute>
                <InsightDetail />
              </ProtectedRoute>
            } />

            <Route path="/watchlist" element={
              <ProtectedRoute>
                <Watchlist />
              </ProtectedRoute>
            } />

            <Route path="/compare" element={
              <ProtectedRoute>
                <Compare />
              </ProtectedRoute>
            } />

            <Route path="/usage" element={
              <ProtectedRoute>
                <Usage />
              </ProtectedRoute>
            } />

            <Route path="/portfolio" element={
              <ProtectedRoute>
                <Portfolio />
              </ProtectedRoute>
            } />

            <Route path="/settings" element={
              <ProtectedRoute>
                <Settings />
              </ProtectedRoute>
            } />

            <Route path="/screener" element={
              <ProtectedRoute>
                <Screener />
              </ProtectedRoute>
            } />

            <Route path="/correlation" element={
              <ProtectedRoute>
                <Correlation />
              </ProtectedRoute>
            } />

            <Route path="/chatbot" element={
              <ProtectedRoute>
                <Chatbot />
              </ProtectedRoute>
            } />

            <Route path="/dividends" element={
              <ProtectedRoute>
                <DividendCalendar />
              </ProtectedRoute>
            } />

            <Route path="/earnings" element={
              <ProtectedRoute>
                <EarningsCalendar />
              </ProtectedRoute>
            } />

            <Route path="/risk-dashboard" element={
              <ProtectedRoute>
                <RiskDashboard />
              </ProtectedRoute>
            } />

            <Route path="/paper-trading" element={
              <ProtectedRoute>
                <PaperTrading />
              </ProtectedRoute>
            } />

            <Route path="/system-map" element={<SystemMap />} />

            <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </div>
        </div>
      </Suspense>
      <OfflineIndicator />
    </>
  );
}

export default function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <LanguageProvider>
          <PreferencesProvider>
            <ToastProvider>
              <BrowserRouter>
                <AppContent />
              </BrowserRouter>
            </ToastProvider>
          </PreferencesProvider>
        </LanguageProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}
