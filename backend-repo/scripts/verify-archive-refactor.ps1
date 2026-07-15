[CmdletBinding()]
param(
    [string]$DatabaseUrl = $env:SPRING_DATASOURCE_URL,
    [string]$DatabaseUser = $env:SPRING_DATASOURCE_USERNAME
)

$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
$sqlFile = Join-Path $PSScriptRoot 'verify-archive-refactor.sql'

Push-Location $backendRoot
try {
    Write-Host '==> Compile backend'
    & mvn -DskipTests compile
    if ($LASTEXITCODE -ne 0) {
        throw "Maven compile failed with exit code $LASTEXITCODE"
    }

    Write-Host '==> Run archive refactor unit tests'
    & mvn '-Dtest=ArchiveRecordServiceImplTest' test
    if ($LASTEXITCODE -ne 0) {
        throw "Maven test failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

if ([string]::IsNullOrWhiteSpace($DatabaseUrl)) {
    Write-Warning 'SPRING_DATASOURCE_URL is empty; skipped PostgreSQL verification.'
    exit 0
}

$psql = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psql) {
    Write-Warning 'psql was not found; skipped PostgreSQL verification.'
    exit 0
}

$connection = $DatabaseUrl -replace '^jdbc:', ''
$args = @('--set', 'ON_ERROR_STOP=1', '--file', $sqlFile, '--dbname', $connection)
if (-not [string]::IsNullOrWhiteSpace($DatabaseUser)) {
    $args += @('--username', $DatabaseUser)
}

Write-Host '==> Verify migrated PostgreSQL schema'
& $psql.Source @args
if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL verification failed with exit code $LASTEXITCODE"
}

Write-Host 'Archive refactor verification completed.'
