import React from "react";
import styles from "../css/ErrorBoundary.module.css";
import { LanguageContext } from "../context/LanguageContext";
import { getTranslation } from "../i18n/translations";

export default class ErrorBoundary extends React.Component {
  static contextType = LanguageContext;

  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, errorInfo: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true };
  }

  componentDidCatch(error, errorInfo) {
    console.error("ErrorBoundary caught an error:", error, errorInfo);
    this.setState({
      error,
      errorInfo,
    });

    // Optional: Send error to logging service
    // logErrorToService(error, errorInfo);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
    window.location.href = "/";
  };

  handleReload = () => {
    window.location.reload();
  };

  render() {
    const language = this.context?.language || "ko";
    const t = (key, params) => getTranslation(language, key, params);

    if (this.state.hasError) {
      return (
        <div className={styles.container}>
          <div className={styles.card}>
            <div className={styles.icon}>⚠️</div>
            <h1 className={styles.title}>{t("errorBoundary.title")}</h1>
            <p className={styles.message}>
              {t("errorBoundary.messageLine1")}
              <br />
              {t("errorBoundary.messageLine2")}
            </p>

            {this.state.error && (
              <details className={styles.details}>
                <summary className={styles.summary}>{t("errorBoundary.details")}</summary>
                <div className={styles.errorDetails}>
                  <p>
                    <strong>{t("errorBoundary.errorLabel")}:</strong> {this.state.error.toString()}
                  </p>
                  {this.state.errorInfo && (
                    <pre className={styles.stackTrace}>
                      {this.state.errorInfo.componentStack}
                    </pre>
                  )}
                </div>
              </details>
            )}

            <div className={styles.actions}>
              <button className={styles.btnPrimary} onClick={this.handleReset}>
                {t("errorBoundary.goHome")}
              </button>
              <button className={styles.btn} onClick={this.handleReload}>
                {t("errorBoundary.refreshPage")}
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
