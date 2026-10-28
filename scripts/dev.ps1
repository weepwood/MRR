param(
    [ValidateSet('doctor', 'up', 'status', 'down', 'frontend')][string]$Command = 'doctor',
    [string]$PythonExe = 'python',
    [int]$BackendPort = 0,
    [int]$WebPort = 9200,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'

if ($Command -eq 'frontend') {
    Push-Location (Join-Path $PSScriptRoot '..\frontend-fantastic-admin')
    try {
        & corepack pnpm dev:mock
        exit $LASTEXITCODE
    } finally {
        Pop-Location
    }
}

$arguments = @('-m', 'scripts.dev', $Command, '--backend-port', "$BackendPort", '--web-port', "$WebPort")
if ($SkipBuild) { $arguments += '--skip-build' }
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    & $PythonExe @arguments
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
