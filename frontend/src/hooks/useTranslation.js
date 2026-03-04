import { useLanguage } from "../context/LanguageContext";
import { getTranslation } from "../i18n/translations";

export function useTranslation() {
  const { language } = useLanguage();

  const t = (key, params) => {
    return getTranslation(language, key, params);
  };

  return { t, language };
}
