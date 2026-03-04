# Stock-AI Integration Tests
# Tests the full stack: Backend (8080) + AI Service (8000)

$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080"
$aiUrl = "http://localhost:8000"

Write-Host "=================================================="
Write-Host "Stock-AI Integration Testing"
Write-Host "=================================================="
Write-Host ""

# Test results
$passed = 0
$failed = 0

# Helper function for API calls
function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Url,
        [string]$Method = "GET",
        [string]$Body = $null,
        [hashtable]$Headers = @{},
        [scriptblock]$Validator = { param($response) $true }
    )

    Write-Host "=== Testing: $Name ===" -ForegroundColor Cyan

    try {
        $params = @{
            Uri = $Url
            Method = $Method
            Headers = $Headers
            UseBasicParsing = $true
        }

        if ($Body) {
            $params.Body = $Body
            $params.ContentType = "application/json"
        }

        $response = Invoke-WebRequest @params
        if ($response.Content -is [byte[]]) {
            $content = [System.Text.Encoding]::UTF8.GetString($response.Content) | ConvertFrom-Json
        } else {
            $content = $response.Content | ConvertFrom-Json
        }

        $isValid = & $Validator $content

        if ($isValid) {
            Write-Host "[PASS] $Name" -ForegroundColor Green
            $script:passed++
            return $content
        } else {
            Write-Host "[FAIL] $Name - Validation failed" -ForegroundColor Red
            Write-Host "Response: $($content | ConvertTo-Json -Compress)" -ForegroundColor Yellow
            $script:failed++
            return $null
        }
    } catch {
        Write-Host "[FAIL] $Name - $_" -ForegroundColor Red
        $script:failed++
        return $null
    }
}

Write-Host "Checking service availability..." -ForegroundColor Yellow
Write-Host ""

# Test 1: Backend Health
Test-Endpoint -Name "Backend Health Check" `
    -Url "$baseUrl/actuator/health" `
    -Validator { param($r) $r.status -eq "UP" }

# Test 2: AI Service Health
Test-Endpoint -Name "AI Service Health Check" `
    -Url "$aiUrl/health" `
    -Validator { param($r) $r.status -eq "ok" }

Write-Host ""
Write-Host "Running authentication flow..." -ForegroundColor Yellow
Write-Host ""

# Test 3: User Registration
$registerBody = @{
    email = "test_integration_$((Get-Date).Ticks)@example.com"
    password = "Test1234!"
} | ConvertTo-Json

$registerResponse = Test-Endpoint -Name "User Registration" `
    -Url "$baseUrl/api/auth/register" `
    -Method "POST" `
    -Body $registerBody `
    -Validator { param($r) $r.accessToken -ne $null }

if (-not $registerResponse) {
    Write-Host "Registration failed, aborting integration tests" -ForegroundColor Red
    exit 1
}

$token = $registerResponse.accessToken
$authHeaders = @{ "Authorization" = "Bearer $token" }

Write-Host ""
Write-Host "Running market analysis workflow..." -ForegroundColor Yellow
Write-Host ""

# Test 4: Analyze Stock (Backend -> AI Service)
$analyzeBody = @{
    ticker = "AAPL"
    market = "US"
    horizonDays = 90
    riskProfile = "moderate"
    days = 90
    newsLimit = 10
} | ConvertTo-Json

$analysisResponse = Test-Endpoint -Name "Stock Analysis (AAPL)" `
    -Url "$baseUrl/api/analysis" `
    -Method "POST" `
    -Body $analyzeBody `
    -Headers $authHeaders `
    -Validator { param($r)
        $r.runId -ne $null -and
        $r.result -ne $null -and
        $r.result.ticker -eq "AAPL"
    }

# Test 5: Backtest Strategy (Backend -> AI Service)
$backtestBody = @{
    ticker = "MSFT"
    market = "US"
    strategy = "SMA_CROSS"
    days = 180
} | ConvertTo-Json

$backtestResponse = Test-Endpoint -Name "Backtest SMA_CROSS Strategy (MSFT)" `
    -Url "$baseUrl/api/backtest" `
    -Method "POST" `
    -Body $backtestBody `
    -Headers $authHeaders `
    -Validator { param($r)
        $r.runId -ne $null -and
        $r.result -ne $null -and
        $r.result.summary -ne $null -and
        $r.result.summary.ticker -eq "MSFT"
    }

Write-Host ""
Write-Host "Testing watchlist features..." -ForegroundColor Yellow
Write-Host ""

# Test 6: Add to Watchlist
$watchlistBody = @{
    ticker = "GOOGL"
    market = "US"
} | ConvertTo-Json

Test-Endpoint -Name "Add GOOGL to Watchlist" `
    -Url "$baseUrl/api/watchlist" `
    -Method "POST" `
    -Body $watchlistBody `
    -Headers $authHeaders `
    -Validator { param($r) $true }

# Test 7: Get Watchlist
Test-Endpoint -Name "Get Watchlist with Prices" `
    -Url "$baseUrl/api/watchlist?test=false" `
    -Headers $authHeaders `
    -Validator { param($r)
        $r -is [array] -and $r.Count -gt 0
    }

Write-Host ""
Write-Host "Testing new features..." -ForegroundColor Yellow
Write-Host ""

# Test 8: Stock Screener (via Backend)
$screenerBody = @{
    market = "US"
    filters = @{
        peMax = 30
        roeMin = 0.10
    }
    sortBy = "pe"
    sortOrder = "asc"
    page = 0
    size = 10
} | ConvertTo-Json -Depth 3

Test-Endpoint -Name "Stock Screener" `
    -Url "$baseUrl/api/screener" `
    -Method "POST" `
    -Body $screenerBody `
    -Headers $authHeaders `
    -Validator { param($r)
        $r -is [array] -and $r.Count -gt 0
    }

# Test 9: Correlation Analysis (via Backend)
$correlationBody = @{
    tickers = @("AAPL", "MSFT", "GOOGL")
    market = "US"
    days = 90
} | ConvertTo-Json

Test-Endpoint -Name "Correlation Analysis" `
    -Url "$baseUrl/api/correlation" `
    -Method "POST" `
    -Body $correlationBody `
    -Headers $authHeaders `
    -Validator { param($r)
        $r.correlationMatrix -ne $null -and
        $r.correlationMatrix.Count -eq 3
    }

# Test 10: Monte Carlo Simulation (via Backend)
$monteCarloBody = @{
    ticker = "AAPL"
    market = "US"
    days = 90
    simulations = 100
    forecastDays = 30
} | ConvertTo-Json

Test-Endpoint -Name "Monte Carlo Simulation" `
    -Url "$baseUrl/api/monte-carlo" `
    -Method "POST" `
    -Body $monteCarloBody `
    -Headers $authHeaders `
    -Validator { param($r)
        $r.paths -ne $null -and
        $r.distribution -ne $null -and
        $r.stats -ne $null
    }

Write-Host ""
Write-Host "Testing analysis history..." -ForegroundColor Yellow
Write-Host ""

# Test 11: My Analysis Runs
Test-Endpoint -Name "Get My Analysis Runs" `
    -Url "$baseUrl/api/analysis/history" `
    -Headers $authHeaders `
    -Validator { param($r)
        $r.items -ne $null -and $r.items.Count -ge 1
    }

# Test 12: My Backtest Runs
Test-Endpoint -Name "Get My Backtest Runs" `
    -Url "$baseUrl/api/backtest/history" `
    -Headers $authHeaders `
    -Validator { param($r)
        $r.items -ne $null
    }

Write-Host ""
Write-Host "=================================================="
Write-Host "Integration Test Summary"
Write-Host "=================================================="
Write-Host ""
Write-Host "Passed: $passed" -ForegroundColor Green
Write-Host "Failed: $failed" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
Write-Host ""

if ($failed -eq 0) {
    Write-Host "SUCCESS: All integration tests passed!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "FAILURE: $failed test(s) failed" -ForegroundColor Red
    exit 1
}
