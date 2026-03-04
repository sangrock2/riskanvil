import { useState, useEffect } from "react";
import { getSettings, updateSettings, setupTotp, verifyTotp, disableTotp } from "../api/settings";
import { useToast } from "../components/ui/Toast";
import { useTranslation } from "../hooks/useTranslation";
import { useLanguage } from "../context/LanguageContext";
import { setTheme } from "../utils/theme";
import styles from "../css/Settings.module.css";

export default function Settings() {
  const [settings, setSettings] = useState(null);
  const [loading, setLoading] = useState(false);
  const [showTotpSetup, setShowTotpSetup] = useState(false);
  const [totpSetupData, setTotpSetupData] = useState(null);
  const [totpCode, setTotpCode] = useState("");
  const [disablePassword, setDisablePassword] = useState("");
  const [disableCode, setDisableCode] = useState("");
  const [showDisableForm, setShowDisableForm] = useState(false);
  const toast = useToast();
  const { t } = useTranslation();
  const { setLanguage } = useLanguage();
  const modalOpen = showTotpSetup || showDisableForm;

  useEffect(() => {
    loadSettings();
  }, []);

  useEffect(() => {
    // Listen for theme changes from ThemeToggle
    const handleThemeChange = (e) => {
      setSettings((prev) => {
        if (prev) {
          return { ...prev, theme: e.detail.theme };
        }
        return prev;
      });
    };

    window.addEventListener("themechange", handleThemeChange);

    return () => {
      window.removeEventListener("themechange", handleThemeChange);
    };
  }, []);

  useEffect(() => {
    if (!modalOpen) return undefined;

    const handleEscape = (e) => {
      if (e.key === "Escape") {
        setShowTotpSetup(false);
        setTotpSetupData(null);
        setTotpCode("");
        setShowDisableForm(false);
        setDisablePassword("");
        setDisableCode("");
      }
    };

    window.addEventListener("keydown", handleEscape);
    return () => window.removeEventListener("keydown", handleEscape);
  }, [modalOpen]);

  const closeTotpSetupModal = () => {
    setShowTotpSetup(false);
    setTotpSetupData(null);
    setTotpCode("");
  };

  const closeDisableModal = () => {
    setShowDisableForm(false);
    setDisablePassword("");
    setDisableCode("");
  };

  const loadSettings = async () => {
    setLoading(true);
    try {
      const data = await getSettings();
      setSettings(data);
    } catch (e) {
      toast.error(e.message || t("settings.failedToLoad"));
    } finally {
      setLoading(false);
    }
  };

  const handleUpdate = async (field, value) => {
    try {
      const updated = await updateSettings({ [field]: value });
      setSettings(updated);

      // Update language context when language changes
      if (field === "language") {
        setLanguage(value);
      }

      // Update theme when theme changes
      if (field === "theme") {
        setTheme(value);
      }

      toast.success(t("settings.settingsUpdated"));
    } catch (e) {
      toast.error(e.message || t("settings.failedToUpdate"));
    }
  };

  const handleSetupTotp = async () => {
    try {
      const data = await setupTotp();
      setTotpSetupData(data);
      setShowTotpSetup(true);
    } catch (e) {
      toast.error(e.message || t("settings.failedToSetupTwoFactor"));
    }
  };

  const handleVerifyTotp = async () => {
    if (totpCode.length !== 6) {
      toast.error(t("settings.codeMustBeSixDigits"));
      return;
    }

    try {
      const result = await verifyTotp(totpCode);
      if (result.valid) {
        toast.success(t("settings.twoFactorEnabled"));
        setShowTotpSetup(false);
        setTotpSetupData(null);
        setTotpCode("");
        loadSettings();
      } else {
        toast.error(t("settings.invalidCode"));
      }
    } catch (e) {
      toast.error(e.message || t("settings.verificationFailed"));
    }
  };

  const handleDisableTotp = async () => {
    if (!disablePassword || disableCode.length !== 6) {
      toast.error(t("settings.disableInputRequired"));
      return;
    }

    try {
      await disableTotp(disablePassword, disableCode);
      toast.success(t("settings.twoFactorDisabled"));
      setShowDisableForm(false);
      setDisablePassword("");
      setDisableCode("");
      loadSettings();
    } catch (e) {
      toast.error(e.message || t("settings.failedToDisableTwoFactor"));
    }
  };

  if (loading) return <div className={styles.loading}>{t("loading")}</div>;

  return (
    <div className={styles.container}>
      <h1>{t("settings.title")}</h1>

      {/* Security Section */}
      <section className={styles.section}>
        <h2>{t("settings.security")}</h2>

        <div className={styles.setting}>
          <div className={styles.settingInfo}>
            <strong>{t("settings.twoFactor")}</strong>
            <p>{t("settings.twoFactorDesc")}</p>
          </div>
          <button
            className={settings?.totpEnabled ? styles.btnDanger : styles.btnPrimary}
            onClick={() => {
              if (settings?.totpEnabled) {
                setShowDisableForm(true);
              } else {
                handleSetupTotp();
              }
            }}
          >
            {settings?.totpEnabled ? t("settings.disableTwoFactor") : t("settings.enableTwoFactor")}
          </button>
        </div>

        {/* TOTP Setup Modal */}
        {showTotpSetup && totpSetupData && (
          <div
            className={styles.modal}
            onMouseDown={(e) => {
              if (e.target === e.currentTarget) closeTotpSetupModal();
            }}
          >
            <div
              className={styles.modalContent}
              role="dialog"
              aria-modal="true"
              aria-labelledby="totp-setup-title"
            >
              <h3 id="totp-setup-title">{t("settings.setupTwoFactor")}</h3>

              <div className={styles.totpSetup}>
                <p><strong>{t("settings.step1")}:</strong> {t("settings.scanQR")}</p>
                <div className={styles.qrCode}>
                  <img src={totpSetupData.qrCodeUrl} alt="QR Code" />
                </div>

                <p><strong>{t("settings.step2")}:</strong> {t("settings.saveBackupCodes")}</p>
                <div className={styles.backupCodes}>
                  {totpSetupData.backupCodes.map((code, idx) => (
                    <code key={idx}>{code}</code>
                  ))}
                </div>

                <p><strong>{t("settings.step3")}:</strong> {t("settings.enterCode")}</p>
                <input
                  type="text"
                  className={styles.totpInput}
                  value={totpCode}
                  onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                  placeholder="000000"
                  maxLength={6}
                  autoFocus
                />

                <div className={styles.modalActions}>
                  <button className={styles.btnPrimary} onClick={handleVerifyTotp}>
                    {t("settings.verifyEnable")}
                  </button>
                  <button className={styles.btnSecondary} onClick={closeTotpSetupModal}>
                    {t("cancel")}
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Disable TOTP Form */}
        {showDisableForm && (
          <div
            className={styles.modal}
            onMouseDown={(e) => {
              if (e.target === e.currentTarget) closeDisableModal();
            }}
          >
            <div
              className={styles.modalContent}
              role="dialog"
              aria-modal="true"
              aria-labelledby="totp-disable-title"
            >
              <h3 id="totp-disable-title">{t("settings.disableTwoFactorTitle")}</h3>
              <p>{t("settings.disableTwoFactorDesc")}</p>

              <input
                type="password"
                placeholder={t("auth.password")}
                value={disablePassword}
                onChange={(e) => setDisablePassword(e.target.value)}
                className={styles.input}
                autoFocus
              />

              <input
                type="text"
                placeholder={t("settings.codePlaceholder")}
                value={disableCode}
                onChange={(e) => setDisableCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                className={styles.input}
                maxLength={6}
              />

              <div className={styles.modalActions}>
                <button className={styles.btnDanger} onClick={handleDisableTotp}>
                  {t("settings.disableTwoFactor")}
                </button>
                <button className={styles.btnSecondary} onClick={closeDisableModal}>
                  {t("cancel")}
                </button>
              </div>
            </div>
          </div>
        )}
      </section>

      {/* Notifications Section */}
      <section className={styles.section}>
        <h2>{t("settings.notifications")}</h2>

        <div className={styles.setting}>
          <div className={styles.settingInfo}>
            <strong>{t("settings.emailAlerts")}</strong>
            <p>{t("settings.emailAlertsDesc")}</p>
          </div>
          <label className={styles.switch}>
            <input
              type="checkbox"
              checked={settings?.emailOnAlerts ?? true}
              onChange={(e) => handleUpdate("emailOnAlerts", e.target.checked)}
            />
            <span className={styles.slider}></span>
          </label>
        </div>

        <div className={styles.setting}>
          <div className={styles.settingInfo}>
            <strong>{t("settings.dailySummary")}</strong>
            <p>{t("settings.dailySummaryDesc")}</p>
          </div>
          <label className={styles.switch}>
            <input
              type="checkbox"
              checked={settings?.dailySummaryEnabled ?? false}
              onChange={(e) => handleUpdate("dailySummaryEnabled", e.target.checked)}
            />
            <span className={styles.slider}></span>
          </label>
        </div>
      </section>

      {/* Preferences Section */}
      <section className={styles.section}>
        <h2>{t("settings.preferences")}</h2>

        <div className={styles.setting}>
          <div className={styles.settingInfo}>
            <strong>{t("settings.theme")}</strong>
            <p>{t("settings.themeDesc")}</p>
          </div>
          <select
            value={settings?.theme ?? "dark"}
            onChange={(e) => handleUpdate("theme", e.target.value)}
            className={styles.select}
          >
            <option value="dark">{t("settings.themeDark")}</option>
            <option value="light">{t("settings.themeLight")}</option>
          </select>
        </div>

        <div className={styles.setting}>
          <div className={styles.settingInfo}>
            <strong>{t("settings.language")}</strong>
            <p>{t("settings.languageDesc")}</p>
          </div>
          <select
            value={settings?.language ?? "ko"}
            onChange={(e) => handleUpdate("language", e.target.value)}
            className={styles.select}
          >
            <option value="ko">한국어</option>
            <option value="en">English</option>
          </select>
        </div>

        <div className={styles.setting}>
          <div className={styles.settingInfo}>
            <strong>{t("settings.defaultMarket")}</strong>
            <p>{t("settings.defaultMarketDesc")}</p>
          </div>
          <select
            value={settings?.defaultMarket ?? "US"}
            onChange={(e) => handleUpdate("defaultMarket", e.target.value)}
            className={styles.select}
          >
            <option value="US">{t("common.market.us")} (US)</option>
            <option value="KR">{t("common.market.kr")} (KR)</option>
          </select>
        </div>
      </section>
    </div>
  );
}
