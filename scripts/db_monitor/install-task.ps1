<#
.SYNOPSIS
    注册 / 卸载 MRR 轻量数据库监控的计划任务。

.DESCRIPTION
    做的事只有三件：
      1. 必要时创建 Windows 事件日志源（告警写入 Application 日志）；
      2. 把连接串写入断开继承、仅 SYSTEM 与 Administrators 可读的文件（默认
         C:\ProgramData\MRR\db-monitor\db-dsn.txt，由 config.json 的 dsn_file 指定）；
      3. 注册每 N 分钟运行一次的 Scheduled Task。

    需要以管理员身份运行。卸载不会删除历史数据与看板。

.PARAMETER Dsn
    PostgreSQL 连接串。只写入受限 ACL 的连接串文件，不写进仓库文件、不写机器级环境变量
    （机器级环境变量存放在 HKLM，本机任何登录用户都能读到）。
    留空则沿用已存在的连接串文件。

.PARAMETER ConfigPath
    collect.ps1 使用的配置文件，默认同目录 config.json。

.PARAMETER EveryMinutes
    采集间隔，默认 5 分钟。

.PARAMETER TaskName
    任务名，默认 MRR-DB-Monitor。

.PARAMETER Uninstall
    删除计划任务（保留数据目录与事件日志源）。

.EXAMPLE
    .\install-task.ps1 -Dsn "postgresql://mrr_monitor:口令@127.0.0.1:5432/<库名>"

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

# 断开 ACL 继承，只保留 SYSTEM 与 Administrators：连接串文件不能沿用父目录给 Users 的读取权限。
function Set-SecretAcl {
    param([string]$Path, [switch]$Directory)
    $system = New-Object System.Security.Principal.SecurityIdentifier('S-1-5-18')
    $admins = New-Object System.Security.Principal.SecurityIdentifier('S-1-5-32-544')
    if ($Directory) {
        $acl = New-Object System.Security.AccessControl.DirectorySecurity
        $acl.SetAccessRuleProtection($true, $false)
        $inherit = [System.Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit'
        $propagation = [System.Security.AccessControl.PropagationFlags]::None
        foreach ($sid in @($system, $admins)) {
            $acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', $inherit, $propagation, 'Allow')))
        }
    }
    else {
        $acl = New-Object System.Security.AccessControl.FileSecurity
        $acl.SetAccessRuleProtection($true, $false)
        foreach ($sid in @($system, $admins)) {
            $acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'Allow')))
        }
    }
    Set-Acl -LiteralPath $Path -AclObject $acl
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

# 2. 连接串写入受限文件（默认 C:\ProgramData\MRR\db-monitor\db-dsn.txt）
$dsnFile = [string]$cfg.dsn_file
if (-not $dsnFile) { $dsnFile = 'C:\ProgramData\MRR\db-monitor\db-dsn.txt' }
if (-not [System.IO.Path]::IsPathRooted($dsnFile)) { $dsnFile = Join-Path $PSScriptRoot $dsnFile }
$envName = [string]$cfg.dsn_env
if (-not $envName) { $envName = 'MRR_DB_DSN' }

if ($Dsn) {
    $dsnDir = Split-Path -Parent $dsnFile
    $createdDir = $false
    if (-not (Test-Path -LiteralPath $dsnDir)) {
        New-Item -ItemType Directory -Path $dsnDir -Force | Out-Null
        $createdDir = $true
    }
    # 只收紧自己新建的目录：dsn_file 若被指到既有共享目录，改它的 ACL 会连带影响别的程序。
    if ($createdDir) { Set-SecretAcl -Path $dsnDir -Directory }
    [System.IO.File]::WriteAllText($dsnFile, $Dsn.Trim(), (New-Object System.Text.UTF8Encoding($false)))
    Set-SecretAcl -Path $dsnFile
    Write-Host "已写入连接串文件 $dsnFile（断开继承，仅 SYSTEM 与 Administrators 可读）" -ForegroundColor Green
}
elseif (Test-Path -LiteralPath $dsnFile) {
    Write-Host "沿用已有连接串文件：$dsnFile"
}
else {
    Write-Host "警告：找不到连接串文件 $dsnFile，任务运行时会因缺少连接串而退出（退出码 3）。" -ForegroundColor Yellow
    Write-Host "      请以管理员重跑：.\install-task.ps1 -Dsn `"postgresql://mrr_monitor:口令@127.0.0.1:5432/<库名>`"" -ForegroundColor Yellow
}

# 早期版本把连接串写在机器级环境变量里。它存放在 HKLM，本机任何登录用户都能读到，这里只提示不自动删除。
$legacy = [System.Environment]::GetEnvironmentVariable($envName, 'Machine')
if ($legacy) {
    if (Test-Path -LiteralPath $dsnFile) {
        Write-Host "提示：检测到遗留的机器级环境变量 $envName，连接串文件优先且已生效，可用" -ForegroundColor Yellow
        Write-Host "      [Environment]::SetEnvironmentVariable('$envName', `$null, 'Machine') 清除它。" -ForegroundColor Yellow
    }
    else {
        Write-Host "提示：当前仍依赖机器级环境变量 $envName。它存放在 HKLM，本机任何登录用户都能读到；" -ForegroundColor Yellow
        Write-Host "      建议用 -Dsn 重跑，把连接串迁到受限文件。" -ForegroundColor Yellow
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
