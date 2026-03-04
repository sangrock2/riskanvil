param(
    [string]$AppUrl = "http://localhost",
    [string]$PrometheusUrl = "http://localhost:9090",
    [string]$GrafanaUrl = "http://localhost:3001",
    [string]$OutputDir = "artifacts/screenshots"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}

Write-Host "Capturing screenshots into $OutputDir" -ForegroundColor Cyan
Write-Host "Installing Playwright Chromium (if needed)..." -ForegroundColor DarkGray

npx --yes playwright install chromium | Out-Null

npx --yes playwright screenshot "$AppUrl" "$OutputDir/app-home.png"
npx --yes playwright screenshot "$PrometheusUrl" "$OutputDir/prometheus-overview.png"
npx --yes playwright screenshot "$GrafanaUrl" "$OutputDir/grafana-overview.png"

Write-Host "Screenshots captured successfully." -ForegroundColor Green
