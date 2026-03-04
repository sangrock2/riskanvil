param(
    [string]$UserEmail = "",
    [switch]$AllUsers,
    [switch]$NoAutoStart,

    [string]$ComposeFile = "docker-compose.yml",
    [string]$MysqlService = "mysql",
    [string]$EnvFile = ".env"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-DotEnv([string]$path) {
    $map = @{}
    if (-not (Test-Path $path)) {
        return $map
    }

    foreach ($line in Get-Content -Path $path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if (-not $trimmed) { continue }
        if ($trimmed.StartsWith("#")) { continue }
        $parts = $trimmed -split "=", 2
        if ($parts.Length -ne 2) { continue }
        $key = $parts[0].Trim()
        $value = $parts[1].Trim()
        if ($value.StartsWith('"') -and $value.EndsWith('"')) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if ($value.StartsWith("'") -and $value.EndsWith("'")) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $map[$key] = $value
    }
    return $map
}

function Invoke-DockerMysql(
    [string]$composePath,
    [string]$service,
    [string]$database,
    [string]$password,
    [string]$query,
    [string]$stdinSql
) {
    $args = @(
        "compose", "-f", $composePath,
        "exec", "-T", $service,
        "mysql",
        "--batch",
        "--raw",
        "--skip-column-names",
        "--user=root",
        "--password=$password",
        "--database=$database"
    )

    if ($query) {
        $args += @("--execute", $query)
        return (& docker @args)
    }

    if ($stdinSql) {
        return ($stdinSql | & docker @args)
    }

    throw "Either query or stdinSql must be provided."
}

function Build-SeedSql(
    [string]$template,
    [string]$userId
) {
    if ($userId -notmatch "^\d+$") {
        throw "Invalid user id for seed SQL: $userId"
    }
    return "SET @target_user_id := $userId;`n$template"
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$composePath = Join-Path $repoRoot $ComposeFile
$envPath = Join-Path $repoRoot $EnvFile
$seedTemplatePath = Join-Path $repoRoot "scripts/sql/seed_demo_data.sql"

if (-not (Test-Path $composePath)) {
    throw "Compose file not found: $composePath"
}
if (-not (Test-Path $seedTemplatePath)) {
    throw "Seed SQL template not found: $seedTemplatePath"
}

if (-not $AllUsers -and [string]::IsNullOrWhiteSpace($UserEmail)) {
    throw "Provide -UserEmail or use -AllUsers."
}

try {
    $serverVersion = & docker info --format "{{.ServerVersion}}" 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $serverVersion) {
        throw "Docker server unavailable"
    }
} catch {
    throw "Docker engine is not running. Start Docker Desktop and retry."
}

Write-Host "Checking MySQL container status..." -ForegroundColor Cyan
$psOut = & docker compose -f $composePath ps --status running $MysqlService
if ($LASTEXITCODE -ne 0 -or ($psOut -notmatch $MysqlService)) {
    if ($NoAutoStart) {
        throw "MySQL service '$MysqlService' is not running. Start it first with: docker compose up -d mysql"
    }

    Write-Host "MySQL is not running. Attempting auto-start..." -ForegroundColor Yellow
    $null = & docker compose -f $composePath up -d $MysqlService
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to auto-start MySQL service '$MysqlService'. Run manually: docker compose up -d mysql"
    }

    Start-Sleep -Seconds 3
    $psOut = & docker compose -f $composePath ps --status running $MysqlService
    if ($LASTEXITCODE -ne 0 -or ($psOut -notmatch $MysqlService)) {
        throw "MySQL service '$MysqlService' is still not running after auto-start. Check docker compose logs mysql."
    }

    Write-Host "MySQL auto-started successfully." -ForegroundColor Green
}

$envMap = Read-DotEnv $envPath
$dbName = if ($envMap.ContainsKey("MYSQL_DATABASE")) { $envMap["MYSQL_DATABASE"] } elseif ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "stock_ai" }
$rootPassword = if ($envMap.ContainsKey("MYSQL_ROOT_PASSWORD")) { $envMap["MYSQL_ROOT_PASSWORD"] } elseif ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "" }

if (-not $rootPassword) {
    try {
        $rootPassword = (& docker compose -f $composePath exec -T $MysqlService sh -lc "printenv MYSQL_ROOT_PASSWORD").Trim()
    } catch {
        $rootPassword = ""
    }
}

if (-not $dbName -or $dbName -eq "stock_ai") {
    try {
        $detectedDb = (& docker compose -f $composePath exec -T $MysqlService sh -lc "printenv MYSQL_DATABASE").Trim()
        if ($detectedDb) {
            $dbName = $detectedDb
        }
    } catch {
        # keep fallback value
    }
}

if (-not $rootPassword) {
    throw "MYSQL_ROOT_PASSWORD was not found in .env, environment variables, or container env. Set it and retry."
}

$sqlTemplate = Get-Content -Path $seedTemplatePath -Raw -Encoding UTF8
if ($AllUsers) {
    Write-Host "Loading all users..." -ForegroundColor Cyan
    $rows = Invoke-DockerMysql -composePath $composePath -service $MysqlService -database $dbName -password $rootPassword -query "SELECT id, email FROM users ORDER BY id;" -stdinSql ""
    $rows = @($rows | Where-Object { $_ -and $_.Trim() -ne "" })
    if ($rows.Count -eq 0) {
        throw "No users found. Register at least one account first."
    }

    foreach ($line in $rows) {
        $parts = $line -split "`t", 2
        if ($parts.Length -lt 2) { continue }
        $userId = $parts[0].Trim()
        $email = $parts[1].Trim()
        if (-not $userId) { continue }

        Write-Host "Applying seed: user_id=$userId, email=$email" -ForegroundColor Green
        $sql = Build-SeedSql -template $sqlTemplate -userId $userId
        $null = Invoke-DockerMysql -composePath $composePath -service $MysqlService -database $dbName -password $rootPassword -query "" -stdinSql $sql
        if ($LASTEXITCODE -ne 0) {
            throw "Seed execution failed for user_id=$userId"
        }
    }

    Write-Host ""
    Write-Host "Seed completed successfully for all users." -ForegroundColor Green
} else {
    $escapedEmail = $UserEmail.Replace("'", "''")
    $userIdQuery = "SELECT id FROM users WHERE email = '$escapedEmail' LIMIT 1;"
    $userId = (Invoke-DockerMysql -composePath $composePath -service $MysqlService -database $dbName -password $rootPassword -query $userIdQuery -stdinSql "").Trim()

    if (-not $userId) {
        throw "Target user not found. Register/login first, then re-run. Email: $UserEmail"
    }

    Write-Host "Target user found: id=$userId, email=$UserEmail" -ForegroundColor Green
    $sql = Build-SeedSql -template $sqlTemplate -userId $userId

    Write-Host "Applying demo seed data..." -ForegroundColor Cyan
    $null = Invoke-DockerMysql -composePath $composePath -service $MysqlService -database $dbName -password $rootPassword -query "" -stdinSql $sql

    if ($LASTEXITCODE -ne 0) {
        throw "Seed execution failed."
    }

    Write-Host ""
    Write-Host "Seed completed successfully." -ForegroundColor Green
    Write-Host "User: $UserEmail (id=$userId)"
}

Write-Host "Marker: demo-seed-v1"
Write-Host ""
Write-Host "Sample pages to verify:" -ForegroundColor Cyan
Write-Host "  - /dashboard"
Write-Host "  - /watchlist"
Write-Host "  - /portfolio"
Write-Host "  - /dividends"
Write-Host "  - /earnings"
Write-Host "  - /risk-dashboard"
Write-Host "  - /paper-trading"
Write-Host "  - /usage"
Write-Host "  - /chatbot"
