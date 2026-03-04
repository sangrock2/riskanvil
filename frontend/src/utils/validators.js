/**
 * Form validation utilities for the Stock-AI frontend
 */

function resolveMessage(t, key, fallback, params) {
  if (typeof t === "function") {
    const translated = t(key, params);
    if (translated && translated !== key) {
      return translated;
    }
  }
  return fallback;
}

/**
 * Validates email format
 * @param {string} email - Email to validate
 * @returns {{ valid: boolean, message: string }}
 */
export function validateEmail(email, t) {
  if (!email || typeof email !== "string") {
    return {
      valid: false,
      message: resolveMessage(t, "validation.emailRequired", "이메일을 입력해 주세요."),
    };
  }

  const trimmed = email.trim();
  if (trimmed.length === 0) {
    return {
      valid: false,
      message: resolveMessage(t, "validation.emailRequired", "이메일을 입력해 주세요."),
    };
  }

  // RFC 5322 simplified email regex
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(trimmed)) {
    return {
      valid: false,
      message: resolveMessage(t, "validation.emailInvalid", "이메일 형식이 올바르지 않습니다."),
    };
  }

  return { valid: true, message: "" };
}

/**
 * Validates password strength
 * @param {string} password - Password to validate
 * @param {object} options - Validation options
 * @param {number} options.minLength - Minimum length (default: 8)
 * @returns {{ valid: boolean, message: string }}
 */
export function validatePassword(password, options = {}, t) {
  const { minLength = 8 } = options;

  if (!password || typeof password !== "string") {
    return {
      valid: false,
      message: resolveMessage(t, "validation.passwordRequired", "비밀번호를 입력해 주세요."),
    };
  }

  if (password.length < minLength) {
    return {
      valid: false,
      message: resolveMessage(
        t,
        "validation.passwordMinLength",
        `비밀번호는 최소 ${minLength}자 이상이어야 합니다.`,
        { minLength }
      ),
    };
  }

  return { valid: true, message: "" };
}

/**
 * Validates that password and confirm password match
 * @param {string} password - Password
 * @param {string} confirmPassword - Confirm password
 * @returns {{ valid: boolean, message: string }}
 */
export function validatePasswordMatch(password, confirmPassword, t) {
  if (password !== confirmPassword) {
    return {
      valid: false,
      message: resolveMessage(t, "validation.passwordMismatch", "비밀번호가 일치하지 않습니다."),
    };
  }

  return { valid: true, message: "" };
}

/**
 * Validates a required field
 * @param {string} value - Value to validate
 * @param {string} fieldName - Name of the field for error message
 * @returns {{ valid: boolean, message: string }}
 */
export function validateRequired(value, fieldName = "필드", t) {
  if (!value || (typeof value === "string" && value.trim().length === 0)) {
    return {
      valid: false,
      message: resolveMessage(
        t,
        "validation.fieldRequired",
        `${fieldName} 입력은 필수입니다.`,
        { field: fieldName }
      ),
    };
  }

  return { valid: true, message: "" };
}

/**
 * Validates a stock ticker symbol
 * @param {string} ticker - Ticker symbol to validate
 * @returns {{ valid: boolean, message: string }}
 */
export function validateTicker(ticker, t) {
  if (!ticker || typeof ticker !== "string") {
    return {
      valid: false,
      message: resolveMessage(t, "validation.tickerRequired", "종목 코드를 입력해 주세요."),
    };
  }

  const trimmed = ticker.trim().toUpperCase();
  if (trimmed.length === 0) {
    return {
      valid: false,
      message: resolveMessage(t, "validation.tickerRequired", "종목 코드를 입력해 주세요."),
    };
  }

  // Ticker format: 1-10 alphanumeric characters (supports US and KR tickers)
  const tickerRegex = /^[A-Z0-9]{1,10}$/;
  if (!tickerRegex.test(trimmed)) {
    return {
      valid: false,
      message: resolveMessage(
        t,
        "validation.tickerInvalid",
        "종목 코드 형식이 올바르지 않습니다. (영문/숫자 1-10자)"
      ),
    };
  }

  return { valid: true, message: "" };
}

/**
 * Validates a number is positive
 * @param {number|string} value - Value to validate
 * @param {string} fieldName - Name of the field for error message
 * @returns {{ valid: boolean, message: string }}
 */
export function validatePositiveNumber(value, fieldName = "값", t) {
  const num = Number(value);

  if (Number.isNaN(num)) {
    return {
      valid: false,
      message: resolveMessage(
        t,
        "validation.numberRequired",
        `${fieldName}은(는) 숫자여야 합니다.`,
        { field: fieldName }
      ),
    };
  }

  if (num <= 0) {
    return {
      valid: false,
      message: resolveMessage(
        t,
        "validation.numberPositive",
        `${fieldName}은(는) 0보다 커야 합니다.`,
        { field: fieldName }
      ),
    };
  }

  return { valid: true, message: "" };
}

/**
 * Validates login form
 * @param {object} form - Form data
 * @param {string} form.email - Email
 * @param {string} form.password - Password
 * @returns {{ valid: boolean, errors: { email?: string, password?: string } }}
 */
export function validateLoginForm({ email, password }, t) {
  const errors = {};

  const emailResult = validateEmail(email, t);
  if (!emailResult.valid) {
    errors.email = emailResult.message;
  }

  const passwordResult = validateRequired(
    password,
    typeof t === "function" ? t("auth.password") : "비밀번호",
    t
  );
  if (!passwordResult.valid) {
    errors.password = passwordResult.message;
  }

  return {
    valid: Object.keys(errors).length === 0,
    errors,
  };
}

/**
 * Validates registration form
 * @param {object} form - Form data
 * @param {string} form.email - Email
 * @param {string} form.password - Password
 * @returns {{ valid: boolean, errors: { email?: string, password?: string } }}
 */
export function validateRegisterForm({ email, password }, t) {
  const errors = {};

  const emailResult = validateEmail(email, t);
  if (!emailResult.valid) {
    errors.email = emailResult.message;
  }

  const passwordResult = validatePassword(password, { minLength: 8 }, t);
  if (!passwordResult.valid) {
    errors.password = passwordResult.message;
  }

  return {
    valid: Object.keys(errors).length === 0,
    errors,
  };
}

/**
 * Validates maximum length for text fields
 * @param {string} value - Input value
 * @param {number} max - Maximum length
 * @param {string} fieldName - Field name for error message
 * @returns {{ valid: boolean, message: string }}
 */
export function validateMaxLength(value, max, fieldName = "필드", t) {
  if (!value) {
    return { valid: true, message: "" };
  }

  if (typeof value === "string" && value.length > max) {
    return {
      valid: false,
      message: resolveMessage(
        t,
        "validation.maxLength",
        `${fieldName}은(는) 최대 ${max}자까지 입력 가능합니다.`,
        { field: fieldName, max }
      ),
    };
  }

  return { valid: true, message: "" };
}

/**
 * Validates notes field (max 500 characters)
 * @param {string} notes - Notes text
 * @returns {{ valid: boolean, message: string }}
 */
export function validateNotes(notes, t) {
  const fieldName = typeof t === "function" ? t("watchlist.notes") : "메모";
  return validateMaxLength(notes, 500, fieldName, t);
}

/**
 * Validates array length
 * @param {Array} arr - Array to validate
 * @param {number} max - Maximum array length
 * @param {string} fieldName - Field name
 * @returns {{ valid: boolean, message: string }}
 */
export function validateArrayLength(arr, max, fieldName = "배열", t) {
  if (!Array.isArray(arr)) {
    return { valid: true, message: "" };
  }

  if (arr.length > max) {
    return {
      valid: false,
      message: resolveMessage(
        t,
        "validation.maxItems",
        `${fieldName}은(는) 최대 ${max}개까지 가능합니다.`,
        { field: fieldName, max }
      ),
    };
  }

  return { valid: true, message: "" };
}

/**
 * Validates watchlist notes update form
 * @param {object} data - Form data { notes }
 * @returns {{ valid: boolean, errors: { notes?: string } }}
 */
export function validateNotesForm(data, t) {
  const errors = {};

  const notesResult = validateNotes(data.notes, t);
  if (!notesResult.valid) {
    errors.notes = notesResult.message;
  }

  return {
    valid: Object.keys(errors).length === 0,
    errors,
  };
}
