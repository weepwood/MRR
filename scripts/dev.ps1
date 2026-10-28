param(
    [ValidateSet('doctor', 'up', 'status', 'down')][string]$Command = 'doctor',
    [string]$PythonExe = 'python',
    [int]$BackendPort = 0,
    [int]$WebPort = 9200,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$arguments = @('-m', 'scripts.dev', $Command, '--backend-port', "$BackendPort", '--web-port', "$WebPort")
if ($SkipBuild) { $arguments += '--skip-build' }
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    & $PythonExe @arguments
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
