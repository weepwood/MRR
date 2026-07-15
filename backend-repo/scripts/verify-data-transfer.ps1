[CmdletBinding()]
param(
    [string]$DatabaseUrl = $env:SPRING_DATASOURCE_URL,
    [string]$DatabaseUser = $env:SPRING_DATASOURCE_USERNAME,
    [switch]$SkipFrontend
)

$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Split-Path -Parent $backendRoot
$frontendRoot = Join-Path $repositoryRoot 'frontend-fantastic-admin'
$sqlFile = Join-Path $PSScriptRoot 'verify-data-transfer.sql'

Push-Location $backendRoot
try {
    Write-Host '==> Compile backend'
    & mvn -DskipTests compile
    if ($LASTEXITCODE -ne 0) {
        throw "Maven compile failed with exit code $LASTEXITCODE"
    }

    Write-Host '==> Run data transfer unit tests'
    & mvn '-Dtest=DataTransferStorageServiceTest' test
    if ($LASTEXITCODE -ne 0) {
        throw "Maven test failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

if (-not $SkipFrontend) {
    $pnpm = Get-Command pnpm -ErrorAction SilentlyContinue
    if (-not $pnpm) {
        Write-Warning 'pnpm was not found; skipped frontend type checking.'
    }
    elseif (-not (Test-Path (Join-Path $frontendRoot 'node_modules'))) {
        Write-Warning 'frontend node_modules is missing; skipped frontend type checking.'
    }
    else {
        Push-Location $frontendRoot
        try {
            Write-Host '==> Type-check frontend'
            & $pnpm.Source lint:tsc
            if ($LASTEXITCODE -ne 0) {
                throw "Frontend type checking failed with exit code $LASTEXITCODE"
            }
        }
        finally {
            Pop-Location
        }
    }
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
$psqlArgs = @('--set', 'ON_ERROR_STOP=1', '--file', $sqlFile, '--dbname', $connection)
if (-not [string]::IsNullOrWhiteSpace($DatabaseUser)) {
    $psqlArgs += @('--username', $DatabaseUser)
}

Write-Host '==> Verify PostgreSQL data transfer schema'
& $psql.Source @psqlArgs
if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL verification failed with exit code $LASTEXITCODE"
}

Write-Host 'Data transfer center verification completed.'
