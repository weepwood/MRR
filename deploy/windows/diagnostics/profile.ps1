[CmdletBinding()]
param(
    [ValidateSet('start','stop','status')]
    [string]$Action = 'start',
    [string]$Root = 'C:\MRR',
    [ValidateRange(1, 60)]
    [int]$DurationMinutes = 5
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$process = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq 'java.exe' -and $_.CommandLine -match 'mrr-backend\.jar'
} | Select-Object -First 1
if (-not $process) { throw '未找到正在运行的 MRR Java 进程。' }

$jcmd = Join-Path (Split-Path $process.ExecutablePath -Parent) 'jcmd.exe'
if (-not (Test-Path -LiteralPath $jcmd -PathType Leaf)) {
    $command = Get-Command jcmd.exe -ErrorAction SilentlyContinue
    if (-not $command) { throw '未找到 jcmd.exe，请确认服务器使用完整 JDK 21。' }
    $jcmd = $command.Source
}

$diagnosticDir = Join-Path $Root 'logs\diagnostics'
New-Item -ItemType Directory -Path $diagnosticDir -Force | Out-Null
$recordingName = 'MRR-OnDemand'

switch ($Action) {
    'start' {
        $file = Join-Path $diagnosticDir "mrr-profile-$(Get-Date -Format 'yyyyMMdd-HHmmss').jfr"
        & $jcmd $process.ProcessId JFR.start "name=$recordingName" settings=profile "duration=${DurationMinutes}m" "filename=$file" dumponexit=true
        if ($LASTEXITCODE -ne 0) { throw '启动 JFR 录制失败。' }
        Write-Host "已开始录制 $DurationMinutes 分钟，完成后保存到：$file" -ForegroundColor Green
    }
    'stop' {
        $file = Join-Path $diagnosticDir "mrr-profile-manual-$(Get-Date -Format 'yyyyMMdd-HHmmss').jfr"
        & $jcmd $process.ProcessId JFR.stop "name=$recordingName" "filename=$file"
        if ($LASTEXITCODE -ne 0) { throw '停止 JFR 录制失败，可能没有活动录制。' }
        Write-Host "录制已停止并保存到：$file" -ForegroundColor Green
    }
    'status' {
        & $jcmd $process.ProcessId JFR.check
        if ($LASTEXITCODE -ne 0) { throw '读取 JFR 状态失败。' }
    }
}
