#requires -Version 5.1
param([Parameter(Mandatory=$true)][string]$PostgresBin)
$ErrorActionPreference = 'Stop'
$path = Join-Path $PSScriptRoot 'test_windows_database_recovery.ps1'
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'Recovery rehearsal has syntax errors.' }
$statement = $ast.EndBlock.Statements | Where-Object { $_ -is [Management.Automation.Language.TryStatementAst] } | Select-Object -Last 1
$body = $statement.Finally.Extent.Text
$cleanup = [scriptblock]::Create($body.Substring(1, $body.Length - 2))
$originalPath = $env:PATH
$originalPgEnvironment = @{}
Get-ChildItem Env: | Where-Object Name -Like 'PG*' | ForEach-Object { $originalPgEnvironment[$_.Name] = $_.Value }
$temporaryParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
$temporary = Join-Path $temporaryParent ('mrr-recovery-test-' + [Guid]::NewGuid().ToString('N'))
$data = Join-Path $temporary 'never-created-cluster'
$clusterStarted = $true
$application = $null
$pgEnvironment = @{ PGHOST='expected-restored-value' }
$oldPath = $originalPath
try {
    New-Item -ItemType Directory -Path $temporary | Out-Null
    $env:PGHOST = 'synthetic-isolated-value'
    $rejected = $false
    try { & $cleanup }
    catch { if ($_.Exception.Message -notlike '*cleanup failed*') { throw }; $rejected = $true }
    if (-not $rejected) { throw 'Cleanup failure did not fail the rehearsal.' }
    if ($env:PGHOST -ne 'expected-restored-value' -or $env:PATH -ne $originalPath) { throw 'Environment was not restored after pg_ctl failure.' }
    if (-not (Test-Path -LiteralPath $temporary)) { throw 'Unconfirmed cluster directory was deleted.' }
    Write-Output 'Actual finally block: pg_ctl stderr failure propagated, environment restored, directory retained.'
}
finally {
    Get-ChildItem Env: | Where-Object Name -Like 'PG*' | ForEach-Object { Remove-Item -LiteralPath ('Env:' + $_.Name) }
    foreach ($key in $originalPgEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $originalPgEnvironment[$key], 'Process') }
    $env:PATH = $originalPath
    $resolved = [IO.Path]::GetFullPath($temporary)
    if ($resolved.StartsWith($temporaryParent + '\', [StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolved -Leaf) -match '^mrr-recovery-test-[a-f0-9]{32}$') {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
