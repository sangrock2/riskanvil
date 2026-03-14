import { describe, expect, test, vi } from "vitest";
import { createWebVitalReporter, getWebVitalRating } from "./reportWebVitals";

describe("getWebVitalRating", () => {
  test("classifies good metrics from thresholds", () => {
    expect(getWebVitalRating({ name: "LCP", value: 2200 })).toBe("good");
    expect(getWebVitalRating({ name: "TTFB", value: 900 })).toBe("needs-improvement");
    expect(getWebVitalRating({ name: "CLS", value: 0.4 })).toBe("poor");
  });

  test("respects native rating when provided", () => {
    expect(getWebVitalRating({ name: "FID", value: 40, rating: "poor" })).toBe("poor");
  });
});

describe("createWebVitalReporter", () => {
  test("reports degraded vitals to sentry", () => {
    const setLevel = vi.fn();
    const setTag = vi.fn();
    const setContext = vi.fn();
    const captureMessage = vi.fn();
    const sentry = {
      withScope(callback) {
        callback({ setLevel, setTag, setContext });
      },
      captureMessage,
    };
    const logger = vi.fn();

    const report = createWebVitalReporter({ sentry, logger });
    report({
      id: "vital-1",
      name: "LCP",
      value: 4600,
      delta: 200,
      navigationType: "navigate",
    });

    expect(logger).toHaveBeenCalledTimes(1);
    expect(setLevel).toHaveBeenCalledWith("error");
    expect(setTag).toHaveBeenCalledWith("web_vital_name", "LCP");
    expect(setTag).toHaveBeenCalledWith("web_vital_rating", "poor");
    expect(setContext).toHaveBeenCalledWith("web_vital", expect.objectContaining({
      name: "LCP",
      rating: "poor",
      value: 4600,
    }));
    expect(captureMessage).toHaveBeenCalledWith("web_vital_lcp");
  });

  test("does not report healthy vitals to sentry", () => {
    const captureMessage = vi.fn();
    const sentry = {
      withScope(callback) {
        callback({});
      },
      captureMessage,
    };

    const report = createWebVitalReporter({ sentry });
    report({
      id: "vital-2",
      name: "FCP",
      value: 1200,
      delta: 20,
      navigationType: "navigate",
    });

    expect(captureMessage).not.toHaveBeenCalled();
  });
});
