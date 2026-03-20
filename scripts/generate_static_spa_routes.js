const fs = require("node:fs");
const path = require("node:path");

const SPA_ROUTE_SEGMENTS = [
  "login",
  "register",
  "glossary",
  "learn",
  "dashboard",
  "analyze",
  "backtest",
  "insight-detail",
  "watchlist",
  "compare",
  "usage",
  "portfolio",
  "settings",
  "screener",
  "correlation",
  "chatbot",
  "dividends",
  "earnings",
  "risk-dashboard",
  "paper-trading",
  "system-map",
];

const distDir = path.resolve(__dirname, "..", "frontend", "dist");
const sourceIndexPath = path.join(distDir, "index.html");

if (!fs.existsSync(sourceIndexPath)) {
  throw new Error(`Missing frontend build entrypoint: ${sourceIndexPath}`);
}

const sourceHtml = fs.readFileSync(sourceIndexPath, "utf8");

for (const segment of SPA_ROUTE_SEGMENTS) {
  const normalizedSegment = String(segment || "").replace(/^\/+|\/+$/g, "");
  if (!normalizedSegment) {
    continue;
  }

  const routeDir = path.join(distDir, normalizedSegment);
  fs.mkdirSync(routeDir, { recursive: true });
  fs.writeFileSync(path.join(routeDir, "index.html"), sourceHtml);
}

fs.writeFileSync(path.join(distDir, "404.html"), sourceHtml);

console.log(`Generated static SPA entrypoints for ${SPA_ROUTE_SEGMENTS.length} routes.`);
