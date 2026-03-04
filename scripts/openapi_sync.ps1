param(
    [string]$BackendBaseUrl = "http://localhost:8080",
    [string]$OutputPath = "docs/openapi/stock-ai.openapi.json",
    [switch]$AsYaml
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot

$url = if ($AsYaml) { "$BackendBaseUrl/v3/api-docs.yaml" } else { "$BackendBaseUrl/v3/api-docs" }

Write-Host "Exporting OpenAPI from: $url" -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri $url -Method GET -TimeoutSec 30
} catch {
    throw "Failed to fetch OpenAPI spec from '$url'. Ensure backend is running."
}

$targetDir = Split-Path $OutputPath -Parent
if ($targetDir -and -not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
}

$response.Content | Set-Content -Path $OutputPath -Encoding UTF8

Write-Host "OpenAPI snapshot saved to: $OutputPath" -ForegroundColor Green
