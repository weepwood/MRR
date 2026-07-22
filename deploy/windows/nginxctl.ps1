[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)]
    [ValidateSet('status','start','stop','restart','reload','test','pause','resume')]
    [string]$Command,
    [string]$Root = 'C:\MRR',
    [string]$Message = '系统维护中，请稍后再试。',
    [int]$TimeoutSeconds = 30
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$GatewayService = 'MRR-Gateway'
$NginxHome = Join-Path $Root 'runtime\nginx'
$NginxExe = Join-Path $NginxHome 'nginx.exe'
$NginxConfig = Join-Path $Root 'config\nginx\nginx.conf'
$MaintenanceFile = Join-Path $Root 'config\nginx\maintenance.inc'
$MaintenancePage = Join-Path $Root 'shared\maintenance.html'
$PidFile = Join-Path $Root 'state\nginx.pid'

function Assert-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw '该操作需要以管理员身份运行 PowerShell。'
    }
}

function Assert-NginxInstalled {
    if (-not (Test-Path -LiteralPath $NginxExe -PathType Leaf)) {
        throw "找不到包内 Nginx：$NginxExe。请先执行 deploy\windows\install.ps1。"
    }
    if (-not (Test-Path -LiteralPath $NginxConfig -PathType Leaf)) {
        throw "找不到 Nginx 配置：$NginxConfig。请先执行安装脚本或恢复配置。"
    }
}

function Get-GatewayService {
    $service = Get-Service -Name $GatewayService -ErrorAction SilentlyContinue
    if (-not $service) {
        throw "Windows 服务未安装：$GatewayService。请先执行 deploy\windows\install.ps1。"
    }
    return $service
}

function Invoke-NginxBinary {
    param(
        [Parameter(Mandatory)]
        [ValidateSet('test','reload')]
        [string]$Action
    )

    Assert-NginxInstalled
    $baseArgs = @('-p', $NginxHome, '-c', $NginxConfig)
    if ($Action -eq 'test') {
        & $NginxExe @baseArgs '-t'
    }
    else {
        & $NginxExe @baseArgs '-t'
        if ($LASTEXITCODE -ne 0) {
            throw 'Nginx 配置校验失败，拒绝重载。'
        }
        & $NginxExe @baseArgs '-s' 'reload'
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Nginx 操作失败：$Action"
    }
}

function Wait-ServiceState {
    param(
        [Parameter(Mandatory)]
        [ValidateSet('Running','Stopped')]
        [string]$Expected
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $service = Get-Service -Name $GatewayService -ErrorAction SilentlyContinue
        if ($service -and [string]$service.Status -eq $Expected) {
            return
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    throw "等待 $GatewayService 进入 $Expected 状态超时。"
}

function Test-MaintenanceEnabled {
    if (-not (Test-Path -LiteralPath $MaintenanceFile -PathType Leaf)) {
        return $false
    }
    return (Get-Content -LiteralPath $MaintenanceFile -Raw -Encoding UTF8) -match 'return\s+503'
}

function Set-NginxMaintenance {
    param(
        [Parameter(Mandatory)]
        [bool]$Enabled
    )

    Assert-Administrator
    New-Item -ItemType Directory -Path (Split-Path -Parent $MaintenanceFile) -Force | Out-Null

    if ($Enabled) {
        Set-Content -LiteralPath $MaintenanceFile -Value "return 503;`r`n" -Encoding ASCII
        if (Test-Path -LiteralPath $MaintenancePage -PathType Leaf) {
            $safeMessage = [Net.WebUtility]::HtmlEncode($Message)
            $html = Get-Content -LiteralPath $MaintenancePage -Raw -Encoding UTF8
            $html = [regex]::Replace(
                $html,
                '<p id="maintenance-message">.*?</p>',
                "<p id=`"maintenance-message`">$safeMessage</p>"
            )
            Set-Content -LiteralPath $MaintenancePage -Value $html -Encoding UTF8
        }
    }
    else {
        Set-Content -LiteralPath $MaintenanceFile -Value "# maintenance disabled`r`n" -Encoding ASCII
    }

    $service = Get-Service -Name $GatewayService -ErrorAction SilentlyContinue
    if ($service -and $service.Status -eq 'Running') {
        Invoke-NginxBinary reload
    }
}

function Show-NginxStatus {
    Assert-NginxInstalled
    $service = Get-Service -Name $GatewayService -ErrorAction SilentlyContinue
    $connections = @(Get-NetTCPConnection -LocalPort 80 -State Listen -ErrorAction SilentlyContinue)
    $processIds = @($connections | Select-Object -ExpandProperty OwningProcess -Unique)
    $versionText = (& $NginxExe -v 2>&1 | Out-String).Trim()
    $pid = if (Test-Path -LiteralPath $PidFile -PathType Leaf) {
        (Get-Content -LiteralPath $PidFile -Raw -Encoding ASCII).Trim()
    }
    else {
        $null
    }

    [pscustomobject]@{
        Version = $versionText
        Service = if ($service) { [string]$service.Status } else { 'NotInstalled' }
        Port80Listening = $connections.Count -gt 0
        ProcessIds = if ($processIds.Count -gt 0) { $processIds -join ',' } else { '' }
        PidFile = if ($pid) { $pid } else { '' }
        Maintenance = if (Test-MaintenanceEnabled) { 'ON' } else { 'OFF' }
        Executable = $NginxExe
        Configuration = $NginxConfig
    } | Format-List
}

switch ($Command) {
    status {
        Show-NginxStatus
    }
    test {
        Invoke-NginxBinary test
    }
    start {
        Assert-Administrator
        Invoke-NginxBinary test
        $service = Get-GatewayService
        if ($service.Status -ne 'Running') {
            Start-Service $GatewayService
            Wait-ServiceState Running
        }
        Show-NginxStatus
    }
    stop {
        Assert-Administrator
        $service = Get-GatewayService
        if ($service.Status -ne 'Stopped') {
            Stop-Service $GatewayService
            Wait-ServiceState Stopped
        }
        Show-NginxStatus
    }
    restart {
        Assert-Administrator
        Invoke-NginxBinary test
        $service = Get-GatewayService
        if ($service.Status -eq 'Running') {
            Restart-Service $GatewayService -Force
        }
        else {
            Start-Service $GatewayService
        }
        Wait-ServiceState Running
        Show-NginxStatus
    }
    reload {
        Assert-Administrator
        $service = Get-GatewayService
        if ($service.Status -ne 'Running') {
            throw 'Nginx 网关尚未运行，请先执行 start。'
        }
        Invoke-NginxBinary reload
        Show-NginxStatus
    }
    pause {
        $service = Get-GatewayService
        if ($service.Status -ne 'Running') {
            throw 'Nginx 网关尚未运行，无法进入维护模式。'
        }
        Set-NginxMaintenance $true
        Write-Host 'Nginx 已进入维护模式；网关保持运行，普通页面和 API 返回维护响应。' -ForegroundColor Yellow
        Show-NginxStatus
    }
    resume {
        Set-NginxMaintenance $false
        Write-Host 'Nginx 已退出维护模式。' -ForegroundColor Green
        Show-NginxStatus
    }
}
