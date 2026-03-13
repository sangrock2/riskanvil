import React from 'react';
import ReactDOM from 'react-dom/client';
import * as Sentry from '@sentry/react';
import './index.css';
import App from './App';
import reportWebVitals from './reportWebVitals';
import { initTheme } from './utils/theme';
import * as serviceWorker from './utils/serviceWorker';

const sentryDsn = import.meta.env.VITE_SENTRY_DSN || import.meta.env.REACT_APP_SENTRY_DSN;
const sentryEnvironment =
  import.meta.env.VITE_SENTRY_ENVIRONMENT ||
  import.meta.env.MODE;
const sentryRelease = import.meta.env.VITE_APP_VERSION || undefined;

function parseSampleRate(raw, fallback) {
  const value = Number(raw);
  if (Number.isFinite(value) && value >= 0 && value <= 1) {
    return value;
  }
  return fallback;
}

// Sentry 에러 모니터링 (VITE_SENTRY_DSN 환경변수가 설정된 경우에만 활성화)
if (sentryDsn) {
  Sentry.init({
    dsn: sentryDsn,
    environment: sentryEnvironment,
    release: sentryRelease,
    tracesSampleRate: parseSampleRate(import.meta.env.VITE_SENTRY_TRACES_SAMPLE_RATE, 0.1),
    replaysSessionSampleRate: 0,
    replaysOnErrorSampleRate: 0,
  });
}
// 앱 시작 시 테마 초기화
initTheme();

// Service Worker 등록 (오프라인 지원)
serviceWorker.register();

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
