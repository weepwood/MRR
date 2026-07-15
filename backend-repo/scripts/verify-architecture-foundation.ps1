[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot

Push-Location $backendRoot
try {
    Write-Host '==> Compile backend'
    & mvn -DskipTests compile
    if ($LASTEXITCODE -ne 0) {
        throw "Maven compile failed with exit code $LASTEXITCODE"
    }

    Write-Host '==> Run architecture foundation unit and H2 mapper tests'
    & mvn '-Dtest=ScanServiceImplTest,ScanControllerTest,ImageControllerTest,LocalImageStorageTest,ArchiveExportServiceImplTest,ScanMapperIntegrationTest' test
    if ($LASTEXITCODE -ne 0) {
        throw "Architecture foundation tests failed with exit code $LASTEXITCODE"
    }

    Write-Host '==> Run PostgreSQL mapper test when Docker is available'
    & mvn '-Dtest=ScanMapperPostgresqlIntegrationTest' test
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL mapper test failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Write-Host 'Architecture foundation verification completed.'
