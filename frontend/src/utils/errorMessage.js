function readApiError(error) {
  if (!error || typeof error !== "object") {
    return null;
  }
  return error.apiError && typeof error.apiError === "object" ? error.apiError : null;
}

export function getErrorStatus(error) {
  return Number(error?.status || readApiError(error)?.status || 0);
}

export function getErrorCode(error) {
  const code = error?.code || readApiError(error)?.error || "";
  return typeof code === "string" ? code.toLowerCase() : "";
}

export function getErrorPath(error) {
  const path = error?.path || readApiError(error)?.path || "";
  return typeof path === "string" ? path : "";
}

export function getErrorText(error) {
  const apiMessage = readApiError(error)?.message;
  if (typeof apiMessage === "string" && apiMessage.trim()) {
    return apiMessage.trim();
  }

  if (typeof error?.message === "string" && error.message.trim()) {
    return error.message.trim();
  }

  return "";
}

export function errorMessageIncludes(error, ...patterns) {
  const text = getErrorText(error).toLowerCase();
  return patterns.some((pattern) => text.includes(String(pattern).toLowerCase()));
}

function resolveFallbackMessage(t, fallbackKey) {
  if (!fallbackKey) {
    return t("errors.genericRequest");
  }
  return t(fallbackKey);
}

export function toUserErrorMessage(error, t, fallbackKey, context = "") {
  const status = getErrorStatus(error);
  const code = getErrorCode(error);
  const path = getErrorPath(error);
  const text = getErrorText(error).toLowerCase();

  if (error?.name === "TypeError" || text.includes("failed to fetch")) {
    return t("errors.network");
  }

  if (status === 429) {
    return t("errors.tooManyRequests");
  }

  if (
    text.includes("invalid credentials") ||
    text.includes("bad credentials")
  ) {
    return t("errors.invalidCredentials");
  }

  if (
    text.includes("invalid 2fa code") ||
    text.includes("invalid or expired pending token")
  ) {
    return t("settings.invalidCode");
  }

  if (text.includes("already exists")) {
    if (context === "watchlistAdd" || path === "/api/watchlist") {
      return t("watchlist.duplicateItem");
    }
    if (context === "portfolioCreate") {
      return t("portfolio.duplicatePortfolio");
    }
    if (context === "portfolioAddPosition") {
      return t("portfolio.duplicatePosition");
    }
    if (context === "register" || text.includes("email already exists")) {
      return t("auth.emailTaken");
    }
    return t("errors.alreadyExists");
  }

  if (status === 401 || text.includes("unauthenticated")) {
    return t("auth.loginRequired");
  }

  if (status === 403 || code === "forbidden" || text.includes("forbidden")) {
    return t("errors.forbidden");
  }

  if (status === 404) {
    return t("errors.notFound");
  }

  if (status >= 500) {
    return t("errors.serverTemporary");
  }

  return resolveFallbackMessage(t, fallbackKey);
}
