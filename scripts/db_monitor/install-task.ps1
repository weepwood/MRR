<#
.SYNOPSIS
    注册 / 卸载 MRR 轻量数据库监控的计划任务。

.DESCRIPTION
    做的事只有三件：
      1. 必要时创建 Windows 事件日志源（告警写入 Application 日志）；
      2. 把连接串写入机器级环境变量（SYSTEM 账户能读到，用户级变量读不到）；
      3. 注册每 N 分钟运行一次的 Scheduled Task。

    需要以管理员身份运行。卸载不会删除历史数据与看板。

.PARAMETER Dsn
    PostgreSQL 连接串。只写入机器级环境变量，不写进仓库文件。
    留空则沿用已存在的机器级 MRR_DB_DSN。

.PARAMETER ConfigPath
    collect.ps1 使用的配置文件，默认同目录 config.json。

.PARAMETER EveryMinutes
    采集间隔，默认 5 分钟。

.PARAMETER TaskName
    任务名，默认 MRR-DB-Monitor。

.PARAMETER Uninstall
    删除计划任务（保留数据目录与事件日志源）。

.EXAMPLE
    .\install-task.ps1 -Dsn "postgresql://mrr_monitor:口令@127.0.0.1:5432/imgapi"

.EXAMPLE
    .\install-task.ps1 -Uninstall
#>
[CmdletBinding()]
param(
    [string]$Dsn = '',
    [string]$ConfigPath = (Join-Path $PSScriptRoot 'config.json'),
    [int]$EveryMinutes = 5,
    [string]$TaskName = 'MRR-DB-Monitor',
    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'

function Assert-Admin {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw '需要管理员权限：请右键“以管理员身份运行” PowerShell 后重试。'
    }
}

Assert-Admin

if ($Uninstall) {
    $existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if ($existing) {
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
        Write-Host "已删除计划任务 $TaskName（历史数据与看板保留）" -ForegroundColor Yellow
    }
    else {
        Write-Host "未找到计划任务 $TaskName" -ForegroundColor Yellow
    }
    exit 0
}

$collector = Join-Path $PSScriptRoot 'collect.ps1'
if (-not (Test-Path $collector)) { throw "找不到采集脚本：$collector" }
if (-not (Test-Path $ConfigPath)) { throw "找不到配置文件：$ConfigPath（可先复制 config.json）" }

# 1. 事件日志源
$source = 'MRR-DB-Monitor'
$cfg = Get-Content -Path $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($cfg.alert -and $cfg.alert.event_source) { $source = $cfg.alert.event_source }
if (-not [System.Diagnostics.EventLog]::SourceExists($source)) {
    New-EventLog -LogName Application -Source $source
    Write-Host "已创建事件日志源：$source（Application 日志）" -ForegroundColor Green
}
else {
    Write-Host "事件日志源已存在：$source"
}

# 2. 连接串写入机器级环境变量
$envName = 'MRR_DB_DSN'
if ($cfg.dsn_env) { $envName = $cfg.dsn_env }
if ($Dsn) {
    [System.Environment]::SetEnvironmentVariable($envName, $Dsn, 'Machine')
    Write-Host "已写入机器级环境变量 $envName（仅本机管理员可见）" -ForegroundColor Green
}
else {
    $current = [System.Environment]::GetEnvironmentVariable($envName, 'Machine')
    if (-not $current) {
        Write-Host "警告：机器级 $envName 为空，任务运行时会因缺少连接串而退出（退出码 3）。" -ForegroundColor Yellow
        Write-Host "      可用 -Dsn 参数重跑，或手动执行：" -ForegroundColor Yellow
        Write-Host "      setx /M $envName `"postgresql://mrr_monitor:口令@127.0.0.1:5432/imgapi`"" -ForegroundColor DarkGray
    }
}

# 3. 计划任务
$arguments = '-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "{0}" -ConfigPath "{1}"' -f $collector, $ConfigPath
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments -WorkingDirectory $PSScriptRoot

$trigger = New-ScheduledTaskTrigger -Once -At ((Get-Date).AddMinutes(1)) `
    -RepetitionInterval (New-TimeSpan -Minutes $EveryMinutes) `
    -RepetitionDuration ([TimeSpan]::MaxValue)

$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest

$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 10)

$task = New-ScheduledTask -Action $action -Trigger $trigger -Principal $principal -Settings $settings
Register-ScheduledTask -TaskName $TaskName -Task $task -Description 'MRR 数据库轻量监控：采集指标、生成离线看板、异常写事件日志' -Force | Out-Null

Write-Host ""
Write-Host "已注册计划任务：$TaskName（每 $EveryMinutes 分钟，SYSTEM 账户）" -ForegroundColor Green
Write-Host "看板文件：$(Join-Path $cfg.out_dir 'index.html')"
Write-Host "验证命令：" -ForegroundColor Cyan
Write-Host "  Start-ScheduledTask -TaskName $TaskName" -ForegroundColor DarkGray
Write-Host "  Get-ScheduledTaskInfo -TaskName $TaskName" -ForegroundColor DarkGray
Write-Host ""
Write-Host "退出码含义：0 正常 / 1 预警 / 2 严重 / 3 采集失败（任务历史里的“上次运行结果”即可看出）" -ForegroundColor Cyan
Write-Host "首次注册后如需立即出图，可手动执行一次：" -ForegroundColor Cyan
Write-Host "  powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$collector`" -NoAlert" -ForegroundColor DarkGray
