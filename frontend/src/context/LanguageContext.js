import { createContext, useContext, useState, useEffect } from "react";
import { getSettings } from "../api/settings";

export const LanguageContext = createContext(null);

export function useLanguage() {
  const context = useContext(LanguageContext);
  if (!context) {
    return { language: "ko", setLanguage: () => {} }; // Default fallback
  }
  return context;
}

export function LanguageProvider({ children }) {
  const [language, setLanguageState] = useState("ko");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Load language from backend settings
    const loadLanguage = async () => {
      try {
        const settings = await getSettings();
        setLanguageState(settings.language || "ko");
      } catch (e) {
        // If not authenticated or error, default to Korean
        setLanguageState("ko");
      } finally {
        setLoading(false);
      }
    };

    loadLanguage();
  }, []);

  const setLanguage = (lang) => {
    setLanguageState(lang);
  };

  return (
    <LanguageContext.Provider value={{ language, setLanguage, loading }}>
      {children}
    </LanguageContext.Provider>
  );
}
