/* eslint-disable no-console */
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const OUT_FILE = path.join(ROOT, "frontend", "src", "data", "systemMapData.json");

const IGNORE_DIRS = new Set([
  ".git",
  "node_modules",
  ".gradle",
  "build",
  "dist",
  "out",
  "__pycache__",
  ".idea",
  ".vscode",
]);

function walk(dir, base = ROOT, out = []) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    if (IGNORE_DIRS.has(entry.name)) continue;
    const abs = path.join(dir, entry.name);
    const rel = path.relative(base, abs).replace(/\\/g, "/");
    if (entry.isDirectory()) {
      walk(abs, base, out);
    } else if (entry.isFile()) {
      out.push(rel);
    }
  }
  return out;
}

function readSafe(relPath) {
  const abs = path.join(ROOT, relPath);
  try {
    return fs.readFileSync(abs, "utf8");
  } catch (_e) {
    return "";
  }
}

function parseJavaClassName(src) {
  const m = src.match(/class\s+([A-Za-z0-9_]+)/);
  return m ? m[1] : null;
}

function parseJavaDependencies(src) {
  const deps = [];
  const re = /private\s+final\s+([A-Za-z0-9_<>]+)\s+[A-Za-z0-9_]+\s*;/g;
  let m;
  while ((m = re.exec(src)) !== null) {
    const raw = m[1];
    const clean = raw.replace(/<.*>/, "");
    deps.push(clean);
  }
  return Array.from(new Set(deps));
}

function parseRequestMappings(src, basePath = "") {
  const endpoints = [];
  const lineRe = /@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\(([^)]*)\)/g;
  let m;
  while ((m = lineRe.exec(src)) !== null) {
    const method = m[1].replace("Mapping", "").toUpperCase();
    const args = m[2] || "";
    let route = "";
    const pathMatch = args.match(/"([^"]*)"/);
    if (pathMatch) route = pathMatch[1];
    if (!route && args.includes("value")) {
      const valueMatch = args.match(/value\s*=\s*"([^"]*)"/);
      if (valueMatch) route = valueMatch[1];
    }
    const full = `${basePath || ""}${route || ""}` || "/";
    endpoints.push({ method, path: full });
  }
  return endpoints;
}

function parseBackendControllers(files) {
  const controllerFiles = files.filter((f) => f.startsWith("backend/src/main/java/com/sw103302/backend/controller/") && f.endsWith(".java"));
  return controllerFiles.map((file) => {
    const src = readSafe(file);
    const name = parseJavaClassName(src) || path.basename(file, ".java");
    const baseMatch = src.match(/@RequestMapping\("([^"]*)"\)/);
    const basePath = baseMatch ? baseMatch[1] : "";
    return {
      name,
      file,
      basePath,
      dependencies: parseJavaDependencies(src),
      endpoints: parseRequestMappings(src, basePath),
    };
  });
}

function parseBackendServices(files) {
  const serviceFiles = files.filter((f) => f.startsWith("backend/src/main/java/com/sw103302/backend/service/") && f.endsWith(".java"));
  return serviceFiles.map((file) => {
    const src = readSafe(file);
    const name = parseJavaClassName(src) || path.basename(file, ".java");
    return {
      name,
      file,
      dependencies: parseJavaDependencies(src),
    };
  });
}

function parseBackendRepositories(files) {
  const repoFiles = files.filter((f) => f.startsWith("backend/src/main/java/com/sw103302/backend/repository/") && f.endsWith(".java"));
  return repoFiles.map((file) => ({
    name: path.basename(file, ".java"),
    file,
  }));
}

function parseAiRoutes(files) {
  const routeFiles = files.filter((f) => f.startsWith("ai/routes/") && f.endsWith(".py"));
  return routeFiles.map((file) => {
    const src = readSafe(file);
    const endpoints = [];
    const re = /@router\.(get|post|put|delete|websocket)\("([^"]+)"/g;
    let m;
    while ((m = re.exec(src)) !== null) {
      endpoints.push({ method: m[1].toUpperCase(), path: m[2] });
    }

    const deps = [];
    const depRe = /from\s+([A-Za-z0-9_\.]+)\s+import\s+([A-Za-z0-9_,\s]+)/g;
    let d;
    while ((d = depRe.exec(src)) !== null) {
      const fromMod = d[1];
      const names = d[2]
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
      if (fromMod.startsWith("data_sources") || fromMod.startsWith("llm") || fromMod.startsWith("analysis") || fromMod.startsWith("routes")) {
        deps.push(...names.map((n) => `${fromMod}.${n}`));
      }
    }
    return {
      file,
      module: path.basename(file, ".py"),
      endpoints,
      dependencies: Array.from(new Set(deps)),
    };
  });
}

function parseFrontendApiModules(files) {
  const apiFiles = files.filter((f) => f.startsWith("frontend/src/api/") && f.endsWith(".js"));
  return apiFiles.map((file) => {
    const src = readSafe(file);
    const paths = Array.from(src.matchAll(/apiFetch\(\s*`([^`]+)`/g)).map((m) => m[1]);
    const plainPaths = Array.from(src.matchAll(/apiFetch\(\s*"([^"]+)"/g)).map((m) => m[1]);
    return {
      file,
      paths: Array.from(new Set([...paths, ...plainPaths])),
    };
  });
}

function parseFrontendHookApis(files) {
  const hookFiles = files.filter((f) => f.startsWith("frontend/src/hooks/") && f.endsWith(".js"));
  return hookFiles.map((file) => {
    const src = readSafe(file);
    const paths = Array.from(src.matchAll(/apiFetch\(\s*`([^`]+)`/g)).map((m) => m[1]);
    const plainPaths = Array.from(src.matchAll(/apiFetch\(\s*"([^"]+)"/g)).map((m) => m[1]);
    const wsUsage = src.includes("quoteWS") || src.includes("WebSocket");
    return {
      file,
      paths: Array.from(new Set([...paths, ...plainPaths])),
      wsUsage,
    };
  });
}

function countByPrefix(files, prefix) {
  return files.filter((f) => f.startsWith(prefix)).length;
}

function main() {
  const allFiles = walk(ROOT).sort();
  const data = {
    generatedAt: new Date().toISOString(),
    stats: {
      totalFiles: allFiles.length,
      frontendFiles: countByPrefix(allFiles, "frontend/"),
      backendFiles: countByPrefix(allFiles, "backend/"),
      aiFiles: countByPrefix(allFiles, "ai/"),
      docsFiles: countByPrefix(allFiles, "docs/"),
      scriptFiles: countByPrefix(allFiles, "scripts/"),
    },
    files: allFiles,
    backend: {
      controllers: parseBackendControllers(allFiles),
      services: parseBackendServices(allFiles),
      repositories: parseBackendRepositories(allFiles),
    },
    ai: {
      routes: parseAiRoutes(allFiles),
    },
    frontend: {
      apiModules: parseFrontendApiModules(allFiles),
      hooks: parseFrontendHookApis(allFiles),
      pages: allFiles.filter((f) => f.startsWith("frontend/src/pages/") && f.endsWith(".js")),
      components: allFiles.filter((f) => f.startsWith("frontend/src/components/") && f.endsWith(".js")),
    },
  };

  fs.mkdirSync(path.dirname(OUT_FILE), { recursive: true });
  fs.writeFileSync(OUT_FILE, JSON.stringify(data, null, 2), "utf8");
  console.log(`Generated: ${path.relative(ROOT, OUT_FILE)}`);
  console.log(`Controllers: ${data.backend.controllers.length}`);
  console.log(`Services: ${data.backend.services.length}`);
  console.log(`AI routes: ${data.ai.routes.length}`);
}

main();
