import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { setTokens } from "../auth/token";
import { apiFetch } from "../api/http";
import { validateEmail, validateRegisterForm } from "../utils/validators";
import { useTranslation } from "../hooks/useTranslation";
import styles from "../css/Register.module.css";

export default function Register() {
    const { t } = useTranslation();
    const nav = useNavigate();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [err, setErr] = useState("");
    const [fieldErrors, setFieldErrors] = useState({});
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [checkingEmail, setCheckingEmail] = useState(false);
    const [emailCheckStatus, setEmailCheckStatus] = useState("idle"); // idle|checking|available|taken|error
    const [emailCheckMessage, setEmailCheckMessage] = useState("");
    const [checkedEmail, setCheckedEmail] = useState("");

    function normalizedEmail() {
        return email.trim().toLowerCase();
    }

    function onEmailChange(nextEmail) {
        setEmail(nextEmail);

        const nextNormalized = nextEmail.trim().toLowerCase();
        if (nextNormalized !== checkedEmail) {
            setEmailCheckStatus("idle");
            setEmailCheckMessage("");
        }

        if (fieldErrors.email) {
            setFieldErrors((prev) => ({ ...prev, email: "" }));
        }
    }

    async function checkEmailAvailability() {
        setErr("");
        setFieldErrors((prev) => ({ ...prev, email: "" }));

        const emailValidation = validateEmail(email, t);
        if (!emailValidation.valid) {
            setFieldErrors((prev) => ({ ...prev, email: emailValidation.message }));
            setEmailCheckStatus("error");
            setEmailCheckMessage(emailValidation.message);
            return;
        }

        const emailToCheck = normalizedEmail();

        try {
            setCheckingEmail(true);
            setEmailCheckStatus("checking");
            setEmailCheckMessage(t("auth.emailChecking"));

            const result = await apiFetch("/api/auth/check-email", {
                method: "POST",
                body: JSON.stringify({ email: emailToCheck }),
                retry: 0,
            });

            setCheckedEmail(emailToCheck);

            if (result?.available) {
                setEmailCheckStatus("available");
                setEmailCheckMessage(t("auth.emailAvailable"));
            } else {
                setEmailCheckStatus("taken");
                setEmailCheckMessage(t("auth.emailTaken"));
            }
        } catch (checkErr) {
            setCheckedEmail(emailToCheck);
            setEmailCheckStatus("error");
            setEmailCheckMessage(t("auth.emailCheckFailedFallback"));
        } finally {
            setCheckingEmail(false);
        }
    }

    async function submit(e) {
        e?.preventDefault();
        setErr("");
        setFieldErrors({});

        const normalized = normalizedEmail();
        const validation = validateRegisterForm({ email: normalized, password, confirmPassword }, t);
        if (!validation.valid) {
            setFieldErrors(validation.errors);
            return;
        }

        const checkedCurrentEmail = checkedEmail === normalized;
        const isTaken = checkedCurrentEmail && emailCheckStatus === "taken";
        const isCheckedAvailable = checkedCurrentEmail && emailCheckStatus === "available";
        const isCheckError = checkedCurrentEmail && emailCheckStatus === "error";

        if (isTaken) {
            setFieldErrors((prev) => ({
                ...prev,
                email: t("auth.emailTaken"),
            }));
            return;
        }

        if (checkingEmail) {
            setFieldErrors((prev) => ({
                ...prev,
                email: t("auth.emailChecking"),
            }));
            return;
        }

        if (!isCheckedAvailable && !isCheckError) {
            setFieldErrors((prev) => ({
                ...prev,
                email: t("auth.emailCheckRequired"),
            }));
            return;
        }

        try {
            setSubmitting(true);
            const data = await apiFetch("/api/auth/register", {
                method: "POST",
                body: JSON.stringify({ email: normalized, password }),
            });

            setTokens(data.accessToken, data.refreshToken);
            nav("/dashboard");
        } catch (e2) {
            setErr(e2.message);
            if (typeof e2?.message === "string" && e2.message.toLowerCase().includes("email already exists")) {
                setEmailCheckStatus("taken");
                setEmailCheckMessage(t("auth.emailTaken"));
            }
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className={styles.container}>
            <h2>{t("auth.register")}</h2>

            <form onSubmit={submit}>
                <div className={styles.row}>
                    <label>{t("auth.email")}</label>
                    <div className={styles.emailCheckWrap}>
                        <input
                            className={styles.input}
                            value={email}
                            onChange={(e) => onEmailChange(e.target.value)}
                            autoComplete="email"
                        />
                        <button
                            type="button"
                            className={styles.checkButton}
                            onClick={checkEmailAvailability}
                            disabled={checkingEmail || submitting}
                        >
                            {checkingEmail ? t("auth.emailChecking") : t("auth.emailCheckButton")}
                        </button>
                    </div>
                    {emailCheckMessage && (
                        <div
                            className={`${styles.emailCheckStatus} ${
                                emailCheckStatus === "available"
                                    ? styles.emailCheckStatusAvailable
                                    : emailCheckStatus === "taken" || emailCheckStatus === "error"
                                        ? styles.emailCheckStatusError
                                        : styles.emailCheckStatusDefault
                            }`}
                        >
                            {emailCheckMessage}
                        </div>
                    )}
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
                            autoComplete="new-password"
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

                <div className={styles.row}>
                    <label>{t("auth.confirmPassword")}</label>
                    <div className={styles.passwordWrap}>
                        <input
                            className={`${styles.input} ${styles.passwordInput}`}
                            type={showConfirmPassword ? "text" : "password"}
                            value={confirmPassword}
                            onChange={(e) => setConfirmPassword(e.target.value)}
                            autoComplete="new-password"
                        />
                        <button
                            type="button"
                            className={styles.passwordToggle}
                            onClick={() => setShowConfirmPassword((prev) => !prev)}
                            aria-label={showConfirmPassword ? "Hide password" : "Show password"}
                            title={showConfirmPassword ? "Hide password" : "Show password"}
                        >
                            {showConfirmPassword ? "🙈" : "👁️"}
                        </button>
                    </div>
                    {fieldErrors.confirmPassword && (
                        <div className={styles.fieldError}>{fieldErrors.confirmPassword}</div>
                    )}
                </div>

                <button className={styles.button} type="submit" disabled={submitting || checkingEmail}>
                    {submitting ? t("common.loading") : t("auth.registerButton")}
                </button>
                {err && <div className={styles.error}>{err}</div>}
            </form>

            <div className={styles.linkText}>
                {t("auth.haveAccount")} <Link to="/login">{t("auth.login")}</Link>
            </div>
        </div>
    );
}
