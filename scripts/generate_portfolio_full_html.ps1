Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$docsDir = Join-Path $repoRoot "docs"
$dossierPath = Join-Path $docsDir "PORTFOLIO_TECHNICAL_DOSSIER.md"
$rolesPath = Join-Path $docsDir "PORTFOLIO_ROLE_HIGHLIGHTS.md"
$outputPath = Join-Path $docsDir "PORTFOLIO_FULL.html"

if (-not (Test-Path $dossierPath)) {
  throw "Missing input file: $dossierPath"
}

if (-not (Test-Path $rolesPath)) {
  throw "Missing input file: $rolesPath"
}

$dossierMarkdown = Get-Content -Path $dossierPath -Raw -Encoding UTF8
$rolesMarkdown = Get-Content -Path $rolesPath -Raw -Encoding UTF8

$dossierHtml = ($dossierMarkdown | ConvertFrom-Markdown).Html
$rolesHtml = ($rolesMarkdown | ConvertFrom-Markdown).Html
$generatedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

$template = @'
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Stock-AI Portfolio Full Document</title>
  <style>
    :root {
      --bg: #f2f5fa;
      --surface: #ffffff;
      --line: #d6dee8;
      --ink: #1a2330;
      --muted: #5e6e80;
      --brand: #0f5fab;
      --brand-strong: #0a4179;
      --chip: #e8f1fb;
      --chip-ink: #0c4d8b;
      --shadow: 0 12px 30px rgba(12, 35, 66, 0.1);
      --radius: 14px;
      --content-max: 1480px;
    }

    * { box-sizing: border-box; }

    html, body { margin: 0; padding: 0; }

    body {
      font-family: "Segoe UI", "Noto Sans KR", Arial, sans-serif;
      color: var(--ink);
      background:
        radial-gradient(circle at 10% 0%, #f8fbff 0%, #f2f5fa 35%),
        linear-gradient(180deg, #f8fbff 0%, #f2f5fa 45%);
      line-height: 1.65;
    }

    .page {
      max-width: var(--content-max);
      margin: 0 auto;
      padding: 22px 16px 40px;
    }

    .hero {
      border-radius: 20px;
      padding: 26px 24px;
      color: #f4f8ff;
      background: linear-gradient(135deg, var(--brand) 0%, var(--brand-strong) 100%);
      box-shadow: var(--shadow);
    }

    .hero h1 {
      margin: 0 0 8px;
      font-size: 31px;
      letter-spacing: -0.4px;
    }

    .hero p {
      margin: 0;
      max-width: 1050px;
      color: #d9e9fb;
    }

    .meta {
      margin-top: 12px;
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .chip {
      display: inline-flex;
      align-items: center;
      padding: 6px 10px;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.2);
      font-size: 12px;
      font-weight: 700;
      color: #edf5ff;
    }

    .layout {
      margin-top: 16px;
      display: grid;
      gap: 16px;
      grid-template-columns: 280px minmax(0, 1fr);
      align-items: start;
    }

    .sidebar {
      position: sticky;
      top: 10px;
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--radius);
      box-shadow: var(--shadow);
      padding: 14px;
      max-height: calc(100vh - 22px);
      overflow: auto;
    }

    .sidebar h2 {
      margin: 0 0 6px;
      font-size: 16px;
      color: #1f3956;
    }

    .sidebar p {
      margin: 0 0 10px;
      color: var(--muted);
      font-size: 13px;
    }

    .toc-list a {
      display: block;
      text-decoration: none;
      color: #27415d;
      border-radius: 8px;
      padding: 6px 8px;
      margin-bottom: 3px;
      font-size: 13px;
      transition: background 0.2s ease;
    }

    .toc-list a:hover {
      background: #edf4fc;
    }

    .toc-list a.lvl-h1 { font-weight: 700; }
    .toc-list a.lvl-h2 { padding-left: 12px; }
    .toc-list a.lvl-h3 { padding-left: 20px; color: #45617d; }

    .tabs {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      margin-bottom: 10px;
    }

    .tab-btn {
      border: 1px solid #c8d4e2;
      border-radius: 999px;
      padding: 7px 13px;
      font-size: 13px;
      font-weight: 700;
      color: #284968;
      background: #fff;
      cursor: pointer;
    }

    .tab-btn.active {
      background: var(--chip);
      border-color: #a9c6e6;
      color: var(--chip-ink);
    }

    .tab-links {
      margin-left: auto;
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }

    .tab-links a {
      text-decoration: none;
      border: 1px solid #d0dce9;
      border-radius: 999px;
      padding: 7px 11px;
      font-size: 12px;
      color: #3b5977;
      background: #fdfefe;
    }

    .doc-panel {
      display: none;
    }

    .doc-panel.active {
      display: block;
    }

    .article {
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--radius);
      box-shadow: var(--shadow);
      padding: 18px 20px 22px;
      min-width: 0;
    }

    .article h1 {
      font-size: 29px;
      margin: 2px 0 14px;
      letter-spacing: -0.35px;
      color: #163657;
      border-bottom: 1px solid #dbe6f1;
      padding-bottom: 11px;
    }

    .article h2 {
      font-size: 24px;
      margin: 24px 0 10px;
      letter-spacing: -0.2px;
      color: #1a3f66;
    }

    .article h3 {
      font-size: 19px;
      margin: 18px 0 8px;
      color: #244b72;
    }

    .article h4, .article h5, .article h6 {
      margin: 16px 0 7px;
      color: #2f557c;
    }

    .article p {
      margin: 0 0 12px;
    }

    .article ul, .article ol {
      margin: 0 0 12px 22px;
      padding: 0;
    }

    .article li {
      margin: 2px 0;
    }

    .article blockquote {
      margin: 12px 0;
      border-left: 4px solid #8cb0d7;
      background: #f7faff;
      padding: 10px 12px;
      color: #37506b;
      border-radius: 6px;
    }

    .table-wrap {
      overflow-x: auto;
      margin: 12px 0;
      border: 1px solid #dbe5f0;
      border-radius: 10px;
      background: #fff;
    }

    .article table {
      width: 100%;
      border-collapse: collapse;
      font-size: 14px;
      min-width: 640px;
      margin: 0;
    }

    .article th, .article td {
      border: 1px solid #dbe5f0;
      text-align: left;
      padding: 9px 10px;
      vertical-align: top;
    }

    .article th {
      background: #edf4fc;
      color: #22435f;
      font-weight: 700;
    }

    .article code {
      font-family: Consolas, "Courier New", monospace;
      background: #f1f5fa;
      border: 1px solid #dce6f1;
      border-radius: 6px;
      padding: 1px 5px;
      font-size: 0.92em;
    }

    .article pre {
      margin: 12px 0;
      overflow-x: auto;
      background: #16293f;
      color: #ecf3fb;
      border-radius: 10px;
      padding: 12px 14px;
      border: 1px solid #294764;
    }

    .article pre code {
      background: transparent;
      border: none;
      color: inherit;
      padding: 0;
      font-size: 0.88em;
    }

    .article a {
      color: var(--brand);
      text-decoration: none;
    }

    .article a:hover {
      text-decoration: underline;
    }

    .footer {
      margin-top: 14px;
      color: var(--muted);
      font-size: 12px;
      padding: 0 2px;
    }

    @media (max-width: 1080px) {
      .layout {
        grid-template-columns: 1fr;
      }

      .sidebar {
        position: static;
        max-height: none;
      }

      .tab-links {
        margin-left: 0;
        width: 100%;
      }
    }

    @media (max-width: 640px) {
      .hero h1 {
        font-size: 24px;
      }

      .article {
        padding: 14px 14px 16px;
      }

      .article h1 {
        font-size: 23px;
      }

      .article h2 {
        font-size: 20px;
      }
    }
  </style>
</head>
<body>
  <main class="page">
    <section class="hero">
      <h1>Stock-AI Portfolio Full Document</h1>
      <p>
        요약판이 아닌 원문 전체를 HTML로 렌더링한 뷰입니다. 기술 문서와 포지션별 문서를 한 화면에서 전환하며 읽을 수 있고,
        좌측 목차를 통해 섹션 단위로 빠르게 이동할 수 있습니다.
      </p>
      <div class="meta">
        <span class="chip">Generated: __GENERATED_AT__</span>
        <span class="chip">기술문서 + 포지션문서 전체본</span>
        <span class="chip">Markdown to HTML (PowerShell)</span>
      </div>
    </section>

    <div class="layout">
      <aside class="sidebar">
        <h2 id="tocTitle">기술문서 목차</h2>
        <p>좌측 목차는 현재 선택된 문서의 헤더 구조를 반영합니다.</p>
        <nav class="toc-list" id="tocList"></nav>
      </aside>

      <section>
        <div class="tabs">
          <button type="button" class="tab-btn active" data-target="dossier">기술문서 전체</button>
          <button type="button" class="tab-btn" data-target="roles">포지션 문서 전체</button>
          <div class="tab-links">
            <a href="./PORTFOLIO_TECHNICAL_DOSSIER.md">원문 MD: 기술문서</a>
            <a href="./PORTFOLIO_ROLE_HIGHLIGHTS.md">원문 MD: 포지션 문서</a>
            <a href="./PORTFOLIO_READABLE.html">요약 HTML</a>
          </div>
        </div>

        <article id="panel-dossier" class="article doc-panel active">
__DOSSIER_HTML__
        </article>

        <article id="panel-roles" class="article doc-panel">
__ROLES_HTML__
        </article>
      </section>
    </div>

    <div class="footer">
      View file: <code>docs/PORTFOLIO_FULL.html</code>
    </div>
  </main>

  <script>
    (function () {
      var buttons = document.querySelectorAll(".tab-btn");
      var panels = {
        dossier: document.getElementById("panel-dossier"),
        roles: document.getElementById("panel-roles")
      };
      var tocList = document.getElementById("tocList");
      var tocTitle = document.getElementById("tocTitle");

      function ensureHeadingIds(panelKey) {
        var panel = panels[panelKey];
        if (!panel) return;
        var headings = panel.querySelectorAll("h1, h2, h3");
        headings.forEach(function (heading, index) {
          if (!heading.id) {
            heading.id = panelKey + "-heading-" + index;
          } else {
            heading.id = panelKey + "-" + heading.id;
          }
        });
      }

      function wrapTables(panelKey) {
        var panel = panels[panelKey];
        if (!panel) return;
        panel.querySelectorAll("table").forEach(function (table) {
          if (table.parentElement && table.parentElement.classList.contains("table-wrap")) {
            return;
          }
          var wrapper = document.createElement("div");
          wrapper.className = "table-wrap";
          table.parentNode.insertBefore(wrapper, table);
          wrapper.appendChild(table);
        });
      }

      function buildToc(panelKey) {
        var panel = panels[panelKey];
        if (!panel) return;

        tocList.innerHTML = "";
        tocTitle.textContent = panelKey === "dossier" ? "기술문서 목차" : "포지션 문서 목차";

        var headings = panel.querySelectorAll("h1, h2, h3");
        headings.forEach(function (heading) {
          var anchor = document.createElement("a");
          anchor.href = "#" + heading.id;
          anchor.textContent = heading.textContent;
          anchor.className = "lvl-" + heading.tagName.toLowerCase();
          tocList.appendChild(anchor);
        });
      }

      function switchTab(target) {
        Object.keys(panels).forEach(function (key) {
          panels[key].classList.toggle("active", key === target);
        });

        buttons.forEach(function (button) {
          button.classList.toggle("active", button.dataset.target === target);
        });

        buildToc(target);
      }

      ensureHeadingIds("dossier");
      ensureHeadingIds("roles");
      wrapTables("dossier");
      wrapTables("roles");
      buildToc("dossier");

      buttons.forEach(function (button) {
        button.addEventListener("click", function () {
          switchTab(button.dataset.target);
          window.scrollTo({ top: 0, behavior: "smooth" });
        });
      });
    }());
  </script>
</body>
</html>
'@

$htmlContent = $template.Replace("__DOSSIER_HTML__", $dossierHtml)
$htmlContent = $htmlContent.Replace("__ROLES_HTML__", $rolesHtml)
$htmlContent = $htmlContent.Replace("__GENERATED_AT__", $generatedAt)

Set-Content -Path $outputPath -Value $htmlContent -Encoding UTF8
Write-Host "Generated: $outputPath"
