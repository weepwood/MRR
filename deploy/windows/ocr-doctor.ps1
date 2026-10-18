#requires -Version 5.1
[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$Bundle)
$ErrorActionPreference = 'Stop'
$resolved = (Resolve-Path -LiteralPath $Bundle).ProviderPath
$python = Join-Path $resolved 'python\python.exe'
$doctor = Join-Path $resolved 'doctor.py'
if (-not (Test-Path -LiteralPath $python -PathType Leaf) -or -not (Test-Path -LiteralPath $doctor -PathType Leaf)) { throw 'Incomplete OCR runtime bundle.' }
& $python -X utf8 -I $doctor --bundle $resolved --smoke
if ($LASTEXITCODE -ne 0) { throw 'OCR runtime verification failed; leave OCR disabled.' }
