import { useState, useEffect } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { apiFetch } from "../api/http";
import { setTokens } from "../auth/token";
import { validateLoginForm } from "../utils/validators";
import { toUserErrorMessage } from "../utils/errorMessage";
import { useTranslation } from "../hooks/useTranslation";
import styles from "../css/Login.module.css";

export default function Login() {
    const { t } = useTranslation();
    const nav = useNavigate();
    const [searchParams] = useSearchParams();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [err, setErr] = useState("");
    const [fieldErrors, setFieldErrors] = useState({});
    const [infoMessage, setInfoMessage] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    // 2FA state
    const [requires2FA, setRequires2FA] = useState(false);
    const [pendingToken, setPendingToken] = useState("");
    const [totpCode, setTotpCode] = useState("");
    const [useBackupCode, setUseBackupCode] = useState(false);
    const [backupCode, setBackupCode] = useState("");
    const [verifying, setVerifying] = useState(false);

    // Check for redirect reason (expired/missing token/inactive/logout)
    useEffect(() => {
        const reason = searchParams.get("reason");
        if (reason === "expired") {
            setInfoMessage(t("auth.sessionExpired"));
        } else if (reason === "missing") {
            setInfoMessage(t("auth.loginRequired"));
        } else if (reason === "inactive") {
            setInfoMessage(t("auth.inactiveSession"));
        } else if (reason === "logout") {
            setInfoMessage(t("auth.loggedOut"));
        }
    }, [searchParams, t]);

    async function login() {
        setErr("");

        const data = await apiFetch("/api/auth/login", {
            method: "POST",
            body: JSON.stringify({ email, password }),
        });

        if (data.requires2FA) {
            // 2FA required - show TOTP input
            setPendingToken(data.pendingToken);
            setRequires2FA(true);
        } else {
            setTokens(data.accessToken, data.refreshToken);
            nav("/dashboard");
        }
    }

    async function verify2FA() {
        setErr("");
        setVerifying(true);

        try {
            const body = { pendingToken };
            if (useBackupCode) {
                body.backupCode = backupCode;
            } else {
                body.totpCode = totpCode;
            }

            const data = await apiFetch("/api/auth/verify-2fa", {
                method: "POST",
                body: JSON.stringify(body),
            });

            setTokens(data.accessToken, data.refreshToken);
            nav("/dashboard");
        } catch (e) {
            setErr(toUserErrorMessage(e, t, "settings.verificationFailed", "twoFactorVerify"));
        } finally {
            setVerifying(false);
        }
    }

    async function submit(e) {
        e.preventDefault();
        setFieldErrors({});

        const validation = validateLoginForm({ email, password }, t);
        if (!validation.valid) {
            setFieldErrors(validation.errors);
            return;
        }

        try {
            setSubmitting(true);
            await login();
        } catch (e2) {
            setErr(toUserErrorMessage(e2, t, "auth.loginRequired", "login"));
        } finally {
            setSubmitting(false);
        }
    }

    function handleBackFrom2FA() {
        setRequires2FA(false);
        setPendingToken("");
        setTotpCode("");
        setBackupCode("");
        setUseBackupCode(false);
        setErr("");
    }

    // 2FA verification screen
    if (requires2FA) {
        return (
            <div className={styles.container}>
                <h2>{t("auth.twoFactorTitle")}</h2>
                <p className={styles.twoFactorDesc}>
                    {t("auth.twoFactorDesc")}
                </p>

                {!useBackupCode ? (
                    <div className={styles.row}>
                        <label>{t("auth.twoFactorCode")}</label>
                        <input
                            className={styles.input}
                            value={totpCode}
                            onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                            placeholder="000000"
                            maxLength={6}
                            autoComplete="one-time-code"
                            inputMode="numeric"
                            autoFocus
                        />
                    </div>
                ) : (
                    <div className={styles.row}>
                        <label>{t("auth.backupCode")}</label>
                        <input
                            className={styles.input}
                            value={backupCode}
                            onChange={(e) => setBackupCode(e.target.value.toUpperCase())}
                            placeholder="XXXXXXXXXX"
                            autoComplete="off"
                            autoFocus
                        />
                    </div>
                )}

                <button
                    className={styles.button}
                    onClick={verify2FA}
                    disabled={verifying || (!useBackupCode ? totpCode.length < 6 : backupCode.length < 6)}
                >
                    {verifying ? t("auth.verifying") : t("auth.verify")}
                </button>

                {err && <div className={styles.error}>{err}</div>}

                <div className={`${styles.linkText} ${styles.spacedLink}`}>
                    <button
                        className={styles.linkBtn}
                        onClick={() => { setUseBackupCode(!useBackupCode); setErr(""); }}
                    >
                        {useBackupCode ? t("auth.useTotpCode") : t("auth.useBackupCode")}
                    </button>
                </div>

                <div className={styles.linkText}>
                    <button
                        className={styles.mutedBtn}
                        onClick={handleBackFrom2FA}
                    >
                        ← {t("auth.backToLogin")}
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className={styles.container}>
            <h2>{t("auth.login")}</h2>

            {infoMessage && (
                <div className={styles.info}>
                    {infoMessage}
                </div>
            )}

            <form onSubmit={submit}>
                <div className={styles.row}>
                    <label>{t("auth.email")}</label>
                    <input
                        className={styles.input}
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        autoComplete="email"
                    />
                    {fieldErrors.email && <div className={styles.fieldError}>{fieldErrors.email}</div>}
                </div>

                <div className={styles.row}>
                    <label>{t("auth.password")}</label>
                    <div className={styles.passwordWrap}>
                        <input
                            className={`${styles.input} ${styles.passwordInput}`}
                            type={showPassword ? "text" : "password"}
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            autoComplete="current-password"
                        />
                        <button
                            type="button"
                            className={styles.passwordToggle}
                            onClick={() => setShowPassword((prev) => !prev)}
                            aria-label={showPassword ? "Hide password" : "Show password"}
                            title={showPassword ? "Hide password" : "Show password"}
                        >
                            {showPassword ? "🙈" : "👁️"}
                        </button>
                    </div>
                    {fieldErrors.password && <div className={styles.fieldError}>{fieldErrors.password}</div>}
                </div>

                <button className={styles.button} type="submit" disabled={submitting}>
                    {submitting ? t("common.loading") : t("auth.loginButton")}
                </button>

                {err && <div className={styles.error}>{err}</div>}
            </form>

            <div className={styles.linkText}>
                {t("auth.noAccount")} <Link to="/register">{t("auth.register")}</Link>
            </div>
        </div>
    );
}
