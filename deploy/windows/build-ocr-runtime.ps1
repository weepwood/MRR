#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$PythonHome,
    [Parameter(Mandatory = $true)][string]$EnvironmentPython,
    [Parameter(Mandatory = $true)][string]$SitePackages,
    [Parameter(Mandatory = $true)][string]$Models,
    [Parameter(Mandatory = $true)][string]$Output,
    [Parameter(Mandatory = $true)][string]$Version
)
$ErrorActionPreference = 'Stop'
$builder = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\backend-repo\ocr-runtime\build_bundle.py'))
if (-not (Test-Path -LiteralPath $builder -PathType Leaf)) { throw 'Run this build-only command from the source checkout.' }
if (Test-Path -LiteralPath $Output) { throw 'Output already exists; use a new immutable release directory.' }
& $EnvironmentPython -X utf8 $builder --python-home $PythonHome --site-packages $SitePackages --models $Models --output $Output --version $Version --zip
if ($LASTEXITCODE -ne 0) { throw 'OCR offline bundle preparation failed.' }
