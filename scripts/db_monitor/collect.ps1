<#
.SYNOPSIS
    MRR 轻量数据库监控采集器（不依赖 Prometheus / Grafana）。

.DESCRIPTION
    用 psql 读取 PostgreSQL 统计视图，结合 Actuator 的 Hikari 指标与主机磁盘水位，
    判定阈值后写入：
      history.jsonl   单行 JSON 历史，供趋势与前后对比
      alerts.jsonl    状态迁移记录（只记变化，不刷屏）
      status.json     当前状态，供外部程序或 Nginx 读取
      index.html      离线可看的看板（单文件，无外部依赖）
    告警通道为 Windows 事件日志与可选的企业微信机器人，二者都不需要额外服务。

    退出码：0 正常 / 1 预警 / 2 严重 / 3 采集失败（计划任务可直接用退出码判结果）。

.PARAMETER ConfigPath
    配置文件路径，默认 scripts\db_monitor\config.json。

.PARAMETER OutputDir
    覆盖配置中的输出目录，便于临时演练。

.PARAMETER NoAlert
    只采集与生成看板，不发送任何告警（用于首次试跑）。

.EXAMPLE
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File collect.ps1 -NoAlert
#>
[CmdletBinding()]
param(
    [string]$ConfigPath = (Join-Path $PSScriptRoot 'config.json'),
    [string]$OutputDir = '',
    [switch]$NoAlert
)

$ErrorActionPreference = 'Stop'
$Inv = [System.Globalization.CultureInfo]::InvariantCulture
$script:Quiet = $false
$script:LogPath = $null

function Write-Log {
    param([string]$Message, [string]$Level = 'INFO')
    $line = '{0} [{1}] {2}' -f (Get-Date).ToString('yyyy-MM-dd HH:mm:ss'), $Level, $Message
    if (-not $script:Quiet) { Write-Host $line }
    if ($script:LogPath) { Add-Content -Path $script:LogPath -Value $line -Encoding UTF8 }
}

# JSON 一律写 UTF-8 无 BOM，避免被 Python / jq / 其他程序读时带出 BOM 报错。
function Write-JsonFile {
    param([string]$Path, [string]$Content)
    [System.IO.File]::WriteAllText($Path, $Content, (New-Object System.Text.UTF8Encoding($false)))
}

function Add-JsonLine {
    param([string]$Path, [string]$Content)
    $text = $Content + [System.Environment]::NewLine
    [System.IO.File]::AppendAllText($Path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

function Format-Bytes {
    param([double]$Bytes)
    if ($Bytes -ge 1GB) { return ('{0:N2} GB' -f ($Bytes / 1GB)) }
    if ($Bytes -ge 1MB) { return ('{0:N1} MB' -f ($Bytes / 1MB)) }
    if ($Bytes -ge 1KB) { return ('{0:N0} KB' -f ($Bytes / 1KB)) }
    return ('{0:N0} B' -f $Bytes)
}

function Format-Metric {
    param([string]$Name, [double]$Value)
    if ($Name -like '*_bytes*') { return (Format-Bytes -Bytes $Value) }
    if ($Name -eq 'cache_hit_pct') { return ('{0:N3} %' -f $Value) }
    if ($Name -like '*_pct') { return ('{0:N1} %' -f $Value) }
    if ($Name -like '*_ratio') { return ('{0:P1}' -f $Value) }
    if ($Name -like '*_ms') { return ('{0:N0} ms' -f $Value) }
    if ($Name -like '*_seconds') { return ('{0:N0} s' -f $Value) }
    if ($Name -like '*_mb') { return ('{0:N1} MB' -f $Value) }
    if ($Value -eq [math]::Floor($Value)) { return ('{0:N0}' -f $Value) }
    return ('{0:N2}' -f $Value)
}

$MetricTitles = @{
    connections_total      = '连接总数'
    connections_active     = '活跃连接'
    connections_idle_in_tx = '空闲事务'
    connections_waiting    = '锁等待会话'
    connections_pct        = '连接使用率'
    max_connections        = '连接上限'
    database_size_bytes    = '数据库大小'
    cache_hit_pct          = '缓存命中率'
    temp_bytes_total       = '临时文件累计'
    temp_bytes_delta       = '临时文件增量'
    deadlocks_total        = '死锁累计'
    deadlocks_delta        = '死锁增量'
    xact_commit_total      = '提交事务累计'
    xact_rollback_total    = '回滚事务累计'
    longest_xact_seconds   = '最长事务时长'
    xacts_over_60s         = '超 60 秒事务'
    dead_tuple_ratio       = '死元组占比'
    tables_needing_vacuum  = '待清理表数'
    biggest_table_bytes    = '最大表体积'
    unused_index_count     = '未使用索引数'
    bloat_max_ratio        = '最高膨胀率'
    bloat_max_bytes        = '最高膨胀体积'
    server_uptime_seconds  = '实例运行时长'
    disk_free_pct          = '数据盘可用'
    disk_free_bytes        = '数据盘剩余'
    hikari_pending         = 'Hikari 等待线程'
    hikari_active          = 'Hikari 活跃连接'
    stmt_avg_latency_ms    = '区间平均延迟'
    stmt_slow_1s           = '慢语句 (>1s)'
    stmt_slow_200ms        = '慢语句 (>200ms)'
    stmt_worst_mean_ms     = '最慢语句均值'
    stmt_temp_mb           = '语句临时文件'
    stmt_calls_total       = '语句累计执行'
    stmt_exec_ms_total     = '语句累计耗时'
    stmt_rows_total        = '语句累计返回行数'
}

function Get-MetricTitle {
    param([string]$Name)
    if ($MetricTitles.ContainsKey($Name)) { return $MetricTitles[$Name] }
    return $Name
}

# ---------------------------------------------------------------- 配置与依赖

if (-not (Test-Path $ConfigPath)) {
    Write-Host "找不到配置文件：$ConfigPath" -ForegroundColor Red
    exit 3
}
$cfg = Get-Content -Path $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json

$outDir = $cfg.out_dir
if ($OutputDir) { $outDir = $OutputDir }
$outDir = [System.IO.Path]::GetFullPath($outDir)
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$script:LogPath = Join-Path $outDir 'collect.log'

$dsn = $cfg.dsn
$dsnFile = ''
if ($cfg.dsn_file) {
    $dsnFile = [string]$cfg.dsn_file
    if (-not [System.IO.Path]::IsPathRooted($dsnFile)) {
        $dsnFile = Join-Path (Split-Path -Parent (Resolve-Path -LiteralPath $ConfigPath)) $dsnFile
    }
}
if (-not $dsn -and $dsnFile -and (Test-Path -LiteralPath $dsnFile)) {
    $dsn = ([System.IO.File]::ReadAllText($dsnFile)).Trim()
    if (-not $dsn) {
        Write-Log "连接串文件为空：$dsnFile" 'ERROR'
        exit 3
    }
}
if (-not $dsn) {
    $envName = $cfg.dsn_env
    if (-not $envName) { $envName = 'MRR_DB_DSN' }
    $dsn = [System.Environment]::GetEnvironmentVariable($envName)
}
if (-not $dsn) {
    if ($dsnFile) {
        Write-Log "未配置连接串：请运行 install-task.ps1 -Dsn 写入 $dsnFile，或设置环境变量 $envName" 'ERROR'
    }
    else {
        Write-Log "未配置连接串：请设置环境变量 $envName，或在 config.json 中配置 dsn_file" 'ERROR'
    }
    exit 3
}

$psql = $cfg.psql
if (-not $psql) {
    $cmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($cmd) { $psql = $cmd.Source }
}
if (-not $psql) {
    foreach ($candidate in @(
            'C:\Program Files\PostgreSQL\16\bin\psql.exe',
            'C:\Program Files\PostgreSQL\17\bin\psql.exe',
            'C:\Program Files\PostgreSQL\15\bin\psql.exe')) {
        if (Test-Path $candidate) { $psql = $candidate; break }
    }
}
if (-not $psql) {
    Write-Log '找不到 psql，请在 config.json 中填写 psql 的完整路径' 'ERROR'
    exit 3
}

$env:PGCLIENTENCODING = 'UTF8'
if (-not $env:PGCONNECT_TIMEOUT) { $env:PGCONNECT_TIMEOUT = '5' }

# Windows PowerShell 5.1 默认按控制台代码页解码原生命令输出，会让 UTF-8 的中文变成乱码；
# 同时 ErrorActionPreference=Stop 会把原生命令写到 stderr 的内容当成终止性错误。
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

# ---------------------------------------------------------------- 采集

function Invoke-MetricSql {
    param([string]$SqlFile)
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $raw = & $psql "--dbname=$dsn" -X -q -t -A -F '|' -v ON_ERROR_STOP=1 -f $SqlFile 2>&1
        $code = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $prevEap
    }
    $texts = @()
    foreach ($item in $raw) { $texts += [string]$item }
    if ($code -ne 0) {
        return [pscustomobject]@{ Ok = $false; Lines = @(); Error = ($texts -join ' ') }
    }
    $lines = @()
    foreach ($text in $texts) {
        if ($text -match '^[A-Za-z0-9_]+\|-?[0-9.]+\|') { $lines += $text }
    }
    return [pscustomobject]@{ Ok = $true; Lines = $lines; Error = '' }
}

$metrics = @{}
$units = @{}
$coreFile = Join-Path $PSScriptRoot 'metrics-core.sql'
$core = Invoke-MetricSql -SqlFile $coreFile
if (-not $core.Ok) {
    Write-Log ("基础指标采集失败：{0}" -f $core.Error) 'ERROR'
    exit 3
}
foreach ($line in $core.Lines) {
    if ($line -match '^([a-zA-Z0-9_]+)\|(-?[0-9.]+)\|(.*)$') {
        $metrics[$Matches[1]] = [double]::Parse($Matches[2], $Inv)
        $units[$Matches[1]] = $Matches[3]
    }
}
if ($metrics.Count -lt 10) {
    Write-Log ("基础指标数量异常（{0} 项），疑似 SQL 与数据库版本不匹配" -f $metrics.Count) 'ERROR'
    exit 3
}

$statementsAvailable = $false
$statementsError = ''
$stmtFile = Join-Path $PSScriptRoot 'metrics-statements.sql'
if (Test-Path $stmtFile) {
    $stmt = Invoke-MetricSql -SqlFile $stmtFile
    if ($stmt.Ok) {
        $statementsAvailable = $true
        foreach ($line in $stmt.Lines) {
            if ($line -match '^([a-zA-Z0-9_]+)\|(-?[0-9.]+)\|(.*)$') {
                $metrics[$Matches[1]] = [double]::Parse($Matches[2], $Inv)
                $units[$Matches[1]] = $Matches[3]
            }
        }
    }
    else {
        $statementsError = $stmt.Error
        Write-Log '查询级指标不可用（pg_stat_statements 未预加载或扩展缺失），本次跳过语句类阈值' 'WARN'
    }
}

if ($cfg.actuator_url) {
    try {
        $resp = Invoke-WebRequest -Uri $cfg.actuator_url -UseBasicParsing -TimeoutSec 5
        $text = [string]$resp.Content
        foreach ($name in @('hikaricp_connections_pending', 'hikaricp_connections_active', 'hikaricp_connections_max')) {
            $pattern = '(?m)^' + [regex]::Escape($name) + '\{[^}]*\}\s+([0-9.eE+\-]+)\s*$'
            $sum = 0.0
            $found = $false
            foreach ($m in [regex]::Matches($text, $pattern)) {
                $sum += [double]::Parse($m.Groups[1].Value, $Inv)
                $found = $true
            }
            if ($found) {
                $short = $name -replace '^hikaricp_connections_', 'hikari_'
                $metrics[$short] = $sum
            }
        }
        if ($metrics.ContainsKey('hikari_active') -and $metrics.ContainsKey('hikari_max')) {
            $script:HikariMax = $metrics['hikari_max']
        }
    }
    catch {
        Write-Log 'Actuator 不可用，跳过 Hikari 指标（数据库监控不受影响）' 'WARN'
    }
}

try {
    $drive = [System.IO.Path]::GetPathRoot($outDir)
    $disk = Get-CimInstance Win32_LogicalDisk -Filter ("DeviceID='" + $drive.TrimEnd('\') + "'")
    if ($disk -and $disk.Size -gt 0) {
        $metrics['disk_free_bytes'] = [double]$disk.FreeSpace
        $metrics['disk_free_pct'] = [math]::Round(100.0 * $disk.FreeSpace / $disk.Size, 2)
    }
}
catch {
    Write-Log '读取磁盘容量失败，跳过磁盘阈值' 'WARN'
}

# ---------------------------------------------------------------- 增量与派生

$historyPath = Join-Path $outDir 'history.jsonl'
$prev = $null
if (Test-Path $historyPath) {
    $tail = Get-Content -Path $historyPath -Tail 1 -Encoding UTF8
    if ($tail) {
        try { $prev = $tail | ConvertFrom-Json } catch { $prev = $null }
    }
}

if ($prev -and $prev.metrics) {
    foreach ($pair in @(@('deadlocks_total', 'deadlocks_delta'), @('temp_bytes_total', 'temp_bytes_delta'))) {
        $cur = $pair[0]; $dst = $pair[1]
        if ($metrics.ContainsKey($cur) -and $prev.metrics.$cur -ne $null) {
            $delta = $metrics[$cur] - [double]$prev.metrics.$cur
            if ($delta -lt 0) { $delta = 0 }   # 统计被重置或实例重启，按 0 处理
            $metrics[$dst] = $delta
        }
    }
    if ($statementsAvailable -and $prev.metrics.stmt_calls_total -ne $null -and $prev.metrics.stmt_exec_ms_total -ne $null) {
        $dcalls = $metrics['stmt_calls_total'] - [double]$prev.metrics.stmt_calls_total
        $dms = $metrics['stmt_exec_ms_total'] - [double]$prev.metrics.stmt_exec_ms_total
        if ($dcalls -gt 0 -and $dms -ge 0) {
            $metrics['stmt_avg_latency_ms'] = [math]::Round($dms / $dcalls, 2)
        }
    }
}

# ---------------------------------------------------------------- 阈值判定

$levels = @{}
$violations = @()
foreach ($prop in $cfg.thresholds.PSObject.Properties) {
    $name = $prop.Name
    $th = $prop.Value
    if (-not $metrics.ContainsKey($name)) { continue }
    $value = $metrics[$name]
    $level = 'ok'
    if ($th.direction -eq 'below') {
        if ($value -le [double]$th.critical) { $level = 'critical' }
        elseif ($value -le [double]$th.warning) { $level = 'warning' }
    }
    else {
        if ($value -ge [double]$th.critical) { $level = 'critical' }
        elseif ($value -ge [double]$th.warning) { $level = 'warning' }
    }
    if ($level -ne 'ok' -and $th.min_bytes_metric) {
        $gate = 0.0
        if ($metrics.ContainsKey($th.min_bytes_metric)) { $gate = $metrics[$th.min_bytes_metric] }
        if ($gate -lt [double]$th.min_bytes) { $level = 'ok' }
    }
    $levels[$name] = $level
    if ($level -ne 'ok') {
        $limit = [double]$th.warning
        if ($level -eq 'critical') { $limit = [double]$th.critical }
        $violations += [pscustomobject]@{
            metric = $name
            title  = (Get-MetricTitle $name)
            level  = $level
            value  = $value
            limit  = $limit
        }
    }
}

$overall = 'ok'
foreach ($v in $violations) {
    if ($v.level -eq 'critical') { $overall = 'critical'; break }
    $overall = 'warning'
}
if (-not $statementsAvailable -and $overall -eq 'ok') { $overall = 'degraded' }

# ---------------------------------------------------------------- 历史与状态

$now = Get-Date
$record = [ordered]@{
    ts                    = $now.ToString('o')
    overall               = $overall
    statements_available  = $statementsAvailable
    metrics               = $metrics
    violations            = $violations
}
$json = $record | ConvertTo-Json -Depth 6 -Compress
Add-JsonLine -Path $historyPath -Content $json

$statusPath = Join-Path $outDir 'status.json'
$status = [ordered]@{
    updated_at           = $now.ToString('o')
    overall              = $overall
    statements_available = $statementsAvailable
    violations           = $violations
    metrics              = $metrics
}
Write-JsonFile -Path $statusPath -Content ($status | ConvertTo-Json -Depth 6)

$statePath = Join-Path $outDir 'state.json'
$state = $null
if (Test-Path $statePath) {
    try { $state = Get-Content -Path $statePath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { $state = $null }
}
$prevLevels = @{}
$lastNotified = @{}
if ($state) {
    if ($state.levels) { foreach ($p in $state.levels.PSObject.Properties) { $prevLevels[$p.Name] = [string]$p.Value } }
    if ($state.last_notified) { foreach ($p in $state.last_notified.PSObject.Properties) { $lastNotified[$p.Name] = [string]$p.Value } }
}

# ---------------------------------------------------------------- 告警

$alertCfg = $cfg.alert
$repeatMinutes = 120
if ($alertCfg -and $alertCfg.repeat_after_minutes) { $repeatMinutes = [int]$alertCfg.repeat_after_minutes }

$changed = @()
foreach ($v in $violations) {
    $before = 'ok'
    if ($prevLevels.ContainsKey($v.metric)) { $before = $prevLevels[$v.metric] }
    $stamp = $null
    if ($lastNotified.ContainsKey($v.metric)) { $stamp = $lastNotified[$v.metric] }
    $due = $true
    if ($stamp) {
        try {
            $age = ($now - [datetime]::Parse($stamp)).TotalMinutes
            if ($age -lt $repeatMinutes) { $due = $false }
        }
        catch { $due = $true }
    }
    if ($before -ne $v.level -or $due) {
        $v | Add-Member -NotePropertyName previous -NotePropertyValue $before
        $changed += $v
        $lastNotified[$v.metric] = $now.ToString('o')
    }
}

$recovered = @()
if ($alertCfg -and $alertCfg.notify_recovery) {
    foreach ($key in $prevLevels.Keys) {
        if ($prevLevels[$key] -ne 'ok' -and -not $levels.ContainsKey($key)) { continue }
        if ($prevLevels[$key] -ne 'ok' -and $levels[$key] -eq 'ok') {
            $recovered += $key
            $lastNotified.Remove($key)
        }
    }
}

if ($NoAlert) {
    $changed = @()
    $recovered = @()
}

$alertsLog = Join-Path $outDir 'alerts.jsonl'
if ($changed.Count -gt 0 -or $recovered.Count -gt 0) {
    $lines = @()
    foreach ($v in $changed) {
        $lines += ('{0} {1} {2} 当前 {3}，阈值 {4}' -f $now.ToString('yyyy-MM-dd HH:mm:ss'), $v.level.ToUpper(),
            $v.title, (Format-Metric $v.metric $v.value), (Format-Metric $v.metric $v.limit))
        $entry = [ordered]@{
            ts       = $now.ToString('o')
            kind     = 'raise'
            metric   = $v.metric
            level    = $v.level
            previous = $v.previous
            value    = $v.value
        }
        Add-JsonLine -Path $alertsLog -Content ($entry | ConvertTo-Json -Compress)
    }
    foreach ($key in $recovered) {
        $lines += ('{0} RECOVERED {1} 已恢复' -f $now.ToString('yyyy-MM-dd HH:mm:ss'), (Get-MetricTitle $key))
        $entry = [ordered]@{ ts = $now.ToString('o'); kind = 'recover'; metric = $key; level = 'ok'; previous = $prevLevels[$key]; value = $null }
        Add-JsonLine -Path $alertsLog -Content ($entry | ConvertTo-Json -Compress)
    }

    $subject = 'MRR 数据库监控：' + $overall.ToUpper()
    $body = ($lines -join "`r`n")

    if ($alertCfg -and $alertCfg.event_log) {
        $source = 'MRR-DB-Monitor'
        if ($alertCfg.event_source) { $source = $alertCfg.event_source }
        $entryType = 'Warning'
        $eventId = 9101
        if ($overall -eq 'critical') { $entryType = 'Error'; $eventId = 9102 }
        if ($recovered.Count -gt 0 -and $changed.Count -eq 0) { $entryType = 'Information'; $eventId = 9103 }
        try {
            Write-EventLog -LogName Application -Source $source -EventId $eventId -EntryType $entryType -Message ("$subject`r`n$body")
        }
        catch {
            Write-Log "写入事件日志失败（事件源未注册？用 install-task.ps1 注册）：$($_.Exception.Message)" 'WARN'
        }
    }

    if ($alertCfg -and $alertCfg.webhook_env) {
        $hook = [System.Environment]::GetEnvironmentVariable($alertCfg.webhook_env)
        if ($hook) {
            $content = "**$subject**`n> 时间：" + $now.ToString('yyyy-MM-dd HH:mm:ss') + "`n> 主机：" + $env:COMPUTERNAME + "`n" + (($lines | ForEach-Object { '> ' + $_ }) -join "`n")
            if ($content.Length -gt 3500) { $content = $content.Substring(0, 3500) + "`n> …（内容已截断，详见看板）" }
            $payload = @{ msgtype = 'markdown'; markdown = @{ content = $content } } | ConvertTo-Json -Depth 4
            try {
                Invoke-RestMethod -Uri $hook -Method Post -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($payload)) -TimeoutSec 8 | Out-Null
                Write-Log '已推送企业微信告警' 'INFO'
            }
            catch {
                Write-Log "Webhook 推送失败：$($_.Exception.Message)" 'WARN'
            }
        }
    }
}

# 保存状态与历史裁剪
$stateOut = [ordered]@{ levels = $levels; last_notified = $lastNotified; saved_at = $now.ToString('o') }
Write-JsonFile -Path $statePath -Content ($stateOut | ConvertTo-Json -Depth 5)

$keepDays = 30
if ($cfg.keep_days) { $keepDays = [int]$cfg.keep_days }
try {
    $info = Get-Item $historyPath
    if ($info.Length -gt 20MB) {
        $cutoff = $now.AddDays(-1 * $keepDays)
        $kept = @()
        foreach ($line in [System.IO.File]::ReadLines($historyPath)) {
            if (-not $line.Trim()) { continue }
            try {
                $obj = $line | ConvertFrom-Json
                if ([datetime]::Parse($obj.ts) -ge $cutoff) { $kept += $line }
            }
            catch { }
        }
        Write-JsonFile -Path $historyPath -Content (($kept -join [System.Environment]::NewLine) + [System.Environment]::NewLine)
        Write-Log ("历史已裁剪，保留 {0} 条（{1} 天）" -f $kept.Count, $keepDays) 'INFO'
    }
}
catch {
    Write-Log "历史裁剪失败：$($_.Exception.Message)" 'WARN'
}

# ---------------------------------------------------------------- 看板

function New-Sparkline {
    param([double[]]$Values, [int]$Width = 260, [int]$Height = 52, [string]$Color = '#2563eb')
    $nums = @($Values | Where-Object { $_ -ne $null })
    if ($nums.Count -lt 2) { return '<div class="empty">数据不足（至少需要两次采集）</div>' }
    $min = ($nums | Measure-Object -Minimum).Minimum
    $max = ($nums | Measure-Object -Maximum).Maximum
    # 视觉放大保护：波动小于 2% 时按 2% 带宽居中绘制，避免 0.01 的抖动被画成断崖。
    $minRange = [Math]::Max([Math]::Abs($max) * 0.02, 0.0001)
    if (($max - $min) -lt $minRange) {
        $center = ($max + $min) / 2
        $min = $center - ($minRange / 2)
        $max = $center + ($minRange / 2)
    }
    $step = $Width / ($nums.Count - 1)
    $points = @()
    for ($i = 0; $i -lt $nums.Count; $i++) {
        $x = [math]::Round($i * $step, 1).ToString($Inv)
        $y = [math]::Round($Height - 3 - (($nums[$i] - $min) / ($max - $min)) * ($Height - 8), 1).ToString($Inv)
        $points += "$x,$y"
    }
    $area = '0,' + $Height + ' ' + ($points -join ' ') + " $Width,$Height"
    $svg = '<svg viewBox="0 0 ' + $Width + ' ' + $Height + '" preserveAspectRatio="none" class="spark">'
    $svg += '<polygon points="' + $area + '" fill="' + $Color + '" opacity="0.08"/>'
    $svg += '<polyline points="' + ($points -join ' ') + '" fill="none" stroke="' + $Color + '" stroke-width="1.6" stroke-linejoin="round"/>'
    $svg += '</svg>'
    return $svg
}

$series = @{}
foreach ($key in @('connections_pct', 'cache_hit_pct', 'stmt_avg_latency_ms', 'disk_free_pct', 'dead_tuple_ratio', 'longest_xact_seconds')) {
    $series[$key] = @()
}
$recent = @()
if (Test-Path $historyPath) {
    $all = @()
    foreach ($line in [System.IO.File]::ReadLines($historyPath)) {
        if (-not $line.Trim()) { continue }
        try { $all += ($line | ConvertFrom-Json) } catch { }
    }
    if ($all.Count -gt 288) { $all = $all[($all.Count - 288)..($all.Count - 1)] }
    foreach ($rec in $all) {
        if (-not $rec.metrics) { continue }
        foreach ($key in @($series.Keys)) {
            if ($rec.metrics.$key -ne $null) { $series[$key] += [double]$rec.metrics.$key }
        }
        $recent += $rec
    }
}

$cards = @(
    @{ key = 'connections_pct'; hint = '上限 ' + [string]$metrics['max_connections'] + ' 个连接' },
    @{ key = 'cache_hit_pct'; hint = '目标 ≥ 99%' },
    @{ key = 'stmt_avg_latency_ms'; hint = '区间内 平均单次耗时' },
    @{ key = 'longest_xact_seconds'; hint = '超 5 分钟需排查' },
    @{ key = 'deadlocks_delta'; hint = '距上次采集新增' },
    @{ key = 'disk_free_pct'; hint = '数据盘剩余比例' },
    @{ key = 'bloat_max_ratio'; hint = '>30% 且 >1GiB 才告警' },
    @{ key = 'dead_tuple_ratio'; hint = 'autovacuum 健康度' }
)

$statusColor = @{ ok = '#15803d'; warning = '#b45309'; critical = '#b91c1c'; degraded = '#6b7280' }
$statusText = @{ ok = '正常'; warning = '预警'; critical = '严重'; degraded = '降级（查询级指标缺失）' }
$overallColor = $statusColor[$overall]
$overallText = $statusText[$overall]

$cardsHtml = New-Object System.Text.StringBuilder
foreach ($card in $cards) {
    $key = $card.key
    if (-not $metrics.ContainsKey($key)) { continue }
    $level = 'ok'
    if ($levels.ContainsKey($key)) { $level = $levels[$key] }
    $color = $statusColor[$level]
    $valueText = Format-Metric $key $metrics[$key]
    [void]$cardsHtml.AppendLine('<div class="card">')
    [void]$cardsHtml.AppendLine('  <div class="card-title">' + (Get-MetricTitle $key) + '</div>')
    [void]$cardsHtml.AppendLine('  <div class="card-value" style="color:' + $color + '">' + $valueText + '</div>')
    [void]$cardsHtml.AppendLine('  <div class="card-hint">' + $card.hint + '</div>')
    [void]$cardsHtml.AppendLine('</div>')
}

$rowsHtml = New-Object System.Text.StringBuilder
foreach ($key in ($metrics.Keys | Sort-Object)) {
    $level = 'ok'
    if ($levels.ContainsKey($key)) { $level = $levels[$key] }
    $color = $statusColor[$level]
    $unit = ''
    if ($units.ContainsKey($key)) { $unit = $units[$key] }
    [void]$rowsHtml.AppendLine('<tr><td>' + (Get-MetricTitle $key) + '</td><td class="mono">' + $key + '</td><td class="num">' + (Format-Metric $key $metrics[$key]) + '</td><td class="muted">' + $unit + '</td><td style="color:' + $color + ';font-weight:600">' + $statusText[$level] + '</td></tr>')
}

$violationHtml = '<p class="muted">当前无告警项。</p>'
if ($violations.Count -gt 0) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine('<ul class="violations">')
    foreach ($v in $violations) {
        [void]$sb.AppendLine('<li><strong style="color:' + $statusColor[$v.level] + '">' + $statusText[$v.level] + '</strong> ' + $v.title + ' = ' + (Format-Metric $v.metric $v.value) + '（阈值 ' + (Format-Metric $v.metric $v.limit) + '）</li>')
    }
    [void]$sb.AppendLine('</ul>')
    $violationHtml = $sb.ToString()
}

$trendHtml = New-Object System.Text.StringBuilder
$trendMeta = @(
    @{ key = 'connections_pct'; color = '#2563eb' },
    @{ key = 'cache_hit_pct'; color = '#0d9488' },
    @{ key = 'stmt_avg_latency_ms'; color = '#7c3aed' },
    @{ key = 'disk_free_pct'; color = '#ca8a04' }
)
foreach ($t in $trendMeta) {
    $vals = @()
    if ($series.ContainsKey($t.key)) { $vals = $series[$t.key] }
    $last = '-'
    if ($vals.Count -gt 0) { $last = Format-Metric $t.key $vals[$vals.Count - 1] }
    [void]$trendHtml.AppendLine('<div class="trend">')
    [void]$trendHtml.AppendLine('  <div class="trend-head"><span>' + (Get-MetricTitle $t.key) + '</span><span class="mono">' + $last + '</span></div>')
    [void]$trendHtml.AppendLine((New-Sparkline -Values $vals -Color $t.color))
    [void]$trendHtml.AppendLine('</div>')
}

$alertHtml = '<p class="muted">近 24 小时无状态变化。</p>'
$alertsPath = Join-Path $outDir 'alerts.jsonl'
if (Test-Path $alertsPath) {
    $cut = (Get-Date).AddHours(-24)
    $items = @()
    foreach ($line in [System.IO.File]::ReadLines($alertsPath)) {
        if (-not $line.Trim()) { continue }
        try {
            $obj = $line | ConvertFrom-Json
            if ([datetime]::Parse($obj.ts) -ge $cut) { $items += $obj }
        }
        catch { }
    }
    if ($items.Count -gt 0) {
        $sb = New-Object System.Text.StringBuilder
        [void]$sb.AppendLine('<table class="alerts"><thead><tr><th>时间</th><th>事件</th><th>指标</th><th>级别</th></tr></thead><tbody>')
        foreach ($item in ($items | Select-Object -Last 30)) {
            $kind = '触发'
            if ($item.kind -eq 'recover') { $kind = '恢复' }
            $lvlText = [string]$item.level
            if ($statusText.ContainsKey($lvlText)) { $lvlText = $statusText[$lvlText] }
            [void]$sb.AppendLine('<tr><td class="mono">' + ([datetime]::Parse($item.ts)).ToString('MM-dd HH:mm') + '</td><td>' + $kind + '</td><td>' + (Get-MetricTitle $item.metric) + '</td><td>' + $lvlText + '</td></tr>')
        }
        [void]$sb.AppendLine('</tbody></table>')
        $alertHtml = $sb.ToString()
    }
}

$stmtNote = '<span style="color:#15803d">已启用</span>'
if (-not $statementsAvailable) {
    $stmtNote = '<span style="color:#b45309">未启用（pg_stat_statements 未预加载，语句类指标与阈值已跳过）</span>'
}

$html = @"
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta http-equiv="refresh" content="300">
<title>MRR 数据库监控</title>
<style>
  :root { color-scheme: light; }
  * { box-sizing: border-box; }
  body { margin: 0; padding: 24px 28px 48px; font-family: "Segoe UI", "Microsoft YaHei", system-ui, sans-serif;
         background: #f6f7f9; color: #1f2937; font-size: 14px; line-height: 1.55; }
  h1 { font-size: 20px; margin: 0 0 4px; font-weight: 650; letter-spacing: .2px; }
  h2 { font-size: 15px; margin: 28px 0 10px; font-weight: 650; color: #374151; }
  .meta { color: #6b7280; font-size: 12.5px; }
  .badge { display: inline-block; padding: 2px 9px; border-radius: 999px; color: #fff; font-size: 12px; font-weight: 600; vertical-align: 2px; }
  .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-top: 18px; }
  .card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 14px 16px; }
  .card-title { font-size: 12.5px; color: #6b7280; }
  .card-value { font-size: 24px; font-weight: 650; margin: 4px 0 2px; font-variant-numeric: tabular-nums; }
  .card-hint { font-size: 11.5px; color: #9ca3af; }
  .trends { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 12px; }
  .trend { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px 14px; }
  .trend-head { display: flex; justify-content: space-between; font-size: 12.5px; color: #6b7280; margin-bottom: 6px; }
  .spark { width: 100%; height: 52px; display: block; }
  .empty { color: #9ca3af; font-size: 12px; height: 52px; line-height: 52px; text-align: center; }
  table { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; overflow: hidden; }
  th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #f1f2f4; font-size: 13px; }
  th { background: #fafafa; color: #6b7280; font-weight: 600; font-size: 12px; }
  tr:last-child td { border-bottom: none; }
  .num { text-align: right; font-variant-numeric: tabular-nums; }
  .mono { font-family: Consolas, "Courier New", monospace; font-size: 12px; color: #4b5563; }
  .muted { color: #9ca3af; }
  .violations { margin: 0; padding-left: 18px; background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px 16px 12px 34px; }
  .violations li { margin: 3px 0; }
  footer { margin-top: 32px; color: #9ca3af; font-size: 12px; border-top: 1px solid #e5e7eb; padding-top: 12px; }
  footer code { color: #6b7280; }
</style>
</head>
<body>
<h1>MRR 数据库监控 <span class="badge" style="background:$overallColor">$overallText</span></h1>
<div class="meta">
  采集时间 $(($now).ToString('yyyy-MM-dd HH:mm:ss'))　·　主机 $env:COMPUTERNAME　·　查询级指标 $stmtNote　·　页面每 5 分钟自动刷新（读取本地文件）
</div>

<div class="cards">
$($cardsHtml.ToString())
</div>

<h2>当前告警</h2>
$violationHtml

<h2>趋势（最近 24 小时，每点为一次采集）</h2>
<div class="trends">
$($trendHtml.ToString())
</div>

<h2>全部指标</h2>
<table>
<thead><tr><th>指标</th><th>键名</th><th class="num">当前值</th><th>单位</th><th>状态</th></tr></thead>
<tbody>
$($rowsHtml.ToString())
</tbody>
</table>

<h2>近 24 小时状态变化</h2>
$alertHtml

<footer>
  由 <code>scripts/db_monitor/collect.ps1</code> 生成，数据来自 PostgreSQL 统计视图、Actuator 与主机磁盘。
  本页不含患者姓名、病案号等标识；阈值见 <code>config.json</code>，口径见
  <code>docs/engineering/database-monitoring-without-prometheus.md</code>。
</footer>
</body>
</html>
"@

$htmlPath = Join-Path $outDir 'index.html'
Set-Content -Path $htmlPath -Value $html -Encoding UTF8

Write-Log ("采集完成：overall={0} 告警 {1} 项 历史 {2}" -f $overall, $violations.Count, $historyPath)

if ($overall -eq 'critical') { exit 2 }
if ($overall -eq 'warning') { exit 1 }
exit 0
