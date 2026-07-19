[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [string]$MetricsDirectory = 'C:\MRR-Probe\metrics',
    [string]$WebhookUrl = '',
    [int]$TimeoutSeconds = 10
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $MetricsDirectory | Out-Null
$metricsFile = Join-Path $MetricsDirectory 'mrr-external-probe.prom'
$stateFile = Join-Path $MetricsDirectory 'mrr-external-probe-state.json'
$started = Get-Date
$targets = @(
    @{ Name = 'gateway'; Url = "$($BaseUrl.TrimEnd('/'))/healthz.txt"; Expected = 'MRR_FRONTEND_OK' },
    @{ Name = 'backend'; Url = "$($BaseUrl.TrimEnd('/'))/api/v1/public/status/ping"; Expected = 'UP' }
)
$results = @()

foreach ($target in $targets) {
    $ok = $false
    $statusCode = 0
    $durationMs = 0
    $errorMessage = $null
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest -Uri $target.Url -UseBasicParsing -TimeoutSec $TimeoutSeconds
        $statusCode = [int]$response.StatusCode
        $ok = $statusCode -eq 200 -and $response.Content.Contains($target.Expected)
        if (-not $ok) { $errorMessage = 'Unexpected status code or response body' }
    }
    catch {
        $errorMessage = $_.Exception.Message
    }
    finally {
        $watch.Stop()
        $durationMs = $watch.Elapsed.TotalMilliseconds
    }
    $results += [pscustomobject]@{
        name = $target.Name
        url = $target.Url
        ok = $ok
        statusCode = $statusCode
        durationMs = [math]::Round($durationMs, 2)
        error = $errorMessage
    }
}

$overallUp = ($results | Where-Object { -not $_.ok }).Count -eq 0
$epoch = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$metricLines = @(
    '# HELP mrr_external_probe_up Whether the external MRR probe succeeded.'
    '# TYPE mrr_external_probe_up gauge'
)
foreach ($result in $results) {
    $value = if ($result.ok) { 1 } else { 0 }
    $metricLines += "mrr_external_probe_up{target=`"$($result.name)`"} $value"
    $metricLines += "mrr_external_probe_duration_seconds{target=`"$($result.name)`"} $([math]::Round($result.durationMs / 1000, 3))"
}
$metricLines += "mrr_external_probe_last_run_timestamp_seconds $epoch"
$metricLines | Set-Content -Encoding ASCII $metricsFile

$previous = $null
if (Test-Path $stateFile) {
    try { $previous = Get-Content $stateFile -Raw | ConvertFrom-Json } catch { $previous = $null }
}
$state = [ordered]@{
    up = $overallUp
    checkedAt = (Get-Date).ToUniversalTime().ToString('o')
    results = $results
}
$state | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 $stateFile

$stateChanged = $null -eq $previous -or [bool]$previous.up -ne $overallUp
if ($stateChanged -and $WebhookUrl) {
    $payload = [ordered]@{
        system = 'MRR'
        status = if ($overallUp) { 'RECOVERED' } else { 'DOWN' }
        checkedAt = $state.checkedAt
        results = $results
    } | ConvertTo-Json -Depth 6
    try {
        Invoke-RestMethod -Method Post -Uri $WebhookUrl -ContentType 'application/json' -Body $payload -TimeoutSec $TimeoutSeconds | Out-Null
    }
    catch {
        Write-Warning "Unable to send probe webhook: $($_.Exception.Message)"
    }
}

if (-not $overallUp) {
    $failed = ($results | Where-Object { -not $_.ok } | ForEach-Object { "$($_.name): $($_.error)" }) -join '; '
    Write-Error "MRR external probe failed: $failed"
}

Write-Host "MRR external probe completed in $([math]::Round(((Get-Date) - $started).TotalSeconds, 2)) seconds. Up=$overallUp"
