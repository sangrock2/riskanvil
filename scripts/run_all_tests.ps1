# Stock-AI Comprehensive Test Suite
# Runs all tests across backend, AI service, and frontend

$ErrorActionPreference = "Stop"

# scripts/ 하위에 있으므로 프로젝트 루트로 이동
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot

Write-Host "================================" -ForegroundColor Cyan
Write-Host "Stock-AI Comprehensive Test Suite" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

$testResults = @{
    "Backend Build" = $false
    "Backend Tests" = $false
    "Frontend Build" = $false
    "AI Service Tests" = $false
}

# Test 1: Backend Build
Write-Host "[1/4] Testing Backend Build..." -ForegroundColor Yellow
try {
    Set-Location backend
    $output = & .\gradlew.bat clean build -x test 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  + Backend build successful" -ForegroundColor Green
        $testResults["Backend Build"] = $true
    } else {
        Write-Host "  x Backend build failed" -ForegroundColor Red
        Write-Host $output
    }
} catch {
    Write-Host "  x Backend build error: $_" -ForegroundColor Red
} finally {
    Set-Location $projectRoot
}

# Test 2: Backend Unit Tests
Write-Host ""
Write-Host "[2/4] Running Backend Unit Tests..." -ForegroundColor Yellow
try {
    Set-Location backend
    $output = & .\gradlew.bat test `
      --tests "com.sw103302.backend.service.PriceServiceTest" `
      --tests "com.sw103302.backend.service.PortfolioServiceTest" `
      --tests "com.sw103302.backend.service.AnalysisServiceTest" `
      --tests "com.sw103302.backend.dto.UpdateSettingsRequestValidationTest" `
      --tests "com.sw103302.backend.util.GlobalExceptionHandlerContractTest" 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  + Backend tests passed" -ForegroundColor Green
        $testResults["Backend Tests"] = $true
    } else {
        Write-Host "  x Backend tests failed" -ForegroundColor Red
        Write-Host "  See: backend\build\reports\tests\test\index.html"
    }
} catch {
    Write-Host "  x Backend test error: $_" -ForegroundColor Red
} finally {
    Set-Location $projectRoot
}

# Test 3: Frontend Build
Write-Host ""
Write-Host "[3/4] Testing Frontend Build..." -ForegroundColor Yellow
try {
    Set-Location frontend
    $output = npm run build 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  + Frontend build successful" -ForegroundColor Green
        $testResults["Frontend Build"] = $true
    } else {
        Write-Host "  x Frontend build failed" -ForegroundColor Red
        Write-Host $output
    }
} catch {
    Write-Host "  x Frontend build error: $_" -ForegroundColor Red
} finally {
    Set-Location $projectRoot
}

# Test 4: AI Service Endpoint Tests
Write-Host ""
Write-Host "[4/4] Testing AI Service Endpoints..." -ForegroundColor Yellow
Write-Host "  NOTE: Requires AI service to be running on port 8000" -ForegroundColor DarkGray

try {
    Set-Location ai
    $unitOutput = python -m pytest -q tests 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  + AI unit tests passed" -ForegroundColor Green
    } else {
        Write-Host "  x AI unit tests failed" -ForegroundColor Red
    }
    Set-Location $projectRoot

    $response = Invoke-WebRequest -Uri "http://localhost:8000/health" -Method GET -TimeoutSec 2 -ErrorAction SilentlyContinue
    if ($response.StatusCode -eq 200) {
        Write-Host "  + AI service is running" -ForegroundColor Green

        Set-Location ai
        $output = python test_endpoints.py 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  + AI endpoint tests passed" -ForegroundColor Green
            $testResults["AI Service Tests"] = $true
        } else {
            Write-Host "  x AI endpoint tests failed" -ForegroundColor Red
        }
        Set-Location $projectRoot
    } else {
        Write-Host "  ! AI service not running - skipping endpoint tests" -ForegroundColor Yellow
        Write-Host "    Start with: cd ai && python -m uvicorn main:app --reload" -ForegroundColor DarkGray
    }
} catch {
    Write-Host "  ! AI service not running - skipping endpoint tests" -ForegroundColor Yellow
    Write-Host "    Start with: cd ai && python -m uvicorn main:app --reload" -ForegroundColor DarkGray
}

# Summary
Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "Test Summary" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan

$passed = 0
$total = 0
foreach ($test in $testResults.GetEnumerator()) {
    $total++
    $status = if ($test.Value) {
        $passed++
        "+ PASS"
    } else {
        "x FAIL"
    }
    $color = if ($test.Value) { "Green" } else { "Red" }
    Write-Host ("{0,-20} : {1}" -f $test.Key, $status) -ForegroundColor $color
}

Write-Host ""
Write-Host "Total: $passed/$total tests passed" -ForegroundColor $(if ($passed -eq $total) { "Green" } else { "Yellow" })

if ($passed -eq $total) {
    Write-Host ""
    Write-Host "All tests passed! System is ready for deployment." -ForegroundColor Green
    exit 0
} else {
    Write-Host ""
    Write-Host "Some tests failed. Please review errors above." -ForegroundColor Yellow
    exit 1
}
