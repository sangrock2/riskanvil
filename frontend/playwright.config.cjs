const { defineConfig, devices } = require("@playwright/test");

const baseURL = (
  process.env.PLAYWRIGHT_BASE_URL ||
  process.env.E2E_BASE_URL ||
  "http://127.0.0.1"
).replace(/\/+$/, "");

module.exports = defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  expect: {
    timeout: 15_000,
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [
    [process.env.CI ? "line" : "list"],
    ["html", { open: "never", outputFolder: "./playwright-report" }],
  ],
  outputDir: "./test-results/playwright",
  use: {
    baseURL,
    headless: true,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
      },
    },
  ],
});
