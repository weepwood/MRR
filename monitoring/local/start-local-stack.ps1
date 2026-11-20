#requires -Version 5.1
<#
.SYNOPSIS
    启动/停止本地 Prometheus + Grafana + postgres_exporter 监控栈（Windows 单机，不依赖 Docker）。

.DESCRIPTION
    三件套的分工：
      postgres_exporter  读 PostgreSQL，暴露 9187
      prometheus         拉取 9187 与 Spring Boot Actuator(18046)，评估告警规则，9090
      grafana            读 Prometheus，渲染预置看板，默认 3001（避开已占用的 3000 服务实例）

    所有运行数据写在 <repo>\.workbuddy\ 下（已 gitignore），不污染 Grafana 安装目录。

.PARAMETER Dsn
    postgres_exporter 的连接串，形如
      postgresql://mrr_monitor:密码@127.0.0.1:5432/imgapi?sslmode=disable
    也可用环境变量 MRR_MONITOR_DSN 传入，避免密码进入命令行历史。

.PARAMETER Stop
    按 .workbuddy\monitoring-demo\pids.json 记录的 PID 停止本脚本拉起的进程。

.EXAMPLE
    $env:MRR_MONITOR_DSN = 'postgresql://mrr_monitor:***@127.0.0.1:5432/imgapi?sslmode=disable'
    .\start-local-stack.ps1

.EXAMPLE
    .\start-local-stack.ps1 -Stop
#>
[CmdletBinding()]
param(
    [string]$Dsn = $env:MRR_MONITOR_DSN,
    [string]$PrometheusHome = 'D:\App\DevEnv\prometheus',
    [string]$GrafanaHome = 'D:\App\DevEnv\Grafana\grafana',
    [string]$ExporterExe = 'D:\App\DevEnv\postgres_exporter\postgres_exporter-0.21.1.windows-amd64\postgres_exporter.exe',
    [int]$GrafanaPort = 3001,
    [switch]$SkipExporter,
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # 探活时的下载进度条会刷屏
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stateDir = Join-Path $repo '.workbuddy\monitoring-demo'
$pidFile = Join-Path $stateDir 'pids.json'
New-Item -ItemType Directory -Force -Path $stateDir | Out-Null

function Write-Step($Message) { Write-Host ('[stack] ' + $Message) }

if ($Stop) {
    if (-not (Test-Path $pidFile)) { Write-Step '没有找到 PID 记录，可能不是本脚本启动的'; return }
    $pids = Get-Content $pidFile -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($name in 'grafana', 'prometheus', 'exporter') {
        $procId = $pids.$name
        if (-not $procId) { continue }
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($proc) {
            Stop-Process -Id $procId -Force
            Write-Step ("已停止 {0} (PID {1})" -f $name, $procId)
        }
    }
    Remove-Item $pidFile -Force
    return
}

# ---------------------------------------------------------------- 前置检查

foreach ($p in @(
        @{ Path = (Join-Path $PrometheusHome 'prometheus.exe'); Name = 'prometheus.exe' },
        @{ Path = (Join-Path $GrafanaHome 'bin\grafana.exe'); Name = 'grafana.exe' }
    )) {
    if (-not (Test-Path $p.Path)) { throw ("找不到 {0}：{1}" -f $p.Name, $p.Path) }
}
if (-not $SkipExporter -and -not (Test-Path $ExporterExe)) {
    throw ("找不到 postgres_exporter：{0}（或用 -SkipExporter 跳过数据库采集）" -f $ExporterExe)
}
if (-not $SkipExporter -and -not $Dsn) {
    throw '缺少 -Dsn 或环境变量 MRR_MONITOR_DSN，无法启动 postgres_exporter（或用 -SkipExporter）'
}

# 端口占用检查：避免两个 Prometheus/Grafana 抢同一个端口
foreach ($port in 9090, 9187, $GrafanaPort) {
    $busy = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($busy -and -not $Stop) { throw ("端口 {0} 已被占用（PID {1}），请先停止占用进程" -f $port, $busy[0].OwningProcess) }
}

# ---------------------------------------------------------------- 目录

$promData = Join-Path $repo '.workbuddy\prom-data'
$grafData = Join-Path $repo '.workbuddy\grafana-data'
$grafLogs = Join-Path $repo '.workbuddy\grafana-logs'
foreach ($d in $promData, $grafData, $grafLogs) { New-Item -ItemType Directory -Force -Path $d | Out-Null }

$promConfig = Join-Path $repo 'monitoring\local\prometheus.local.yml'
$provisioning = Join-Path $repo 'monitoring\grafana\provisioning'
$dashboards = Join-Path $repo 'monitoring\grafana\dashboards'
$queries = Join-Path $repo 'monitoring\postgresql\queries.yaml'

# ---------------------------------------------------------------- postgres_exporter

$exporterPid = $null
if (-not $SkipExporter) {
    # 0.20+ 的 --config.file 只接受 exporter 自身的认证配置（auth_modules），
    # 自定义查询仍走 --extend.query-path（该开关已标记 DEPRECATED，但仍是唯一入口）。
    $env:DATA_SOURCE_NAME = $Dsn
    $argLine = '--extend.query-path="{0}" --web.listen-address=127.0.0.1:9187 --log.level=warn' -f $queries
    $proc = Start-Process -FilePath $ExporterExe -ArgumentList $argLine -PassThru -WindowStyle Hidden
    $exporterPid = $proc.Id
    Write-Step ("postgres_exporter 已启动 PID {0}" -f $exporterPid)
    Remove-Item Env:\DATA_SOURCE_NAME
}

# ---------------------------------------------------------------- Prometheus

$promArgs = @(
    ('--config.file="{0}"' -f $promConfig),
    ('--storage.tsdb.path="{0}"' -f $promData),
    '--web.listen-address=127.0.0.1:9090',
    '--web.enable-lifecycle',
    '--storage.tsdb.retention.time=15d'
) -join ' '
$promProc = Start-Process -FilePath (Join-Path $PrometheusHome 'prometheus.exe') -ArgumentList $promArgs -PassThru -WindowStyle Hidden
Write-Step ("Prometheus 已启动 PID {0}" -f $promProc.Id)

# ---------------------------------------------------------------- Grafana

# 用环境变量覆盖，不改安装目录里的 conf/*.ini，便于多实例共存。
$env:GF_PATHS_PROVISIONING = $provisioning
$env:GF_PATHS_DATA = $grafData
$env:GF_PATHS_LOGS = $grafLogs
$env:GF_SERVER_HTTP_PORT = [string]$GrafanaPort
$env:GF_AUTH_ANONYMOUS_ENABLED = 'true'      # 演示用：只给 Viewer，不能改看板
$env:GF_AUTH_ANONYMOUS_ORG_ROLE = 'Viewer'
$env:GF_ANALYTICS_REPORTING_ENABLED = 'false'
$env:GF_NEWS_NEWS_FEED_ENABLED = 'false'
$env:MRR_GRAFANA_DASHBOARD_PATH = $dashboards

$grafArgs = 'server --homepath "{0}"' -f $GrafanaHome
$grafProc = Start-Process -FilePath (Join-Path $GrafanaHome 'bin\grafana.exe') -ArgumentList $grafArgs -PassThru -WindowStyle Hidden
Write-Step ("Grafana 已启动 PID {0}" -f $grafProc.Id)

foreach ($v in 'GF_PATHS_PROVISIONING', 'GF_PATHS_DATA', 'GF_PATHS_LOGS', 'GF_SERVER_HTTP_PORT',
    'GF_AUTH_ANONYMOUS_ENABLED', 'GF_AUTH_ANONYMOUS_ORG_ROLE', 'GF_ANALYTICS_REPORTING_ENABLED',
    'GF_NEWS_NEWS_FEED_ENABLED', 'MRR_GRAFANA_DASHBOARD_PATH') {
    Remove-Item ("Env:\" + $v) -ErrorAction SilentlyContinue
}

# ---------------------------------------------------------------- 记录 PID

[pscustomobject]@{
    exporter   = $exporterPid
    prometheus = $promProc.Id
    grafana    = $grafProc.Id
    startedAt  = (Get-Date).ToString('s')
} | ConvertTo-Json | Set-Content -Path $pidFile -Encoding UTF8

Write-Host ''
Write-Step '等待服务就绪 ...'
$deadline = (Get-Date).AddSeconds(90)
$ready = $false
while ((Get-Date) -lt $deadline) {
    try {
        $null = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 'http://127.0.0.1:9090/-/ready'
        $null = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 ("http://127.0.0.1:{0}/api/health" -f $GrafanaPort)
        $ready = $true
        break
    }
    catch { Start-Sleep -Seconds 3 }
}

Write-Host ''
if ($ready) {
    Write-Step '就绪'
    Write-Host ("  Prometheus   http://127.0.0.1:9090/targets")
    Write-Host ("  Grafana      http://127.0.0.1:{0}/d/mrr-postgresql-overview" -f $GrafanaPort)
    Write-Host ("  应用概览     http://127.0.0.1:{0}/d/mrr-application-overview" -f $GrafanaPort)
} else {
    Write-Step ("90 秒内未探活成功，请检查 Grafana 日志：{0}" -f $grafLogs)
}
