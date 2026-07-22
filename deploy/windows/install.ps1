[CmdletBinding()]
param(
    [string]$Root = 'C:\MRR',
    [string]$WinSWPath,

    [string]$NginxPath,

    [Parameter(Mandatory = $true)]
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

function Assert-BundledRuntimeChecksums {
    param(
        [Parameter(Mandatory = $true)][string]$ChecksumPath
    )

    if (-not (Test-Path -LiteralPath $ChecksumPath -PathType Leaf)) {
        throw "发布包缺少 Nginx 运行时校验清单：$ChecksumPath"
    }

    $runtimeRoot = [IO.Path]::GetFullPath((Split-Path -Parent $ChecksumPath))
    $runtimePrefix = $runtimeRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar

    foreach ($line in Get-Content -LiteralPath $ChecksumPath -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line -notmatch '^([a-fA-F0-9]{64})\s+\*?(.+)$') {
            throw "无效的 Nginx 运行时校验行：$line"
        }

        $expected = $Matches[1].ToUpperInvariant()
        $relative = $Matches[2].Trim().TrimStart('.', '/', '\').Replace('/', [IO.Path]::DirectorySeparatorChar)
        $target = [IO.Path]::GetFullPath((Join-Path $runtimeRoot $relative))
        if (-not $target.StartsWith($runtimePrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Nginx 运行时校验路径越界：$relative"
        }
        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
            throw "Nginx 运行时文件不存在：$relative"
        }

        $actual = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
        if ($actual -ne $expected) {
            throw "Nginx 运行时 SHA-256 不匹配：$relative"
        }
    }

    Write-Host '包内 Nginx 与 WinSW 运行时校验通过。' -ForegroundColor Green
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
    }
    else {
        & $wrapperExe install
        if ($LASTEXITCODE -ne 0) { throw "WinSW install 失败：$serviceId" }
    }
}

Assert-Administrator

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$usingBundledWinSW = [string]::IsNullOrWhiteSpace($WinSWPath)
$usingBundledNginx = [string]::IsNullOrWhiteSpace($NginxPath)
if ($usingBundledWinSW -or $usingBundledNginx) {
    Assert-BundledRuntimeChecksums (Join-Path $scriptDir '..\..\runtime\SHA256SUMS')
}
if ($usingBundledWinSW) {
    $WinSWPath = Join-Path $scriptDir '..\..\runtime\winsw\WinSW-x64.exe'
}
if ($usingBundledNginx) {
    $NginxPath = Join-Path $scriptDir '..\..\runtime\nginx'
}

$resolvedWinSW = (Resolve-Path -LiteralPath $WinSWPath).Path
$resolvedNginx = (Resolve-Path -LiteralPath $NginxPath).Path
$resolvedJava = (Resolve-Path -LiteralPath $JavaHome).Path
$javaExe = Join-Path $resolvedJava 'bin\java.exe'

if (-not (Test-Path -LiteralPath $resolvedWinSW -PathType Leaf)) {
    throw "WinSW 文件不存在：$resolvedWinSW"
}
if (-not (Test-Path -LiteralPath $resolvedNginx -PathType Container)) {
    throw "Nginx 目录不存在：$resolvedNginx"
}
if (-not (Test-Path -LiteralPath (Join-Path $resolvedNginx 'nginx.exe') -PathType Leaf)) {
    throw "Nginx 目录中没有 nginx.exe：$resolvedNginx"
}
if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
    throw "JavaHome 中没有 bin\java.exe：$resolvedJava"
}

$directories = @(
    'config',
    'config\nginx',
    'secrets',
    'releases',
    'staging',
    'packages',
    'current',
    'previous-placeholder',
    'logs\backend',
    'logs\nginx',
    'logs\service',
    'monitoring-data',
    'ops\services',
    'runtime',
    'shared',
    'state',
    'backups'
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
}
else {
    Write-Host "保留现有 Nginx：$nginxDestination"
}

$rootUri = $Root.Replace('\', '/')
$templateTokens = @{
    'MRR_ROOT'     = $Root
    'MRR_URI_ROOT' = $rootUri
}

Write-TemplateIfMissing `
    -Source (Join-Path $scriptDir 'templates\application-prod.properties') `
    -Destination (Join-Path $Root 'config\application-prod.properties') `
    -Tokens $templateTokens
Write-TemplateIfMissing `
    -Source (Join-Path $scriptDir 'templates\application-secrets.properties') `
    -Destination (Join-Path $Root 'secrets\application-secrets.properties')
Write-TemplateIfMissing `
    -Source (Join-Path $scriptDir 'templates\nginx.conf') `
    -Destination (Join-Path $Root 'config\nginx\nginx.conf') `
    -Tokens $templateTokens
Write-TemplateIfMissing `
    -Source (Join-Path $scriptDir 'templates\proxy.inc') `
    -Destination (Join-Path $Root 'config\nginx\proxy.inc')
Write-TemplateIfMissing `
    -Source (Join-Path $scriptDir 'templates\maintenance.html') `
    -Destination (Join-Path $Root 'shared\maintenance.html')

Set-Content -LiteralPath (Join-Path $Root 'config\nginx\maintenance.inc') -Value "# maintenance disabled`r`n" -Encoding ASCII
Set-Content -LiteralPath (Join-Path $Root 'shared\healthz.txt') -Value "ok`r`n" -Encoding ASCII
Copy-Item -LiteralPath (Join-Path $scriptDir 'mrrctl.ps1') -Destination (Join-Path $Root 'ops\mrrctl.ps1') -Force
Copy-Item -LiteralPath (Join-Path $scriptDir 'nginxctl.ps1') -Destination (Join-Path $Root 'ops\nginxctl.ps1') -Force
Copy-Item -LiteralPath (Join-Path $scriptDir 'nginx-control.cmd') -Destination (Join-Path $Root 'ops\nginx-control.cmd') -Force

# 敏感配置只允许 Administrators 和 SYSTEM 访问。MRR-Backend 默认以 LocalSystem 运行。
$secretsPath = Join-Path $Root 'secrets'
& icacls.exe $secretsPath /inheritance:r | Out-Null
& icacls.exe $secretsPath /grant:r '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-18:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "设置 secrets ACL 失败：$secretsPath"
}

$tokens = @{
    'MRR_ROOT'     = $Root
    'MRR_URI_ROOT' = $rootUri
    'JAVA_EXE'     = $javaExe
    'NGINX_EXE'    = (Join-Path $nginxDestination 'nginx.exe')
    'NGINX_HOME'   = $nginxDestination
    'NGINX_CONFIG' = (Join-Path $Root 'config\nginx\nginx.conf')
}

Install-WinSWService `
    -BaseName 'mrr-backend' `
    -TemplatePath (Join-Path $scriptDir 'services\mrr-backend.xml') `
    -Tokens $tokens
Install-WinSWService `
    -BaseName 'mrr-gateway' `
    -TemplatePath (Join-Path $scriptDir 'services\mrr-gateway.xml') `
    -Tokens $tokens

$nginxExe = Join-Path $nginxDestination 'nginx.exe'
& $nginxExe -p $nginxDestination -c (Join-Path $Root 'config\nginx\nginx.conf') -t
if ($LASTEXITCODE -ne 0) {
    throw 'Nginx 配置校验失败。请修复后再启动服务。'
}

Write-Host ''
Write-Host 'MRR Windows 运维环境安装完成。' -ForegroundColor Green
Write-Host "1. 编辑 $Root\config\application-prod.properties"
Write-Host "2. 编辑 $Root\secrets\application-secrets.properties"
Write-Host "3. 将发布包放入 $Root\packages"
Write-Host "4. 执行：$Root\ops\mrrctl.ps1 deploy <发布包路径>"
Write-Host "5. Nginx 控制：$Root\ops\nginx-control.cmd status|start|stop|restart|reload|test|pause|resume"
