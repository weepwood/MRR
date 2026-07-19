[CmdletBinding()]
param(
    [string]$Root = 'C:\MRR',
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\16\bin',
    [string]$Database = 'imgapi',
    [string]$HostName = '127.0.0.1',
    [int]$Port = 5432,
    [string]$User = 'mrr_backup',
    [string]$PgPassFile = 'C:\MRR\secrets\pgpass.conf',
    [int]$RetentionDays = 30
)

$ErrorActionPreference = 'Stop'
$backupDir = Join-Path $Root 'backups\postgresql\logical'
$metricsDir = Join-Path $Root 'monitoring-data\windows-exporter-textfile'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$finalFile = Join-Path $backupDir "$Database-$timestamp.dump"
$tempFile = "$finalFile.partial"
$manifestFile = "$finalFile.json"
$pgDump = Join-Path $PostgresBin 'pg_dump.exe'
$pgRestore = Join-Path $PostgresBin 'pg_restore.exe'

New-Item -ItemType Directory -Force -Path $backupDir, $metricsDir | Out-Null
if (-not (Test-Path $pgDump)) { throw "pg_dump.exe not found: $pgDump" }
if (-not (Test-Path $pgRestore)) { throw "pg_restore.exe not found: $pgRestore" }
if (-not (Test-Path $PgPassFile)) { throw "PGPASS file not found: $PgPassFile" }

$env:PGPASSFILE = $PgPassFile
$startedAt = Get-Date
try {
    & $pgDump --host=$HostName --port=$Port --username=$User --dbname=$Database `
        --format=custom --compress=6 --no-owner --no-acl --file=$tempFile
    if ($LASTEXITCODE -ne 0) { throw "pg_dump failed with exit code $LASTEXITCODE" }

    & $pgRestore --list $tempFile | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "pg_restore validation failed with exit code $LASTEXITCODE" }

    Move-Item -Force $tempFile $finalFile
    $hash = (Get-FileHash -Algorithm SHA256 -Path $finalFile).Hash.ToLowerInvariant()
    $item = Get-Item $finalFile
    $manifest = [ordered]@{
        type = 'postgresql-logical'
        database = $Database
        host = $HostName
        startedAt = $startedAt.ToUniversalTime().ToString('o')
        completedAt = (Get-Date).ToUniversalTime().ToString('o')
        file = $item.Name
        sizeBytes = $item.Length
        sha256 = $hash
        verifiedBy = 'pg_restore --list'
    }
    $manifest | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 $manifestFile

    $epoch = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    @(
        '# HELP mrr_backup_last_success_timestamp_seconds Last successful MRR PostgreSQL logical backup time.'
        '# TYPE mrr_backup_last_success_timestamp_seconds gauge'
        "mrr_backup_last_success_timestamp_seconds $epoch"
        '# HELP mrr_backup_last_size_bytes Size of the latest logical backup.'
        '# TYPE mrr_backup_last_size_bytes gauge'
        "mrr_backup_last_size_bytes $($item.Length)"
    ) | Set-Content -Encoding ASCII (Join-Path $metricsDir 'mrr-backup.prom')

    Get-ChildItem $backupDir -File | Where-Object {
        $_.LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays)
    } | Remove-Item -Force

    Write-Host "Backup completed and verified: $finalFile"
}
catch {
    Remove-Item -Force -ErrorAction SilentlyContinue $tempFile
    $epoch = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    @(
        '# HELP mrr_backup_last_failure_timestamp_seconds Last failed MRR PostgreSQL backup time.'
        '# TYPE mrr_backup_last_failure_timestamp_seconds gauge'
        "mrr_backup_last_failure_timestamp_seconds $epoch"
    ) | Set-Content -Encoding ASCII (Join-Path $metricsDir 'mrr-backup-failure.prom')
    throw
}
finally {
    Remove-Item Env:PGPASSFILE -ErrorAction SilentlyContinue
}
