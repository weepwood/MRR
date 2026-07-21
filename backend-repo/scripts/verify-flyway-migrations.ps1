[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$migrationRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'src/main/resources/db/migration'
$pattern = '^V(?<version>\d{14})__(?<description>[a-z0-9]+(?:_[a-z0-9]+)*)\.sql$'

if (-not (Test-Path $migrationRoot -PathType Container)) {
    throw "Flyway migration directory not found: $migrationRoot"
}

$files = @(Get-ChildItem -Path $migrationRoot -File -Filter 'V*.sql' | Sort-Object Name)
if ($files.Count -eq 0) {
    throw "No Flyway versioned migrations found in $migrationRoot"
}

$versions = @{}
foreach ($file in $files) {
    $match = [regex]::Match($file.Name, $pattern)
    if (-not $match.Success) {
        throw "Invalid Flyway migration name '$($file.Name)'. Expected VyyyyMMddHHmmss__description.sql"
    }

    $version = $match.Groups['version'].Value
    try {
        [void][datetime]::ParseExact(
            $version,
            'yyyyMMddHHmmss',
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::None
        )
    }
    catch {
        throw "Invalid Flyway migration timestamp '$version' in '$($file.Name)'"
    }

    if ($versions.ContainsKey($version)) {
        throw "Duplicate Flyway migration timestamp '$version': '$($versions[$version])' and '$($file.Name)'"
    }
    $versions[$version] = $file.Name
}

Write-Host "Flyway migration naming verified: $($files.Count) files."
