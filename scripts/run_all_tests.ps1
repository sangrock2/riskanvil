# Stock-AI Comprehensive Test Suite
# Runs the current canonical local checks:
# - backend build
# - backend full test suite
# - frontend test suite
# - frontend build
# - AI unit tests
# - optional local API smoke if backend/AI are already running
# - optional local browser E2E if frontend/backend/AI are already running

$ErrorActionPreference = "Stop"

# scripts/ 하위에 있으므로 프로젝트 루트로 이동
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot

if (-not $env:GRADLE_USER_HOME -or [string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) {
    $env:GRADLE_USER_HOME = Join-Path $projectRoot "backend\.gradle-local-review"
}
New-Item -ItemType Directory -Force -Path $env:GRADLE_USER_HOME | Out-Null

Write-Host "================================" -ForegroundColor Cyan
Write-Host "Stock-AI Comprehensive Test Suite" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

$results = New-Object System.Collections.Generic.List[object]

function Add-Result {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail = ""
    )

    $results.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
    })
}

# Test 1: Backend Build
Write-Host "[1/7] Testing Backend Build..." -ForegroundColor Yellow
try {
    Set-Location backend
    $output = & .\gradlew.bat clean build -x test 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  + Backend build successful" -ForegroundColor Green
        Add-Result "Backend Build" "PASS"
    } else {
        Write-Host "  x Backend build failed" -ForegroundColor Red
        Write-Host $output
        Add-Result "Backend Build" "FAIL" "backend clean build -x test failed"
    }
} catch {
    Write-Host "  x Backend build error: $_" -ForegroundColor Red
    Add-Result "Backend Build" "FAIL" "$_"
} finally {
    Set-Location $projectRoot
}

# Test 2: Backend Full Test Suite
Write-Host ""
Write-Host "[2/7] Running Backend Full Test Suite..." -ForegroundColor Yellow
try {
    Set-Location backend
    $output = & .\gradlew.bat test 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  + Backend tests passed" -ForegroundColor Green
        Add-Result "Backend Tests" "PASS"
    } else {
        Write-Host "  x Backend tests failed" -ForegroundColor Red
        Write-Host "  See: backend\build\reports\tests\test\index.html"
        Add-Result "Backend Tests" "FAIL" "backend full test suite failed"
    }
} catch {
    Write-Host "  x Backend test error: $_" -ForegroundColor Red
    Add-Result "Backend Tests" "FAIL" "$_"
} finally {
    Set-Location $projectRoot
}

# Test 3: Frontend Test Suite
Write-Host ""
Write-Host "[3/7] Running Frontend Test Suite..." -ForegroundColor Yellow
try {
    Set-Location frontend
    $output = npm test 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  + Frontend tests passed" -ForegroundColor Green
        Add-Result "Frontend Tests" "PASS"
    } else {
        Write-Host "  x Frontend tests failed" -ForegroundColor Red
        Write-Host $output
        Add-Result "Frontend Tests" "FAIL" "frontend vitest suite failed"
    }
} catch {
    Write-Host "  x Frontend test error: $_" -ForegroundColor Red
    Add-Result "Frontend Tests" "FAIL" "$_"
} finally {
    Set-Location $projectRoot
}

# Test 4: Frontend Build
Write-Host ""
Write-Host "[4/7] Testing Frontend Build..." -ForegroundColor Yellow
try {
    Set-Location frontend
    $output = npm run build 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  + Frontend build successful" -ForegroundColor Green
        Add-Result "Frontend Build" "PASS"
    } else {
        Write-Host "  x Frontend build failed" -ForegroundColor Red
        Write-Host $output
        Add-Result "Frontend Build" "FAIL" "frontend vite build failed"
    }
} catch {
    Write-Host "  x Frontend build error: $_" -ForegroundColor Red
    Add-Result "Frontend Build" "FAIL" "$_"
} finally {
    Set-Location $projectRoot
}

# Test 5: AI Unit Tests
Write-Host ""
Write-Host "[5/7] Running AI Unit Tests..." -ForegroundColor Yellow
try {
    Set-Location ai
    $unitOutput = python -m pytest -q tests 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  + AI unit tests passed" -ForegroundColor Green
        Add-Result "AI Unit Tests" "PASS"
    } else {
        Write-Host "  x AI unit tests failed" -ForegroundColor Red
        Write-Host $unitOutput
        Add-Result "AI Unit Tests" "FAIL" "ai pytest suite failed"
    }
} catch {
    Write-Host "  x AI unit test error: $_" -ForegroundColor Red
    Add-Result "AI Unit Tests" "FAIL" "$_"
} finally {
    Set-Location $projectRoot
}

# Test 6: Optional local API smoke
Write-Host ""
Write-Host "[6/7] Optional Local API Smoke..." -ForegroundColor Yellow
try {
    $backendReady = $false
    $aiReady = $false
    $frontendBaseUrl = $null

    try {
        $backendHealth = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health/readiness" -Method GET -TimeoutSec 2 -ErrorAction Stop
        $backendReady = ($backendHealth.StatusCode -eq 200)
    } catch {
        $backendReady = $false
    }

    try {
        $aiHealth = Invoke-WebRequest -Uri "http://localhost:8000/health" -Method GET -TimeoutSec 2 -ErrorAction Stop
        $aiReady = ($aiHealth.StatusCode -eq 200)
    } catch {
        $aiReady = $false
    }

    foreach ($candidate in @("http://localhost:3000", "http://localhost")) {
        try {
            $frontendProbe = Invoke-WebRequest -Uri $candidate -Method GET -TimeoutSec 2 -ErrorAction Stop
            if ($frontendProbe.StatusCode -ge 200 -and $frontendProbe.StatusCode -lt 400) {
                $frontendBaseUrl = $candidate
                break
            }
        } catch {
            continue
        }
    }

    if ($backendReady -and $aiReady) {
        $output = & "$PSScriptRoot\integration_tests.ps1" 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  + Local API smoke passed" -ForegroundColor Green
            Add-Result "Local API Smoke" "PASS"
        } else {
            Write-Host "  x Local API smoke failed" -ForegroundColor Red
            Write-Host $output
            Add-Result "Local API Smoke" "FAIL" "scripts/integration_tests.ps1 failed"
        }
    } else {
        Write-Host "  ! Backend or AI service is not running locally - skipping API smoke" -ForegroundColor Yellow
        Write-Host "    Expected: backend http://localhost:8080, ai http://localhost:8000" -ForegroundColor DarkGray
        Add-Result "Local API Smoke" "SKIP" "localhost backend/ai not running"
    }
} catch {
    Write-Host "  ! Local API smoke skipped due to error: $_" -ForegroundColor Yellow
    Add-Result "Local API Smoke" "SKIP" "$_"
}

# Test 7: Optional local browser E2E
Write-Host ""
Write-Host "[7/7] Optional Local Browser E2E..." -ForegroundColor Yellow
try {
    if ($backendReady -and $aiReady -and $frontendBaseUrl) {
        Set-Location frontend
        $env:PLAYWRIGHT_BASE_URL = $frontendBaseUrl
        $output = npm run test:e2e 2>&1
        $browserOutputText = ($output | Out-String)

        if ($LASTEXITCODE -eq 0) {
            Write-Host "  + Local browser E2E passed" -ForegroundColor Green
            Add-Result "Local Browser E2E" "PASS"
        } elseif ($browserOutputText -match "Executable doesn't exist|Please run the following command to download new browsers|playwright install") {
            Write-Host "  ! Playwright browser runtime not installed - skipping browser E2E" -ForegroundColor Yellow
            Add-Result "Local Browser E2E" "SKIP" "Playwright browser runtime is not installed"
        } else {
            Write-Host "  x Local browser E2E failed" -ForegroundColor Red
            Write-Host $output
            Add-Result "Local Browser E2E" "FAIL" "frontend playwright smoke suite failed"
        }
    } else {
        Write-Host "  ! Frontend or backend/AI service is not running locally - skipping browser E2E" -ForegroundColor Yellow
        Write-Host "    Expected: frontend http://localhost:3000 or http://localhost, backend http://localhost:8080, ai http://localhost:8000" -ForegroundColor DarkGray
        Add-Result "Local Browser E2E" "SKIP" "localhost frontend/backend/ai not running"
    }
} catch {
    Write-Host "  ! Local browser E2E skipped due to error: $_" -ForegroundColor Yellow
    Add-Result "Local Browser E2E" "SKIP" "$_"
} finally {
    Remove-Item Env:PLAYWRIGHT_BASE_URL -ErrorAction SilentlyContinue
    Set-Location $projectRoot
}

# Summary
Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "Test Summary" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan

$passed = @($results | Where-Object { $_.Status -eq "PASS" }).Count
$failed = @($results | Where-Object { $_.Status -eq "FAIL" }).Count
$skipped = @($results | Where-Object { $_.Status -eq "SKIP" }).Count
$optionalChecks = @("Local API Smoke", "Local Browser E2E")
$requiredTotal = @($results | Where-Object { $optionalChecks -notcontains $_.Name }).Count
$requiredPassed = @($results | Where-Object { $optionalChecks -notcontains $_.Name -and $_.Status -eq "PASS" }).Count

foreach ($result in $results) {
    $label = switch ($result.Status) {
        "PASS" { "+ PASS" }
        "FAIL" { "x FAIL" }
        default { "- SKIP" }
    }
    $color = switch ($result.Status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        default { "Yellow" }
    }
    Write-Host ("{0,-20} : {1}" -f $result.Name, $label) -ForegroundColor $color
}

Write-Host ""
Write-Host "Required: $requiredPassed/$requiredTotal passed" -ForegroundColor $(if ($failed -eq 0 -and $requiredPassed -eq $requiredTotal) { "Green" } else { "Yellow" })
Write-Host "Optional: skipped=$skipped" -ForegroundColor Yellow

if ($failed -eq 0 -and $requiredPassed -eq $requiredTotal) {
    Write-Host ""
    Write-Host "All tests passed! System is ready for deployment." -ForegroundColor Green
    exit 0
} else {
    Write-Host ""
    Write-Host "Some tests failed. Please review errors above." -ForegroundColor Yellow
    exit 1
}
