/**
 * Shared formatting utilities for the Stock-AI frontend
 * Consolidates duplicate formatting functions across components
 */

/**
 * Formats a decimal number as a percentage string
 * @param {number|null|undefined} x - The decimal value (e.g., 0.15 for 15%)
 * @param {number} digits - Number of decimal places (default: 1)
 * @returns {string} Formatted percentage or "N/A"
 */
export function pct(x, digits = 1) {
  if (x === null || x === undefined || Number.isNaN(Number(x))) return "N/A";
  return `${(Number(x) * 100).toFixed(digits)}%`;
}

/**
 * Formats a number with fixed decimal places
 * @param {number|null|undefined} x - The number to format
 * @param {number} d - Number of decimal places (default: 2)
 * @returns {string} Formatted number or "N/A"
 */
export function num(x, d = 2) {
  if (x === null || x === undefined || Number.isNaN(Number(x))) return "N/A";
  return Number(x).toFixed(d);
}

/**
 * Formats an ISO date string to YYYY-MM-DD format
 * @param {string|null|undefined} iso - ISO date string
 * @returns {string} Formatted date or empty string
 */
export function fmtDate(iso) {
  if (!iso) return "";

  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return "";
    return d.toISOString().slice(0, 10);
  } catch {
    return "";
  }
}

/**
 * Formats a number as localized currency/money string
 * @param {number|null|undefined} x - The number to format
 * @returns {string} Localized number string or "N/A"
 */
export function money(x) {
  const n = Number(x);
  if (!Number.isFinite(n)) return "N/A";
  return n.toLocaleString();
}

/**
 * Formats large numbers in a human-readable format (e.g., 1.5B, 2.3M)
 * @param {number|null|undefined} x - The number to format
 * @param {number} digits - Decimal places (default: 1)
 * @returns {string} Formatted string or "N/A"
 */
export function formatLargeNumber(x, digits = 1) {
  if (x === null || x === undefined || Number.isNaN(Number(x))) return "N/A";

  const n = Number(x);
  const absN = Math.abs(n);

  if (absN >= 1e12) return `${(n / 1e12).toFixed(digits)}T`;
  if (absN >= 1e9) return `${(n / 1e9).toFixed(digits)}B`;
  if (absN >= 1e6) return `${(n / 1e6).toFixed(digits)}M`;
  if (absN >= 1e3) return `${(n / 1e3).toFixed(digits)}K`;

  return n.toFixed(digits);
}

/**
 * Formats a date relative to now (e.g., "2 days ago")
 * @param {string|Date} date - The date to format
 * @returns {string} Relative time string
 */
export function formatRelativeTime(date) {
  if (!date) return "";

  try {
    const d = new Date(date);
    if (Number.isNaN(d.getTime())) return "";

    const now = new Date();
    const diffMs = now - d;
    const diffSecs = Math.floor(diffMs / 1000);
    const diffMins = Math.floor(diffSecs / 60);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffDays > 30) return fmtDate(date);
    if (diffDays > 0) return `${diffDays}d ago`;
    if (diffHours > 0) return `${diffHours}h ago`;
    if (diffMins > 0) return `${diffMins}m ago`;
    return "just now";
  } catch {
    return "";
  }
}

/**
 * Parses AlphaVantage time_published format to readable string
 * @param {string} tp - AlphaVantage format "20260110T012345"
 * @returns {string} Formatted datetime "2026-01-10 01:23"
 */
export function parseAlphaTimePublished(tp) {
  const s = String(tp || "").trim();
  if (!s || s.length < 8) return "";
  const y = s.slice(0, 4);
  const m = s.slice(4, 6);
  const d = s.slice(6, 8);
  const hh = s.length >= 11 ? s.slice(9, 11) : "00";
  const mm = s.length >= 13 ? s.slice(11, 13) : "00";
  return `${y}-${m}-${d} ${hh}:${mm}`;
}

/**
 * Gets current date in YYYYMMDD format
 * @returns {string} Date string
 */
export function ymd() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${y}${m}${dd}`;
}
