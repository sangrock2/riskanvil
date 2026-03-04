import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { setTokens } from "../auth/token";
import { apiFetch } from "../api/http";
import { validateRegisterForm } from "../utils/validators";
import { useTranslation } from "../hooks/useTranslation";
import styles from "../css/Register.module.css";

export default function Register() {
    const { t } = useTranslation();
    const nav = useNavigate();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [err, setErr] = useState("");
    const [fieldErrors, setFieldErrors] = useState({});
    const [showPassword, setShowPassword] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    async function submit(e) {
        e?.preventDefault();
        setErr("");
        setFieldErrors({});

        const validation = validateRegisterForm({ email, password }, t);
        if (!validation.valid) {
            setFieldErrors(validation.errors);
            return;
        }

        try {
            setSubmitting(true);
            const data = await apiFetch("/api/auth/register", {
                method: "POST",
                body: JSON.stringify({ email, password }),
            });

            setTokens(data.accessToken, data.refreshToken);
            nav("/dashboard");
        } catch (e2) {
            setErr(e2.message);
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

                <button className={styles.button} type="submit" disabled={submitting}>
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
