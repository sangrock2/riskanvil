Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$markdownPath = Join-Path $repoRoot "docs/SERVICE_MANUAL.md"
$outputPath = Join-Path $repoRoot "docs/SERVICE_MANUAL.html"

if (-not (Test-Path $markdownPath)) {
  throw "Missing input file: $markdownPath"
}

$markdown = Get-Content -Path $markdownPath -Raw -Encoding UTF8
$body = ($markdown | ConvertFrom-Markdown).Html
$generatedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

$template = @"
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Stock-AI 서비스 사용/운영 매뉴얼</title>
  <style>
    :root {
      --bg: #f3f6fb;
      --surface: #ffffff;
      --text: #172334;
      --line: #d9e2ee;
      --shadow: 0 10px 26px rgba(18, 35, 60, 0.08);
      --radius: 14px;
      --max: 1160px;
      --code-bg: #f5f8fc;
    }

    * { box-sizing: border-box; }
    html, body {
      margin: 0;
      padding: 0;
      background: radial-gradient(circle at 0% 0%, #f9fcff 0%, var(--bg) 45%);
      color: var(--text);
      font-family: "Segoe UI", "Noto Sans KR", Arial, sans-serif;
      line-height: 1.62;
    }

    .page { max-width: var(--max); margin: 0 auto; padding: 24px 18px 42px; }
    .hero {
      background: linear-gradient(135deg, #0f5fa7 0%, #093f74 100%);
      color: #f2f8ff;
      border-radius: 18px;
      box-shadow: var(--shadow);
      padding: 24px 22px;
      margin-bottom: 16px;
    }

    .hero h1 { margin: 0 0 8px; font-size: 30px; letter-spacing: -0.4px; }
    .hero p { margin: 0; color: #d5e7fb; }
    .meta { margin-top: 10px; font-size: 12px; color: #dbeafd; opacity: 0.95; }

    .article {
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--radius);
      box-shadow: var(--shadow);
      padding: 20px 24px 24px;
    }

    .article h1, .article h2, .article h3 { color: #173a61; letter-spacing: -0.2px; }
    .article h1 { margin-top: 0; border-bottom: 1px solid #e2eaf3; padding-bottom: 10px; }
    .article h2 { margin-top: 30px; border-left: 4px solid #96bce2; padding-left: 10px; }
    .article p, .article li { font-size: 15px; }
    .article ul, .article ol { padding-left: 20px; }

    .article code {
      background: var(--code-bg);
      border: 1px solid #dbe6f2;
      border-radius: 6px;
      padding: 2px 6px;
      font-family: Consolas, "Courier New", monospace;
      font-size: 0.93em;
      color: #18385a;
    }

    .article pre {
      background: #111a26;
      color: #ebf2fd;
      border-radius: 10px;
      border: 1px solid #2e415c;
      padding: 12px;
      overflow: auto;
      font-size: 13px;
    }

    .article pre code {
      background: transparent;
      border: 0;
      padding: 0;
      color: inherit;
    }

    @media (max-width: 880px) {
      .page { padding: 16px 12px 28px; }
      .hero { padding: 18px 16px; }
      .hero h1 { font-size: 24px; }
      .article { padding: 14px 14px 18px; }
      .article p, .article li { font-size: 14px; }
    }
  </style>
</head>
<body>
  <div class="page">
    <section class="hero">
      <h1>Stock-AI 서비스 사용/운영 매뉴얼</h1>
      <p>사용자 기능 사용법과 운영 기능 사용법을 한 문서에서 빠르게 확인할 수 있는 통합 가이드입니다.</p>
      <div class="meta">Generated at: $generatedAt</div>
    </section>
    <article class="article">
      $body
    </article>
  </div>
</body>
</html>
"@

Set-Content -Path $outputPath -Value $template -Encoding UTF8
Write-Host "Generated: $outputPath"
