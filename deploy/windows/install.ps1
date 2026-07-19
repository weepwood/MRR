[CmdletBinding()]
param(
    [string]$Root = 'C:\MRR',
    [string]$WinSWPath,
    [string]$NginxPath,
    [string]$JavaHome,
    [switch]$Force
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

function Resolve-FileOption([string]$Provided, [string[]]$Candidates, [string]$Description) {
    if ($Provided) {
        if (-not (Test-Path -LiteralPath $Provided -PathType Leaf)) { throw "$Description 不存在：$Provided" }
        return (Resolve-Path -LiteralPath $Provided).Path
    }
    foreach ($candidate in $Candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return (Resolve-Path -LiteralPath $candidate).Path }
    }
    throw "未找到 $Description。可将运行时放入 deploy\windows\runtime，或通过参数手工指定。"
}

function Resolve-DirectoryOption([string]$Provided, [string[]]$Candidates, [string]$RequiredChild, [string]$Description) {
    $paths = @()
    if ($Provided) { $paths += $Provided }
    $paths += $Candidates
    foreach ($candidate in $paths) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate $RequiredChild) -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw "未找到 $Description。可将运行时放入 deploy\windows\runtime，或通过参数手工指定。"
}

function Write-TemplateIfMissing {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination,
        [hashtable]$Tokens = @{}
    )
    if ((Test-Path -LiteralPath $Destination) -and -not $Force) {
        Write-Host "保留现有文件：$Destination"
        return
    }
    $content = Get-Content -LiteralPath $Source -Raw -Encoding UTF8
    foreach ($entry in $Tokens.GetEnumerator()) {
        $content = $content.Replace("{{$($entry.Key)}}", [string]$entry.Value)
    }
    New-Item -ItemType Directory -Path (Split-Path -Parent $Destination) -Force | Out-Null
    Set-Content -LiteralPath $Destination -Value $content -Encoding UTF8
}

function Install-WinSWService {
    param(
        [Parameter(Mandatory = $true)][string]$BaseName,
        [Parameter(Mandatory = $true)][string]$TemplatePath,
        [Parameter(Mandatory = $true)][hashtable]$Tokens
    )
    $serviceDir = Join-Path $Root 'ops\services'
    $wrapperExe = Join-Path $serviceDir "$BaseName.exe"
    $wrapperXml = Join-Path $serviceDir "$BaseName.xml"
    Copy-Item -LiteralPath $resolvedWinSW -Destination $wrapperExe -Force
    $xml = Get-Content -LiteralPath $TemplatePath -Raw -Encoding UTF8
    foreach ($entry in $Tokens.GetEnumerator()) {
        $xml = $xml.Replace("{{$($entry.Key)}}", [string]$entry.Value)
    }
    Set-Content -LiteralPath $wrapperXml -Value $xml -Encoding UTF8

    $serviceId = if ($BaseName -eq 'mrr-backend') { 'MRR-Backend' } else { 'MRR-Gateway' }
    if (Get-Service -Name $serviceId -ErrorAction SilentlyContinue) {
        Write-Host "服务已存在，刷新配置：$serviceId"
        & $wrapperExe refresh
        if ($LASTEXITCODE -ne 0) { throw "WinSW refresh 失败：$serviceId" }
    } else {
        & $wrapperExe install
        if ($LASTEXITCODE -ne 0) { throw "WinSW install 失败：$serviceId" }
    }
}

function Set-ProtectedDirectoryAcl([string]$Path) {
    & icacls.exe $Path /inheritance:r | Out-Null
    & icacls.exe $Path /grant:r '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "设置受保护目录 ACL 失败：$Path" }
}

function Install-DailyBackupTask {
    $taskName = 'MRR-Daily-Backup'
    $script = Join-Path $Root 'ops\backup\backup-database.ps1'
    $arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$script`" -Root `"$Root`""
    $existing = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($existing -and -not $Force) {
        Write-Host "保留现有计划任务：$taskName"
        return
    }
    if ($existing) { Unregister-ScheduledTask -TaskName $taskName -Confirm:$false }
    $action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
    $trigger = New-ScheduledTaskTrigger -Daily -At '02:00'
    $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal `
        -Description 'MRR 每日数据库和服务器配置备份' | Out-Null
    Write-Host "已创建每日备份任务：$taskName（02:00）"
}

Assert-Administrator
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$resolvedWinSW = Resolve-FileOption $WinSWPath @(
    (Join-Path $scriptDir 'runtime\winsw\WinSW-x64.exe'),
    (Join-Path $scriptDir 'runtime\WinSW-x64.exe')
) 'WinSW'
$resolvedNginx = Resolve-DirectoryOption $NginxPath @(
    (Join-Path $scriptDir 'runtime\nginx')
) 'nginx.exe' 'Nginx'
$resolvedJava = Resolve-DirectoryOption $JavaHome @(
    (Join-Path $scriptDir 'runtime\jre'),
    (Join-Path $scriptDir 'runtime\jdk')
) 'bin\java.exe' 'JDK/JRE 21'

$directories = @(
    'config', 'config\nginx', 'secrets', 'releases', 'staging', 'packages',
    'current', 'previous-placeholder', 'logs\backend', 'logs\nginx', 'logs\service',
    'logs\diagnostics', 'ops\services', 'ops\backup', 'ops\diagnostics', 'runtime',
    'shared', 'state', 'state\audit', 'state\backup', 'backups',
    'backups\postgresql\daily', 'backups\postgresql\weekly', 'backups\postgresql\monthly',
    'backups\restore-drills'
)
foreach ($relative in $directories) {
    New-Item -ItemType Directory -Path (Join-Path $Root $relative) -Force | Out-Null
}
Remove-Item -LiteralPath (Join-Path $Root 'previous-placeholder') -Force -ErrorAction SilentlyContinue

$nginxDestination = Join-Path $Root 'runtime\nginx'
if ((Test-Path -LiteralPath $nginxDestination) -and $Force) {
    Remove-Item -LiteralPath $nginxDestination -Recurse -Force
}
if (-not (Test-Path -LiteralPath $nginxDestination)) {
    Copy-Item -LiteralPath $resolvedNginx -Destination $nginxDestination -Recurse -Force
} else {
    Write-Host "保留现有 Nginx：$nginxDestination"
}

$javaRuntime = $resolvedJava
$bundledRuntimeRoot = [IO.Path]::GetFullPath((Join-Path $scriptDir 'runtime')).TrimEnd('\') + '\'
$resolvedJavaFull = [IO.Path]::GetFullPath($resolvedJava)
if ($resolvedJavaFull.StartsWith($bundledRuntimeRoot, [StringComparison]::OrdinalIgnoreCase)) {
    $javaDestination = Join-Path $Root 'runtime\java'
    if ((Test-Path -LiteralPath $javaDestination) -and $Force) {
        Remove-Item -LiteralPath $javaDestination -Recurse -Force
    }
    if (-not (Test-Path -LiteralPath $javaDestination)) {
        Copy-Item -LiteralPath $resolvedJava -Destination $javaDestination -Recurse -Force
    } else {
        Write-Host "保留现有 Java 运行时：$javaDestination"
    }
    $javaRuntime = $javaDestination
}
$javaExe = Join-Path $javaRuntime 'bin\java.exe'

$rootUri = $Root.Replace('\', '/')
$templateTokens = @{ 'MRR_ROOT' = $Root; 'MRR_URI_ROOT' = $rootUri }
Write-TemplateIfMissing -Source (Join-Path $scriptDir 'templates\application-prod.properties') `
    -Destination (Join-Path $Root 'config\application-prod.properties') -Tokens $templateTokens
Write-TemplateIfMissing -Source (Join-Path $scriptDir 'templates\application-secrets.properties') `
    -Destination (Join-Path $Root 'secrets\application-secrets.properties')
Write-TemplateIfMissing -Source (Join-Path $scriptDir 'templates\nginx.conf') `
    -Destination (Join-Path $Root 'config\nginx\nginx.conf') -Tokens $templateTokens
Write-TemplateIfMissing -Source (Join-Path $scriptDir 'templates\proxy.inc') `
    -Destination (Join-Path $Root 'config\nginx\proxy.inc')
Write-TemplateIfMissing -Source (Join-Path $scriptDir 'templates\maintenance.html') `
    -Destination (Join-Path $Root 'shared\maintenance.html')

Set-Content -LiteralPath (Join-Path $Root 'config\nginx\maintenance.inc') -Value "# maintenance disabled`r`n" -Encoding ASCII
Set-Content -LiteralPath (Join-Path $Root 'shared\healthz.txt') -Value "MRR_FRONTEND_OK`r`n" -Encoding ASCII
Copy-Item -LiteralPath (Join-Path $scriptDir 'mrrctl.ps1') -Destination (Join-Path $Root 'ops\mrrctl.ps1') -Force
Copy-Item -LiteralPath (Join-Path $scriptDir 'mrr-manager.ps1') -Destination (Join-Path $Root 'ops\mrr-manager.ps1') -Force
Copy-Item -LiteralPath (Join-Path $scriptDir 'MRR-Manager.cmd') -Destination (Join-Path $Root 'MRR-Manager.cmd') -Force
Copy-Item -Path (Join-Path $scriptDir 'backup\*') -Destination (Join-Path $Root 'ops\backup') -Recurse -Force
Copy-Item -Path (Join-Path $scriptDir 'diagnostics\*') -Destination (Join-Path $Root 'ops\diagnostics') -Recurse -Force

Set-ProtectedDirectoryAcl (Join-Path $Root 'secrets')
Set-ProtectedDirectoryAcl (Join-Path $Root 'state\audit')
Set-ProtectedDirectoryAcl (Join-Path $Root 'backups')

$tokens = @{
    'MRR_ROOT' = $Root
    'MRR_URI_ROOT' = $rootUri
    'JAVA_EXE' = $javaExe
    'NGINX_EXE' = (Join-Path $nginxDestination 'nginx.exe')
    'NGINX_HOME' = $nginxDestination
    'NGINX_CONFIG' = (Join-Path $Root 'config\nginx\nginx.conf')
}
Install-WinSWService -BaseName 'mrr-backend' -TemplatePath (Join-Path $scriptDir 'services\mrr-backend.xml') -Tokens $tokens
Install-WinSWService -BaseName 'mrr-gateway' -TemplatePath (Join-Path $scriptDir 'services\mrr-gateway.xml') -Tokens $tokens

$nginxExe = Join-Path $nginxDestination 'nginx.exe'
& $nginxExe -p $nginxDestination -c (Join-Path $Root 'config\nginx\nginx.conf') -t
if ($LASTEXITCODE -ne 0) { throw 'Nginx 配置校验失败。请修复后再启动服务。' }

Install-DailyBackupTask
Write-Host ''
Write-Host 'MRR 单服务器运维环境安装完成。' -ForegroundColor Green
Write-Host "1. 编辑 $Root\config\application-prod.properties"
Write-Host "2. 编辑 $Root\secrets\application-secrets.properties"
Write-Host "3. 将发布包放入 $Root\packages"
Write-Host "4. 双击 $Root\MRR-Manager.cmd 完成部署和日常运维"
