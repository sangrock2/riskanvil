param(
    [Parameter(Mandatory = $true)]
    [string]$FrontendUrl,
    [string]$BackendHealthUrl = "",
    [string]$AiHealthUrl = "",
    [Parameter(Mandatory = $true)]
    [string]$Email,
    [Parameter(Mandatory = $true)]
    [string]$Password,
    [string]$PublicReportPath = "artifacts/reports/public-service-verification.json"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot

$publicVerificationExitCode = 0
& "$PSScriptRoot\verify_public_service.ps1" `
    -PublicAppUrl $FrontendUrl `
    -BackendHealthUrl $BackendHealthUrl `
    -AiHealthUrl $AiHealthUrl `
    -OutputPath $PublicReportPath
$publicVerificationExitCode = $LASTEXITCODE

if ($publicVerificationExitCode -ne 0) {
    Write-Host "Public verification reported failures. Continuing to browser smoke for additional diagnostics." -ForegroundColor Yellow
}

try {
    Set-Location frontend
    $env:PLAYWRIGHT_BASE_URL = $FrontendUrl.TrimEnd("/")
    $env:PLAYWRIGHT_DEPLOYED_EMAIL = $Email
    $env:PLAYWRIGHT_DEPLOYED_PASSWORD = $Password

    $output = npm run test:e2e:deployed 2>&1
    $browserSmokeExitCode = $LASTEXITCODE
    if ($browserSmokeExitCode -ne 0) {
        Write-Host $output
        throw "Deployed browser smoke failed"
    }

    Write-Host "Deployed production smoke passed." -ForegroundColor Green
} finally {
    Remove-Item Env:PLAYWRIGHT_BASE_URL -ErrorAction SilentlyContinue
    Remove-Item Env:PLAYWRIGHT_DEPLOYED_EMAIL -ErrorAction SilentlyContinue
    Remove-Item Env:PLAYWRIGHT_DEPLOYED_PASSWORD -ErrorAction SilentlyContinue
    Set-Location $projectRoot
}

if ($publicVerificationExitCode -ne 0) {
    throw "Public service verification failed"
}
