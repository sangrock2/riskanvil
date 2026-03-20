const { test, expect } = require("@playwright/test");

const EMAIL = process.env.PLAYWRIGHT_DEPLOYED_EMAIL || "";
const PASSWORD = process.env.PLAYWRIGHT_DEPLOYED_PASSWORD || "";

async function resolveLoginField(candidates) {
  for (const locator of candidates) {
    if ((await locator.count()) > 0) {
      return locator.first();
    }
  }

  return candidates[candidates.length - 1].first();
}

async function getLoginForm(page) {
  const testIdForm = page.getByTestId("login-form");
  if ((await testIdForm.count()) > 0) {
    return testIdForm.first();
  }

  return page.locator("form").filter({
    has: page.getByRole("button", { name: /^(\s)*(로그인|Login)(\s)*$/i }),
  }).first();
}

async function getLoginEmailField(page) {
  const form = await getLoginForm(page);
  return resolveLoginField([
    form.getByTestId("login-email-input"),
    form.getByLabel(/이메일|Email/i),
    form.locator("input").first(),
  ]);
}

async function getLoginPasswordField(page) {
  const form = await getLoginForm(page);
  return resolveLoginField([
    form.getByTestId("login-password-input"),
    form.getByLabel(/^(\s)*(비밀번호|Password)(\s)*$/i),
    form.locator("input").nth(1),
  ]);
}

async function expectLoginFormVisible(page) {
  await expect(page.getByRole("heading", { name: /로그인|Login/i })).toBeVisible();
  await expect(await getLoginEmailField(page)).toBeVisible();
  await expect(await getLoginPasswordField(page)).toBeVisible();
}

async function expectPortfolioPageReady(page) {
  const portfolioHeading = page.getByRole("heading", { name: /포트폴리오|Portfolio/i });

  try {
    await expect(portfolioHeading).toBeVisible({ timeout: 15_000 });
  } catch (error) {
    const loadingIndicator = page.getByText(/포트폴리오 로딩 중|loading portfolios/i).first();
    if ((await loadingIndicator.count()) > 0 && (await loadingIndicator.isVisible().catch(() => false))) {
      throw new Error("Portfolio page remained in the loading state for more than 15 seconds.");
    }

    throw error;
  }
}

async function openLoginRoute(page) {
  await page.goto("/login");
  await page.waitForLoadState("domcontentloaded");

  const currentPath = new URL(page.url()).pathname;
  if (!/\/login\/?$/.test(currentPath)) {
    throw new Error(
      `Expected deployed frontend to preserve /login deep link, but ended on ${currentPath}. Check static hosting SPA rewrite rules.`
    );
  }

  await expectLoginFormVisible(page);
}

async function openLoginFromHome(page) {
  await page.goto("/");
  await page.getByRole("link", { name: /로그인|Login/i }).click();
  await expect(page).toHaveURL(/\/login\/?$/);
  await expectLoginFormVisible(page);
}

async function loginWithRetry(page, openLoginPage) {
  let lastError = null;

  for (let attempt = 1; attempt <= 3; attempt += 1) {
    await openLoginPage(page);

    const emailField = await getLoginEmailField(page);
    const passwordField = await getLoginPasswordField(page);
    await emailField.fill(EMAIL);
    await passwordField.fill(PASSWORD);
    await page.getByRole("button", { name: /^(\s)*(로그인|Login)(\s)*$/i }).click();

    try {
      await expect(page).toHaveURL(/\/dashboard$/, { timeout: 25_000 });
      return;
    } catch (error) {
      const loadingButton = page.getByRole("button", { name: /로딩 중|Loading/i }).first();
      if ((await loadingButton.count()) > 0 && (await loadingButton.isVisible().catch(() => false))) {
        throw new Error("Login request stayed pending on /login for more than 25 seconds.");
      }

      lastError = error;
      if (attempt < 3) {
        await page.waitForTimeout(5_000);
      }
    }
  }

  throw lastError;
}

test.describe("deployed smoke", () => {
  test.skip(!EMAIL || !PASSWORD, "requires PLAYWRIGHT_DEPLOYED_EMAIL and PLAYWRIGHT_DEPLOYED_PASSWORD");

  test("direct /login deep link renders the login form", async ({ page }) => {
    await openLoginRoute(page);
  });

  test("homepage navigation, login, protected navigation, and logout work on the deployed frontend", async ({ page }) => {
    test.slow();

    await loginWithRetry(page, openLoginFromHome);
    await expect(page.getByRole("heading", { name: /대시보드|Dashboard/i })).toBeVisible();

    await page.goto("/watchlist");
    await expect(page).toHaveURL(/\/watchlist$/);
    await expect(page.getByText(/관심종목|Watchlist/i).first()).toBeVisible();

    await page.goto("/portfolio");
    await expect(page).toHaveURL(/\/portfolio$/);
    await expectPortfolioPageReady(page);

    await page.getByRole("button", { name: /로그아웃|Logout/i }).click();
    await expect(page).toHaveURL(/\/login/);
    await expectLoginFormVisible(page);
  });
});
