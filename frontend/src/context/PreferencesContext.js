import { createContext, useContext, useState, useEffect, useCallback } from "react";
import { getItem, setItem } from "../utils/storage";

const PreferencesContext = createContext(null);

export function usePreferences() {
  const context = useContext(PreferencesContext);
  if (!context) {
    throw new Error("usePreferences must be used within PreferencesProvider");
  }
  return context;
}

const DEFAULT_PREFERENCES = {
  lastVisitedPage: "/analyze",
  analyze: {
    market: "NASDAQ",
    horizon: 30,
    riskProfile: "MODERATE",
  },
  watchlist: {
    sortBy: "ticker",
    sortOrder: "asc",
    selectedTags: [],
  },
  compare: {
    chartType: "line",
    dateRange: "1M",
  },
  chart: {
    zoomLevel: 1,
    dateRange: null,
  },
  searchHistory: [],
  recentAnalyses: [],
};

export function PreferencesProvider({ children }) {
  const [preferences, setPreferences] = useState(() => {
    // 초기 로드 시 localStorage에서 복원
    const saved = getItem("preferences", DEFAULT_PREFERENCES);
    return { ...DEFAULT_PREFERENCES, ...saved };
  });

  // preferences 변경 시 localStorage에 저장
  useEffect(() => {
    setItem("preferences", preferences);
  }, [preferences]);

  const updatePreference = useCallback((key, value) => {
    setPreferences((prev) => {
      // Nested key 지원 (e.g., "analyze.market")
      if (key.includes(".")) {
        const [parent, child] = key.split(".");
        return {
          ...prev,
          [parent]: {
            ...prev[parent],
            [child]: value,
          },
        };
      }

      return {
        ...prev,
        [key]: value,
      };
    });
  }, []);

  const getPreference = useCallback(
    (key, defaultValue = null) => {
      if (key.includes(".")) {
        const [parent, child] = key.split(".");
        return preferences[parent]?.[child] ?? defaultValue;
      }
      return preferences[key] ?? defaultValue;
    },
    [preferences]
  );

  const resetPreferences = useCallback(() => {
    setPreferences(DEFAULT_PREFERENCES);
  }, []);

  // 검색 히스토리 추가
  const addSearchHistory = useCallback((query) => {
    setPreferences((prev) => {
      const history = prev.searchHistory || [];
      const filtered = history.filter((q) => q !== query);
      const updated = [query, ...filtered].slice(0, 10); // 최대 10개
      return {
        ...prev,
        searchHistory: updated,
      };
    });
  }, []);

  // 최근 분석 추가
  const addRecentAnalysis = useCallback((analysis) => {
    setPreferences((prev) => {
      const recent = prev.recentAnalyses || [];
      const filtered = recent.filter(
        (a) => !(a.ticker === analysis.ticker && a.market === analysis.market)
      );
      const updated = [analysis, ...filtered].slice(0, 20); // 최대 20개
      return {
        ...prev,
        recentAnalyses: updated,
      };
    });
  }, []);

  const value = {
    preferences,
    updatePreference,
    getPreference,
    resetPreferences,
    addSearchHistory,
    addRecentAnalysis,
  };

  return (
    <PreferencesContext.Provider value={value}>
      {children}
    </PreferencesContext.Provider>
  );
}
