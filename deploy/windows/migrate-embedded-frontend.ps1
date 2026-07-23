[CmdletBinding()]
param(
    [string]$Root = 'C:\MRR'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw '请以管理员身份运行 PowerShell。'
    }
}

function Render-Template {
    param(
        [Parameter(Mandatory)][string]$Source,
        [Parameter(Mandatory)][string]$RootUri
    )
    $content = Get-Content -LiteralPath $Source -Raw -Encoding UTF8
    return $content.Replace('{{MRR_ROOT}}', $Root).Replace('{{MRR_URI_ROOT}}', $RootUri)
}

Assert-Administrator

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$nginxTemplate = Join-Path $scriptDir 'templates\nginx.conf'
$frontendTemplate = Join-Path $scriptDir 'templates\frontend-mode-embedded.inc'
$nginxConfig = Join-Path $Root 'config\nginx\nginx.conf'
$frontendMode = Join-Path $Root 'config\nginx\frontend-mode.inc'
$nginxExe = Join-Path $Root 'runtime\nginx\nginx.exe'
$nginxHome = Join-Path $Root 'runtime\nginx'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$nginxBackup = "$nginxConfig.pre-embedded-$timestamp.bak"
$modeBackup = "$frontendMode.pre-embedded-$timestamp.bak"
$rootUri = $Root.Replace('\', '/')

foreach ($required in $nginxTemplate,$frontendTemplate,$nginxExe) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "迁移所需文件不存在：$required"
    }
}

if (Test-Path -LiteralPath $nginxConfig -PathType Leaf) {
    Copy-Item -LiteralPath $nginxConfig -Destination $nginxBackup -Force
}
if (Test-Path -LiteralPath $frontendMode -PathType Leaf) {
    Copy-Item -LiteralPath $frontendMode -Destination $modeBackup -Force
}

try {
    New-Item -ItemType Directory -Path (Split-Path -Parent $nginxConfig) -Force | Out-Null
    Set-Content -LiteralPath $nginxConfig -Value (Render-Template $nginxTemplate $rootUri) -Encoding UTF8
    Set-Content -LiteralPath $frontendMode -Value (Render-Template $frontendTemplate $rootUri) -Encoding UTF8

    & $nginxExe -p $nginxHome -c $nginxConfig -t
    if ($LASTEXITCODE -ne 0) { throw '新版 Nginx 配置校验失败。' }

    foreach ($name in 'mrrctl.ps1','mrr-manager.ps1','nginxctl.ps1','nginx-control.cmd','MRR-Manager.cmd') {
        $source = Join-Path $scriptDir $name
        if (Test-Path -LiteralPath $source -PathType Leaf) {
            Copy-Item -LiteralPath $source -Destination (Join-Path $Root "ops\$name") -Force
        }
    }
    $manager = Join-Path $scriptDir 'MRR-Manager.cmd'
    if (Test-Path -LiteralPath $manager -PathType Leaf) {
        Copy-Item -LiteralPath $manager -Destination (Join-Path $Root 'ops\MRR-管理中心.cmd') -Force
    }

    $gateway = Get-Service 'MRR-Gateway' -ErrorAction SilentlyContinue
    if ($gateway -and $gateway.Status -eq 'Running') {
        & $nginxExe -p $nginxHome -c $nginxConfig -s reload
        if ($LASTEXITCODE -ne 0) { throw 'Nginx 平滑重载失败。' }
    }

    Write-Host '现有服务器已迁移到 JAR 内嵌前端模式。' -ForegroundColor Green
    Write-Host "原 Nginx 配置备份：$nginxBackup"
    Write-Host "切回外置前端：$Root\ops\mrrctl.ps1 frontend external"
}
catch {
    if (Test-Path -LiteralPath $nginxBackup -PathType Leaf) {
        Copy-Item -LiteralPath $nginxBackup -Destination $nginxConfig -Force
    }
    if (Test-Path -LiteralPath $modeBackup -PathType Leaf) {
        Copy-Item -LiteralPath $modeBackup -Destination $frontendMode -Force
    }
    else {
        Remove-Item -LiteralPath $frontendMode -Force -ErrorAction SilentlyContinue
    }
    throw
}
