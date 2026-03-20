param(
    [Parameter(Mandatory = $true)]
    [string]$PublicAppUrl,
    [string]$BackendHealthUrl = "",
    [string]$AiHealthUrl = "",
    [string]$FrontendDeepLinkPath = "/login",
    [string]$OutputPath = "artifacts/reports/public-service-verification.json"
)

$ErrorActionPreference = "Stop"

function Resolve-FinalUrl {
    param(
        [object]$Response,
        [string]$FallbackUrl
    )

    if ($null -ne $Response.BaseResponse) {
        if ($null -ne $Response.BaseResponse.ResponseUri) {
            return $Response.BaseResponse.ResponseUri.AbsoluteUri
        }

        if ($null -ne $Response.BaseResponse.RequestMessage -and $null -ne $Response.BaseResponse.RequestMessage.RequestUri) {
            return $Response.BaseResponse.RequestMessage.RequestUri.AbsoluteUri
        }
    }

    if ($null -ne $Response.Headers -and $null -ne $Response.Headers.Location) {
        $location = [string]$Response.Headers.Location
        if ([string]::IsNullOrWhiteSpace($location)) {
            return $FallbackUrl
        }

        if ([uri]::IsWellFormedUriString($location, [System.UriKind]::Absolute)) {
            return $location
        }

        return ([uri]::new(([uri]$FallbackUrl), $location)).AbsoluteUri
    }

    return $FallbackUrl
}

function Test-HttpEndpoint {
    param(
        [string]$Url,
        [int[]]$ExpectedStatus = @(200),
        [int]$TimeoutSec = 20,
        [int]$MaxAttempts = 3,
        [int]$DelaySec = 5
    )

    $result = @{
        url = $Url
        ok = $false
        status = $null
        message = ""
        checkedAt = (Get-Date).ToString("o")
        attempts = 0
        finalUrl = $null
    }

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt += 1) {
        $result.attempts = $attempt

        try {
            $resp = Invoke-WebRequest -Uri $Url -Method GET -TimeoutSec $TimeoutSec -MaximumRedirection 5
            $result.status = [int]$resp.StatusCode
            $result.finalUrl = Resolve-FinalUrl -Response $resp -FallbackUrl $Url

            if ($ExpectedStatus -contains [int]$resp.StatusCode) {
                $result.ok = $true
                $result.message = if ($attempt -eq 1) { "reachable" } else { "reachable after retry" }
            } else {
                $result.message = "unexpected status code"
            }
        } catch {
            if ($_.Exception.Response) {
                $result.status = [int]$_.Exception.Response.StatusCode
            }
            $result.message = $_.Exception.Message
        }

        if ($result.ok -or $attempt -eq $MaxAttempts) {
            break
        }

        Start-Sleep -Seconds $DelaySec
    }

    return $result
}

function Test-FrontendDeepLink {
    param(
        [string]$BaseUrl,
        [string]$RoutePath
    )

    $normalizedBaseUrl = $BaseUrl.TrimEnd("/")
    $normalizedRoutePath = if ($RoutePath.StartsWith("/")) { $RoutePath } else { "/$RoutePath" }
    $targetUrl = "$normalizedBaseUrl$normalizedRoutePath"
    $result = Test-HttpEndpoint -Url $targetUrl -ExpectedStatus @(200) -TimeoutSec 20 -MaxAttempts 2 -DelaySec 3

    if (-not $result.ok) {
        return $result
    }

    $requestedPath = ([uri]$targetUrl).AbsolutePath
    $finalUri = [uri]$result.finalUrl
    if ($finalUri.AbsolutePath -ne $requestedPath) {
        $result.ok = $false
        $result.message = "frontend deep link redirected to $($finalUri.AbsolutePath)"
    } else {
        $result.message = "frontend deep link preserved"
    }

    return $result
}

$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot

$checks = @()
$checks += Test-HttpEndpoint -Url $PublicAppUrl -ExpectedStatus @(200, 301, 302)
$checks += Test-FrontendDeepLink -BaseUrl $PublicAppUrl -RoutePath $FrontendDeepLinkPath

if (-not [string]::IsNullOrWhiteSpace($BackendHealthUrl)) {
    $checks += Test-HttpEndpoint -Url $BackendHealthUrl -ExpectedStatus @(200) -TimeoutSec 30 -MaxAttempts 3 -DelaySec 10
}
if (-not [string]::IsNullOrWhiteSpace($AiHealthUrl)) {
    $checks += Test-HttpEndpoint -Url $AiHealthUrl -ExpectedStatus @(200) -TimeoutSec 30 -MaxAttempts 3 -DelaySec 10
}

$payload = @{
    generatedAt = (Get-Date).ToString("o")
    publicAppUrl = $PublicAppUrl
    frontendDeepLinkPath = $FrontendDeepLinkPath
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
