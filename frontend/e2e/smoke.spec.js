const { test, expect } = require("@playwright/test");

const PASSWORD = "Passw0rd!2345";

function uniqueEmail(prefix) {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}@example.com`;
}

async function registerViaApi(page) {
  const email = uniqueEmail("playwright");
  const response = await page.request.post("/api/auth/register", {
    data: {
      email,
      password: PASSWORD,
    },
  });

  expect(response.ok()).toBeTruthy();

  return { email };
}

async function loginViaUi(page, email) {
  await page.goto("/login");
  await expect(page.getByTestId("login-page")).toBeVisible();
  await page.getByTestId("login-email-input").fill(email);
  await page.getByTestId("login-password-input").fill(PASSWORD);
  await page.getByTestId("login-submit").click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByTestId("dashboard-page")).toBeVisible();
}

test("redirects guests from protected routes to login", async ({ page }) => {
  await page.goto("/dashboard");

  await expect(page).toHaveURL(/\/login\?reason=missing/);
  await expect(page.getByTestId("login-page")).toBeVisible();
  await expect(page.getByTestId("login-form")).toBeVisible();
});

test("register UI creates a user session and reaches dashboard", async ({ page }) => {
  test.slow();

  const email = uniqueEmail("playwright_ui");

  await page.goto("/register");
  await expect(page.getByTestId("register-page")).toBeVisible();

  await page.getByTestId("register-email-input").fill(email);
  await page.getByTestId("register-password-input").fill(PASSWORD);
  await page.getByTestId("register-confirm-password-input").fill(PASSWORD);

  await page.getByTestId("register-email-check").click();
  await expect(page.getByTestId("register-email-status")).toContainText(/사용 가능한 이메일|중복 확인에 실패했습니다/);

  await page.getByTestId("register-submit").click();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByTestId("dashboard-page")).toBeVisible();
  await expect(page.getByTestId("navbar-logout")).toBeVisible();
});

test("authenticated user can manage watchlist and portfolio core flows", async ({ page }) => {
  test.slow();

  const portfolioName = `Playwright Portfolio ${Date.now()}`;

  const { email } = await registerViaApi(page);
  await loginViaUi(page, email);

  await page.goto("/watchlist");
  await expect(page.getByTestId("watchlist-page")).toBeVisible();

  await page.getByTestId("watchlist-add-ticker").fill("AAPL");
  await page.getByTestId("watchlist-add-market").selectOption("US");
  await page.getByTestId("watchlist-add-submit").click();

  await expect(page.getByTestId("watchlist-item-AAPL-US")).toBeVisible({ timeout: 20_000 });

  await page.goto("/portfolio");
  await expect(page.getByTestId("portfolio-page")).toBeVisible();

  await page.getByTestId("portfolio-create-open").click();
  await expect(page.getByTestId("portfolio-create-modal")).toBeVisible();
  await page.getByTestId("portfolio-create-name").fill(portfolioName);
  await page.getByTestId("portfolio-create-description").fill("Browser smoke test portfolio");
  await page.getByTestId("portfolio-create-target-return").fill("12");
  await page.getByTestId("portfolio-create-risk-profile").selectOption("moderate");
  await page.getByTestId("portfolio-create-submit").click();

  const createdPortfolioCard = page.getByTestId("portfolio-card").filter({ hasText: portfolioName });
  await expect(createdPortfolioCard).toBeVisible({ timeout: 20_000 });
  await createdPortfolioCard.click();

  await page.getByTestId("portfolio-add-position-open").click();
  await expect(page.getByTestId("portfolio-add-position-modal")).toBeVisible();
  await page.getByTestId("portfolio-position-ticker").fill("AAPL");
  await page.getByTestId("portfolio-position-market").selectOption("US");
  await page.getByTestId("portfolio-position-quantity").fill("10");
  await page.getByTestId("portfolio-position-entry-price").fill("180");
  await page.getByTestId("portfolio-position-submit").click();

  await expect(page.getByTestId("portfolio-position-row-AAPL-US")).toBeVisible({ timeout: 20_000 });
});
