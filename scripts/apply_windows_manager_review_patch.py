from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
manager_path = ROOT / 'deploy/windows/mrr-manager.ps1'
manager = manager_path.read_text(encoding='utf-8')

old = '''    $command = @"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
try {
    & $scriptLiteral @($literalArguments)
    if (`$LASTEXITCODE -and `$LASTEXITCODE -ne 0) { exit `$LASTEXITCODE }
    exit 0
}
catch {
    Write-Error (`$_ | Out-String)
    exit 1
}
"@
'''
new = '''    $command = @"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
`$invokeArguments = @($literalArguments)
try {
    & $scriptLiteral @invokeArguments
    if (-not `$?) { exit 1 }
    exit 0
}
catch {
    Write-Error (`$_ | Out-String)
    exit 1
}
"@
'''

if new not in manager:
    if old not in manager:
        raise SystemExit('Expected background invocation block not found')
    manager = manager.replace(old, new, 1)

manager_path.write_text(manager, encoding='utf-8', newline='\n')
Path(__file__).unlink()
