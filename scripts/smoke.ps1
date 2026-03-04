# smoke.ps1
$ErrorActionPreference = "Stop"

$BASE = "http://localhost:8080"
$EMAIL = "test@example.com"
$PASSWORD = "password1234"

function Try-PostJson($url, $body, $headers=@{}) {
  try {
    return Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType "application/json" -Body ($body | ConvertTo-Json -Depth 20)
  } catch {
    Write-Host "POST failed: $url"
    if ($_.Exception.Response) {
      $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
      Write-Host $reader.ReadToEnd()
    }
    throw
  }
}

function Try-Get($url, $headers=@{}) {
  try {
    return Invoke-RestMethod -Method Get -Uri $url -Headers $headers
  } catch {
    Write-Host "GET failed: $url"
    if ($_.Exception.Response) {
      $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
      Write-Host $reader.ReadToEnd()
    }
    throw
  }
}

Write-Host "1) Login..."
$login = Try-PostJson "$BASE/api/auth/login" @{ email=$EMAIL; password=$PASSWORD }
$token = $login.accessToken
if (-not $token) { throw "No token in login response" }
$H = @{ Authorization = "Bearer $token" }

Write-Host "2) Analysis run..."
$analysis = Try-PostJson "$BASE/api/analysis" @{
  ticker="AAPL"; market="US"; horizonDays=252; riskProfile="moderate"
} $H

$analysisRunId = $analysis.runId
if (-not $analysisRunId) { throw "No runId from analysis" }
Write-Host "  analysisRunId=$analysisRunId"

Write-Host "3) Analysis detail..."
$analysisDetail = Try-Get "$BASE/api/analysis/$analysisRunId" $H
if (-not $analysisDetail) { throw "Empty analysis detail" }

Write-Host "4) Backtest run..."
$bt = Try-PostJson "$BASE/api/backtest" @{
  ticker="AAPL"; market="US"; strategy="SMA_CROSS";
  start=$null; end=$null; initialCapital=1000000; feeBps=5
} $H

$btRunId = $bt.runId
if (-not $btRunId) { throw "No runId from backtest" }
Write-Host "  backtestRunId=$btRunId"

Write-Host "5) Backtest detail..."
$btDetail = Try-Get "$BASE/api/backtest/$btRunId" $H
if (-not $btDetail) { throw "Empty backtest detail" }

Write-Host "6) History page..."
$hist = Try-Get "$BASE/api/backtest/history?page=0&size=5&sort=createdAt,desc" $H
if (-not $hist.items) { throw "No items in history" }

Write-Host "OK SMOKE TEST OK"
