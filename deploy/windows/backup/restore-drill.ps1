[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$Root = 'C:\MRR',
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\16\bin',
    [string]$HostName = '127.0.0.1',
    [int]$Port = 5432,
    [string]$User = 'mrr_restore',
    [string]$PgPassFile = 'C:\MRR\secrets\pgpass.conf',
    [string]$RestoreDatabase = "imgapi_restore_$(Get-Date -Format 'yyyyMMddHHmmss')",
    [switch]$KeepDatabase
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path $BackupFile)) { throw "Backup file not found: $BackupFile" }
if (-not (Test-Path $PgPassFile)) { throw "PGPASS file not found: $PgPassFile" }

$createdb = Join-Path $PostgresBin 'createdb.exe'
$dropdb = Join-Path $PostgresBin 'dropdb.exe'
$pgRestore = Join-Path $PostgresBin 'pg_restore.exe'
$psql = Join-Path $PostgresBin 'psql.exe'
foreach ($command in @($createdb, $dropdb, $pgRestore, $psql)) {
    if (-not (Test-Path $command)) { throw "PostgreSQL tool not found: $command" }
}

$reportDir = Join-Path $Root 'backups\restore-drills'
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$reportFile = Join-Path $reportDir "restore-drill-$(Get-Date -Format 'yyyyMMdd-HHmmss').json"
$env:PGPASSFILE = $PgPassFile
$startedAt = Get-Date
$checks = [ordered]@{}
$created = $false

try {
    & $pgRestore --list $BackupFile | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Backup catalog validation failed' }
    $checks.backupCatalog = 'PASS'

    & $createdb --host=$HostName --port=$Port --username=$User --encoding=UTF8 $RestoreDatabase
    if ($LASTEXITCODE -ne 0) { throw "Unable to create restore database $RestoreDatabase" }
    $created = $true

    & $pgRestore --host=$HostName --port=$Port --username=$User --dbname=$RestoreDatabase `
        --no-owner --no-acl --exit-on-error --jobs=2 $BackupFile
    if ($LASTEXITCODE -ne 0) { throw "pg_restore failed with exit code $LASTEXITCODE" }
    $checks.restore = 'PASS'

    $validationSql = @'
SELECT json_build_object(
  'schema_exists', EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'app'),
  'mr_scan_rows', (SELECT COUNT(*) FROM app.mr_scan),
  'mr_statistics_rows', (SELECT COUNT(*) FROM app.mr_statistics),
  'access_log_rows', (SELECT COUNT(*) FROM app.access_log),
  'orphan_scan_statistics', (
      SELECT COUNT(*) FROM app.mr_scan s
      WHERE s.bah IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM app.mr_statistics st WHERE st.bah = s.bah)
  )
);
'@
    $validationOutput = $validationSql | & $psql --host=$HostName --port=$Port --username=$User `
        --dbname=$RestoreDatabase --tuples-only --no-align --set ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) { throw 'Post-restore validation query failed' }
    $checks.coreQueries = 'PASS'
    $validation = ($validationOutput | Out-String).Trim()

    $completedAt = Get-Date
    $report = [ordered]@{
        result = 'PASS'
        backupFile = (Resolve-Path $BackupFile).Path
        backupSha256 = (Get-FileHash -Algorithm SHA256 $BackupFile).Hash.ToLowerInvariant()
        restoreDatabase = $RestoreDatabase
        startedAt = $startedAt.ToUniversalTime().ToString('o')
        completedAt = $completedAt.ToUniversalTime().ToString('o')
        rtoSeconds = [math]::Round(($completedAt - $startedAt).TotalSeconds, 2)
        checks = $checks
        validation = $validation | ConvertFrom-Json
        databaseKept = [bool]$KeepDatabase
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $reportFile
    Write-Host "Restore drill passed. Report: $reportFile"
}
catch {
    $completedAt = Get-Date
    $report = [ordered]@{
        result = 'FAIL'
        backupFile = $BackupFile
        restoreDatabase = $RestoreDatabase
        startedAt = $startedAt.ToUniversalTime().ToString('o')
        completedAt = $completedAt.ToUniversalTime().ToString('o')
        rtoSeconds = [math]::Round(($completedAt - $startedAt).TotalSeconds, 2)
        checks = $checks
        error = $_.Exception.Message
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $reportFile
    throw
}
finally {
    if ($created -and -not $KeepDatabase) {
        & $dropdb --host=$HostName --port=$Port --username=$User --if-exists $RestoreDatabase | Out-Null
    }
    Remove-Item Env:PGPASSFILE -ErrorAction SilentlyContinue
}
