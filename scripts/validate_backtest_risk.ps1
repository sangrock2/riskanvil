param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OutputPath = "artifacts/reports/validation-backtest-risk.json"
)

$ErrorActionPreference = "Stop"

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        $Body = $null
    )

    $params = @{
        Method = $Method
        Uri = $Url
        Headers = $Headers
        TimeoutSec = 30
    }

    if ($null -ne $Body) {
        $params["ContentType"] = "application/json"
        $params["Body"] = ($Body | ConvertTo-Json -Depth 20)
    }

    return Invoke-RestMethod @params
}

$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot

$unique = [guid]::NewGuid().ToString("N").Substring(0, 10)
$email = "validation_$unique@example.com"
$password = "Passw0rd!2345"

$checks = New-Object System.Collections.Generic.List[Object]

function Add-Check {
    param([string]$Name, [bool]$Pass, [string]$Detail)
    $checks.Add([PSCustomObject]@{
        name = $Name
        pass = $Pass
        detail = $Detail
    })
    if ($Pass) {
        Write-Host "[PASS] $Name - $Detail" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] $Name - $Detail" -ForegroundColor Red
    }
}

try {
    $null = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/auth/register" -Body @{
        email = $email
        password = $password
    }
    Add-Check -Name "register" -Pass $true -Detail $email
} catch {
    Add-Check -Name "register" -Pass $false -Detail $_.Exception.Message
    throw
}

$login = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/auth/login" -Body @{
    email = $email
    password = $password
}
$token = $login.accessToken
if (-not $token) {
    Add-Check -Name "login" -Pass $false -Detail "missing accessToken"
    throw "Login failed: missing accessToken"
}
Add-Check -Name "login" -Pass $true -Detail "token issued"
$auth = @{ Authorization = "Bearer $token" }

$portfolio = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/portfolio" -Headers $auth -Body @{
    name = "Validation Portfolio $unique"
    description = "Backtest/Risk validation"
    targetReturn = 10.0
    riskProfile = "moderate"
}

if (-not $portfolio.id) {
    Add-Check -Name "create_portfolio" -Pass $false -Detail "missing id"
    throw "Portfolio creation failed: missing id"
}
Add-Check -Name "create_portfolio" -Pass $true -Detail "portfolioId=$($portfolio.id)"

$null = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/portfolio/$($portfolio.id)/position" -Headers $auth -Body @{
    ticker = "AAPL"
    market = "US"
    quantity = 10
    entryPrice = 180
    notes = "validation-seed"
}
Add-Check -Name "add_position" -Pass $true -Detail "AAPL/US"

$backtest = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/backtest" -Headers $auth -Body @{
    ticker = "AAPL"
    market = "US"
    strategy = "SMA_CROSS"
    start = "2024-01-01"
    end = "2024-12-31"
    initialCapital = 100000
    feeBps = 5
}

if (-not $backtest.runId) {
    Add-Check -Name "backtest_run" -Pass $false -Detail "missing runId"
    throw "Backtest failed: missing runId"
}
Add-Check -Name "backtest_run" -Pass $true -Detail "runId=$($backtest.runId)"

$detail = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/backtest/$($backtest.runId)" -Headers $auth
$hasDetail = $null -ne $detail
Add-Check -Name "backtest_detail" -Pass $hasDetail -Detail ($(if ($hasDetail) { "detail loaded" } else { "empty response" }))

$risk = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/portfolio/$($portfolio.id)/risk-dashboard?lookbackDays=252" -Headers $auth

$riskChecks = @(
    [PSCustomObject]@{name="risk_volatility_non_negative"; pass=($risk.annualizedVolatilityPct -ge 0); detail="annualizedVolatilityPct=$($risk.annualizedVolatilityPct)"},
    [PSCustomObject]@{name="risk_var_non_positive"; pass=($risk.valueAtRisk95Pct -le 0); detail="valueAtRisk95Pct=$($risk.valueAtRisk95Pct)"},
    [PSCustomObject]@{name="risk_es_non_positive"; pass=($risk.expectedShortfall95Pct -le 0); detail="expectedShortfall95Pct=$($risk.expectedShortfall95Pct)"},
    [PSCustomObject]@{name="risk_drawdown_non_positive"; pass=($risk.maxDrawdownPct -le 0); detail="maxDrawdownPct=$($risk.maxDrawdownPct)"},
    [PSCustomObject]@{name="risk_holdings_present"; pass=($risk.holdings.Count -ge 1); detail="holdings=$($risk.holdings.Count)"}
)

foreach ($rc in $riskChecks) {
    Add-Check -Name $rc.name -Pass $rc.pass -Detail $rc.detail
}

$earnings = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/portfolio/$($portfolio.id)/earnings-calendar?daysAhead=60" -Headers $auth
$hasEarnings = $null -ne $earnings
Add-Check -Name "earnings_response" -Pass $hasEarnings -Detail ($(if ($hasEarnings) { "response ok" } else { "empty response" }))

$result = @{
    generatedAt = (Get-Date).ToString("o")
    baseUrl = $BaseUrl
    portfolioId = $portfolio.id
    backtestRunId = $backtest.runId
    checks = $checks
    passed = @($checks | Where-Object { $_.pass }).Count
    failed = @($checks | Where-Object { -not $_.pass }).Count
}

$dir = Split-Path $OutputPath -Parent
if ($dir -and -not (Test-Path $dir)) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
}
$result | ConvertTo-Json -Depth 10 | Set-Content -Path $OutputPath -Encoding UTF8

if ($result.failed -gt 0) {
    Write-Host "Validation failed. See $OutputPath" -ForegroundColor Red
    exit 1
}

Write-Host "Validation passed. See $OutputPath" -ForegroundColor Green
exit 0
