import * as Sentry from "@sentry/react";

export const VITAL_THRESHOLDS = {
  CLS: { good: 0.1, needsImprovement: 0.25 },
  FCP: { good: 1800, needsImprovement: 3000 },
  FID: { good: 100, needsImprovement: 300 },
  LCP: { good: 2500, needsImprovement: 4000 },
  TTFB: { good: 800, needsImprovement: 1800 },
};

export function getWebVitalRating(metric) {
  if (!metric || !metric.name) {
    return "unknown";
  }

  if (metric.rating) {
    return metric.rating;
  }

  const threshold = VITAL_THRESHOLDS[metric.name];
  if (!threshold) {
    return "unknown";
  }

  const value = Number(metric.value);
  if (!Number.isFinite(value)) {
    return "unknown";
  }

  if (value <= threshold.good) {
    return "good";
  }
  if (value <= threshold.needsImprovement) {
    return "needs-improvement";
  }
  return "poor";
}

function toSeverity(rating) {
  if (rating === "needs-improvement") {
    return "warning";
  }
  if (rating === "poor") {
    return "critical";
  }
  return "ok";
}

export function createWebVitalReporter({ sentry = Sentry, logger } = {}) {
  return metric => {
    if (!metric || !metric.name) {
      return;
    }

    const rating = getWebVitalRating(metric);
    const severity = toSeverity(rating);
    const payload = {
      id: metric.id,
      name: metric.name,
      value: metric.value,
      delta: metric.delta,
      rating,
      navigationType: metric.navigationType,
    };

    if (typeof logger === "function") {
      logger(payload);
    }

    if (severity === "ok") {
      return;
    }

    if (!sentry || typeof sentry.withScope !== "function" || typeof sentry.captureMessage !== "function") {
      return;
    }

    sentry.withScope(scope => {
      if (typeof scope.setLevel === "function") {
        scope.setLevel(severity === "critical" ? "error" : "warning");
      }
      if (typeof scope.setTag === "function") {
        scope.setTag("web_vital_name", metric.name);
        scope.setTag("web_vital_rating", rating);
      }
      if (typeof scope.setContext === "function") {
        scope.setContext("web_vital", payload);
      }

      sentry.captureMessage(`web_vital_${metric.name.toLowerCase()}`);
    });
  };
}

const defaultLogger = import.meta.env.DEV
  ? metric => {
      console.info("[web-vitals]", metric);
    }
  : undefined;

const defaultReporter = createWebVitalReporter({ logger: defaultLogger });

const reportWebVitals = (onPerfEntry = defaultReporter) => {
  if (onPerfEntry && onPerfEntry instanceof Function) {
    import("web-vitals").then(({ getCLS, getFID, getFCP, getLCP, getTTFB }) => {
      getCLS(onPerfEntry);
      getFID(onPerfEntry);
      getFCP(onPerfEntry);
      getLCP(onPerfEntry);
      getTTFB(onPerfEntry);
    });
  }
};

export default reportWebVitals;
