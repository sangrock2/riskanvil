param(
    [switch]$SyncFromBackend,
    [string]$BackendBaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot

if ($SyncFromBackend) {
    & pwsh -File scripts/openapi_sync.ps1 -BackendBaseUrl $BackendBaseUrl
    if ($LASTEXITCODE -ne 0) {
        throw "OpenAPI sync failed."
    }
}

Write-Host "Generating typed client from docs/openapi/stock-ai.openapi.json" -ForegroundColor Cyan

& npm --prefix frontend run openapi:generate
if ($LASTEXITCODE -ne 0) {
    throw "OpenAPI client generation failed."
}

Write-Host "Generated client: frontend/src/api/generated" -ForegroundColor Green
