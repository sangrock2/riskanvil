param(
    [string]$UserEmail = "",
    [switch]$AllUsers,
    [switch]$NoAutoStart,

    [string]$ComposeFile = "docker-compose.yml",
    [string]$PostgresService = "postgres",
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

function Invoke-DockerPsql(
    [string]$composePath,
    [string]$service,
    [string]$database,
    [string]$username,
    [string]$password,
    [string]$query,
    [string]$stdinSql,
    [hashtable]$Variables = @{}
) {
    $args = @(
        "compose", "-f", $composePath,
        "exec", "-T", "-e", "PGPASSWORD=$password",
        $service,
        "psql",
        "--no-psqlrc",
        "--username", $username,
        "--dbname", $database,
        "--tuples-only",
        "--no-align",
        "-F", "`t",
        "-v", "ON_ERROR_STOP=1"
    )

    foreach ($key in ($Variables.Keys | Sort-Object)) {
        $args += @("-v", "$key=$($Variables[$key])")
    }

    if ($query) {
        $args += @("-c", $query)
        return (& docker @args)
    }

    if ($stdinSql) {
        return ($stdinSql | & docker @args)
    }

    throw "Either query or stdinSql must be provided."
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

Write-Host "Checking PostgreSQL container status..." -ForegroundColor Cyan
$psOut = & docker compose -f $composePath ps --status running $PostgresService
if ($LASTEXITCODE -ne 0 -or ($psOut -notmatch $PostgresService)) {
    if ($NoAutoStart) {
        throw "PostgreSQL service '$PostgresService' is not running. Start it first with: docker compose up -d postgres"
    }

    Write-Host "PostgreSQL is not running. Attempting auto-start..." -ForegroundColor Yellow
    $null = & docker compose -f $composePath up -d $PostgresService
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to auto-start PostgreSQL service '$PostgresService'. Run manually: docker compose up -d postgres"
    }

    Start-Sleep -Seconds 3
    $psOut = & docker compose -f $composePath ps --status running $PostgresService
    if ($LASTEXITCODE -ne 0 -or ($psOut -notmatch $PostgresService)) {
        throw "PostgreSQL service '$PostgresService' is still not running after auto-start. Check docker compose logs postgres."
    }

    Write-Host "PostgreSQL auto-started successfully." -ForegroundColor Green
}

$envMap = Read-DotEnv $envPath
$dbName = if ($envMap.ContainsKey("POSTGRES_DB")) { $envMap["POSTGRES_DB"] } elseif ($envMap.ContainsKey("DB_NAME")) { $envMap["DB_NAME"] } elseif ($env:POSTGRES_DB) { $env:POSTGRES_DB } elseif ($env:DB_NAME) { $env:DB_NAME } else { "stock_ai" }
$dbUser = if ($envMap.ContainsKey("POSTGRES_USER")) { $envMap["POSTGRES_USER"] } elseif ($envMap.ContainsKey("DB_USERNAME")) { $envMap["DB_USERNAME"] } elseif ($env:POSTGRES_USER) { $env:POSTGRES_USER } elseif ($env:DB_USERNAME) { $env:DB_USERNAME } else { "postgres" }
$dbPassword = if ($envMap.ContainsKey("POSTGRES_PASSWORD")) { $envMap["POSTGRES_PASSWORD"] } elseif ($envMap.ContainsKey("DB_PASSWORD")) { $envMap["DB_PASSWORD"] } elseif ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } elseif ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "" }

if (-not $dbPassword) {
    try {
        $dbPassword = (& docker compose -f $composePath exec -T $PostgresService sh -lc "printenv POSTGRES_PASSWORD").Trim()
    } catch {
        $dbPassword = ""
    }
}

if (-not $dbUser -or $dbUser -eq "postgres") {
    try {
        $detectedUser = (& docker compose -f $composePath exec -T $PostgresService sh -lc "printenv POSTGRES_USER").Trim()
        if ($detectedUser) {
            $dbUser = $detectedUser
        }
    } catch {
        # keep fallback value
    }
}

if (-not $dbName -or $dbName -eq "stock_ai") {
    try {
        $detectedDb = (& docker compose -f $composePath exec -T $PostgresService sh -lc "printenv POSTGRES_DB").Trim()
        if ($detectedDb) {
            $dbName = $detectedDb
        }
    } catch {
        # keep fallback value
    }
}

if (-not $dbPassword) {
    throw "POSTGRES_PASSWORD was not found in .env, environment variables, or container env. Set it and retry."
}

$sqlTemplate = Get-Content -Path $seedTemplatePath -Raw -Encoding UTF8
if ($AllUsers) {
    Write-Host "Loading all users..." -ForegroundColor Cyan
    $rows = Invoke-DockerPsql -composePath $composePath -service $PostgresService -database $dbName -username $dbUser -password $dbPassword -query "SELECT id, email FROM users ORDER BY id;" -stdinSql ""
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
        $null = Invoke-DockerPsql -composePath $composePath -service $PostgresService -database $dbName -username $dbUser -password $dbPassword -query "" -stdinSql $sqlTemplate -Variables @{ target_user_id = $userId }
        if ($LASTEXITCODE -ne 0) {
            throw "Seed execution failed for user_id=$userId"
        }
    }

    Write-Host ""
    Write-Host "Seed completed successfully for all users." -ForegroundColor Green
} else {
    $escapedEmail = $UserEmail.Replace("'", "''")
    $userIdQuery = "SELECT id FROM users WHERE email = '$escapedEmail' LIMIT 1;"
    $userId = (Invoke-DockerPsql -composePath $composePath -service $PostgresService -database $dbName -username $dbUser -password $dbPassword -query $userIdQuery -stdinSql "").Trim()

    if (-not $userId) {
        throw "Target user not found. Register/login first, then re-run. Email: $UserEmail"
    }

    Write-Host "Target user found: id=$userId, email=$UserEmail" -ForegroundColor Green
    Write-Host "Applying demo seed data..." -ForegroundColor Cyan
    $null = Invoke-DockerPsql -composePath $composePath -service $PostgresService -database $dbName -username $dbUser -password $dbPassword -query "" -stdinSql $sqlTemplate -Variables @{ target_user_id = $userId }

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
