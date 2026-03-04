param(
    [Parameter(Mandatory = $true)]
    [string]$PublicAppUrl,
    [string]$BackendHealthUrl = "",
    [string]$AiHealthUrl = "",
    [string]$OutputPath = "artifacts/reports/public-service-verification.json"
)

$ErrorActionPreference = "Stop"

function Test-HttpEndpoint {
    param(
        [string]$Url,
        [int[]]$ExpectedStatus = @(200)
    )

    $result = @{
        url = $Url
        ok = $false
        status = $null
        message = ""
        checkedAt = (Get-Date).ToString("o")
    }

    try {
        $resp = Invoke-WebRequest -Uri $Url -Method GET -TimeoutSec 20 -MaximumRedirection 5
        $result.status = [int]$resp.StatusCode
        if ($ExpectedStatus -contains [int]$resp.StatusCode) {
            $result.ok = $true
            $result.message = "reachable"
        } else {
            $result.message = "unexpected status code"
        }
    } catch {
        if ($_.Exception.Response) {
            $result.status = [int]$_.Exception.Response.StatusCode
        }
        $result.message = $_.Exception.Message
    }

    return $result
}

$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot

$checks = @()
$checks += Test-HttpEndpoint -Url $PublicAppUrl -ExpectedStatus @(200, 301, 302)

if (-not [string]::IsNullOrWhiteSpace($BackendHealthUrl)) {
    $checks += Test-HttpEndpoint -Url $BackendHealthUrl -ExpectedStatus @(200)
}
if (-not [string]::IsNullOrWhiteSpace($AiHealthUrl)) {
    $checks += Test-HttpEndpoint -Url $AiHealthUrl -ExpectedStatus @(200)
}

$payload = @{
    generatedAt = (Get-Date).ToString("o")
    publicAppUrl = $PublicAppUrl
    backendHealthUrl = $BackendHealthUrl
    aiHealthUrl = $AiHealthUrl
    checks = $checks
    pass = @($checks | Where-Object { -not $_.ok }).Count -eq 0
}

$outDir = Split-Path $OutputPath -Parent
if ($outDir -and -not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}

$payload | ConvertTo-Json -Depth 10 | Set-Content -Path $OutputPath -Encoding UTF8

if ($payload.pass) {
    Write-Host "Public service verification passed." -ForegroundColor Green
    Write-Host "Report: $OutputPath" -ForegroundColor Green
    exit 0
}

Write-Host "Public service verification failed." -ForegroundColor Red
Write-Host "Report: $OutputPath" -ForegroundColor Yellow
exit 1
