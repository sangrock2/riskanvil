param(
    [Parameter(Mandatory = $true)]
    [string]$BackendBaseUrl,
    [string]$AiHealthUrl = "",
    [int]$MonitorMinutes = 60,
    [int]$MonitorIntervalSeconds = 300,
    [int]$LoadVus = 6,
    [int]$LoadIterations = 8,
    [string]$OutputDir = "artifacts/reports/dr-rehearsal",
    [string]$PythonExe = "python",
    [switch]$BackupRestoreConfirmed,
    [switch]$DataIntegrityChecked
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[dr] $Message" -ForegroundColor Cyan
}

$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}

$syntheticReport = Join-Path $OutputDir "synthetic-monitor.json"
$loadReport = Join-Path $OutputDir "load-test-short.json"
$summaryReport = Join-Path $OutputDir "dr-rehearsal-summary.json"

Write-Step "Starting DR rehearsal validation"
Write-Step "Backend=$BackendBaseUrl, AI Health=$AiHealthUrl"

$env:SYN_BASE_URL = $BackendBaseUrl
$env:SYN_AI_HEALTH_URL = $AiHealthUrl
$env:SYN_DURATION_MINUTES = "$MonitorMinutes"
$env:SYN_INTERVAL_SECONDS = "$MonitorIntervalSeconds"
$env:SYN_OUTPUT_PATH = $syntheticReport

Write-Step "Running synthetic monitor for $MonitorMinutes minutes..."
& $PythonExe scripts/synthetic_monitor.py
$syntheticExit = $LASTEXITCODE

$env:LOAD_BASE_URL = $BackendBaseUrl
$env:LOAD_VUS = "$LoadVus"
$env:LOAD_ITERATIONS = "$LoadIterations"
$env:LOAD_OUTPUT_PATH = $loadReport

Write-Step "Running short load test (vus=$LoadVus, iterations=$LoadIterations)..."
& $PythonExe scripts/load_test_short.py
$loadExit = $LASTEXITCODE

$syntheticData = $null
$loadData = $null
if (Test-Path $syntheticReport) {
    $syntheticData = Get-Content $syntheticReport -Raw | ConvertFrom-Json
}
if (Test-Path $loadReport) {
    $loadData = Get-Content $loadReport -Raw | ConvertFrom-Json
}

$summary = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    backendBaseUrl = $BackendBaseUrl
    aiHealthUrl = $AiHealthUrl
    monitorMinutes = $MonitorMinutes
    loadVus = $LoadVus
    loadIterations = $LoadIterations
    checks = [ordered]@{
        syntheticMonitor = [ordered]@{
            pass = ($syntheticExit -eq 0)
            report = $syntheticReport
            successRatio = if ($syntheticData) { $syntheticData.successRatio } else { $null }
        }
        shortLoadTest = [ordered]@{
            pass = ($loadExit -eq 0)
            report = $loadReport
            errorRate = if ($loadData) { $loadData.summary.errorRate } else { $null }
            p95ms = if ($loadData) { $loadData.summary.p95ms } else { $null }
        }
        backupRestoreConfirmed = [ordered]@{
            pass = [bool]$BackupRestoreConfirmed
            note = "Use -BackupRestoreConfirmed after real backup restore completed"
        }
        dataIntegrityChecked = [ordered]@{
            pass = [bool]$DataIntegrityChecked
            note = "Use -DataIntegrityChecked after DB data integrity sampling completed"
        }
    }
}

$allPass = $summary.checks.syntheticMonitor.pass -and `
           $summary.checks.shortLoadTest.pass -and `
           $summary.checks.backupRestoreConfirmed.pass -and `
           $summary.checks.dataIntegrityChecked.pass

$summary["overallPass"] = $allPass
$summary["overallStatus"] = if ($allPass) { "PASS" } else { "FAIL" }

$summary | ConvertTo-Json -Depth 10 | Set-Content -Path $summaryReport -Encoding UTF8

if ($allPass) {
    Write-Host "[dr] DR rehearsal PASS. Report: $summaryReport" -ForegroundColor Green
    exit 0
}

Write-Host "[dr] DR rehearsal FAIL. Report: $summaryReport" -ForegroundColor Red
exit 1

