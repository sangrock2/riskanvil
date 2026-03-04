export function safeId(v) {
  return String(v || "")
    .replace(/[^a-zA-Z0-9_]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

export function buildFileTree(paths) {
  const root = { name: "/", path: "", type: "dir", children: new Map() };

  for (const p of paths) {
    const parts = p.split("/");
    let cur = root;
    let full = "";
    for (let i = 0; i < parts.length; i += 1) {
      const part = parts[i];
      full = full ? `${full}/${part}` : part;
      const isLast = i === parts.length - 1;
      if (!cur.children.has(part)) {
        cur.children.set(part, {
          name: part,
          path: full,
          type: isLast ? "file" : "dir",
          children: new Map(),
        });
      }
      cur = cur.children.get(part);
      if (isLast) cur.type = "file";
    }
  }

  function normalize(node) {
    if (node.type === "file") return { ...node, children: [] };
    const children = Array.from(node.children.values())
      .map(normalize)
      .sort((a, b) => {
        if (a.type !== b.type) return a.type === "dir" ? -1 : 1;
        return a.name.localeCompare(b.name);
      });
    return { ...node, children };
  }

  return normalize(root);
}

export function filterTree(node, q) {
  if (!q) return node;
  const query = q.toLowerCase();
  if (node.type === "file") {
    return node.path.toLowerCase().includes(query) ? node : null;
  }
  const children = (node.children || [])
    .map((ch) => filterTree(ch, query))
    .filter(Boolean);
  if (node.path.toLowerCase().includes(query) || children.length > 0) {
    return { ...node, children };
  }
  return null;
}

export function pathStartsWith(basePath, routePath) {
  if (!basePath || !routePath) return false;
  const bp = basePath.split("?")[0];
  const rp = routePath.split("?")[0].replace(/\$\{[^}]+\}/g, ":var");
  return rp.startsWith(bp);
}

export function replaceTemplate(text, ctx) {
  return String(text || "").replace(/\{\{\s*([a-zA-Z0-9_]+)\s*\}\}/g, (_m, key) => {
    const v = ctx[key];
    return v === undefined || v === null ? "" : String(v);
  });
}

export function truncateText(value, maxLen) {
  const text = String(value || "");
  if (text.length <= maxLen) return text;
  return `${text.slice(0, Math.max(0, maxLen - 1)).trimEnd()}…`;
}

export function cloneJson(value) {
  try {
    return JSON.parse(JSON.stringify(value ?? {}));
  } catch (_e) {
    return {};
  }
}

export function diffTopLevel(before, after) {
  const prev = before && typeof before === "object" ? before : {};
  const next = after && typeof after === "object" ? after : {};
  const prevKeys = Object.keys(prev);
  const nextKeys = Object.keys(next);
  const prevSet = new Set(prevKeys);
  const nextSet = new Set(nextKeys);

  const added = nextKeys.filter((k) => !prevSet.has(k));
  const removed = prevKeys.filter((k) => !nextSet.has(k));
  const updated = nextKeys.filter(
    (k) =>
      prevSet.has(k) &&
      JSON.stringify(prev[k]) !== JSON.stringify(next[k])
  );

  return { added, updated, removed };
}

export function deriveAction(score) {
  if (score >= 75) return "BUY";
  if (score >= 60) return "HOLD";
  return "SELL";
}
