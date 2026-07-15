[CmdletBinding()]
param(
    [string]$DatabaseUrl = $env:SPRING_DATASOURCE_URL,
    [string]$DatabaseUser = $env:SPRING_DATASOURCE_USERNAME,
    [ValidateRange(1, 100000)]
    [int]$BatchSize = 10000,
    [ValidateRange(0, 2147483647)]
    [int]$StartAfterId = 0,
    [ValidateRange(0, 2147483647)]
    [int]$MaxBatches = 0
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($DatabaseUrl)) {
    throw 'DatabaseUrl is required. Set SPRING_DATASOURCE_URL or pass -DatabaseUrl.'
}

$psql = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psql) {
    throw 'psql was not found in PATH.'
}

$connection = $DatabaseUrl -replace '^jdbc:', ''
$baseArgs = @('--no-align', '--tuples-only', '--set', 'ON_ERROR_STOP=1', '--dbname', $connection)
if (-not [string]::IsNullOrWhiteSpace($DatabaseUser)) {
    $baseArgs += @('--username', $DatabaseUser)
}

$lastId = $StartAfterId
$batchNumber = 0
$totalScanned = [long]0
$totalUpdated = [long]0

while ($true) {
    if ($MaxBatches -gt 0 -and $batchNumber -ge $MaxBatches) {
        Write-Warning "Reached MaxBatches=$MaxBatches. Resume with -StartAfterId $lastId."
        break
    }

    $query = "SELECT last_id || ',' || scanned_count || ',' || updated_count FROM app.backfill_scan_source_record_keys($lastId, $BatchSize);"
    $output = & $psql.Source @baseArgs --command $query
    if ($LASTEXITCODE -ne 0) {
        throw "Backfill query failed with exit code $LASTEXITCODE. Resume with -StartAfterId $lastId."
    }

    $row = $output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Last 1
    if ([string]::IsNullOrWhiteSpace($row)) {
        throw "Backfill returned no result. Resume with -StartAfterId $lastId."
    }

    $parts = $row.Trim().Split(',')
    if ($parts.Count -ne 3) {
        throw "Unexpected backfill result: $row"
    }

    $nextId = [int]$parts[0]
    $scanned = [int]$parts[1]
    $updated = [int]$parts[2]
    $batchNumber++
    $totalScanned += $scanned
    $totalUpdated += $updated

    Write-Host ("Batch {0}: lastId={1}, scanned={2}, keysWritten={3}" -f $batchNumber, $nextId, $scanned, $updated)

    if ($scanned -eq 0) {
        Write-Host ("Backfill completed. batches={0}, scanned={1}, keysWritten={2}" -f $batchNumber, $totalScanned, $totalUpdated)
        break
    }

    if ($nextId -le $lastId) {
        throw "Backfill cursor did not advance. Current lastId=$lastId, nextId=$nextId."
    }

    $lastId = $nextId
}
