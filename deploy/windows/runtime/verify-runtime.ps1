[CmdletBinding()]
param(
    [string]$RuntimeRoot = (Split-Path -Parent $MyInvocation.MyCommand.Path)
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$manifestPath = Join-Path $RuntimeRoot 'runtime-manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "离线运行时清单不存在：$manifestPath"
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ([string]$manifest.architecture -ne 'windows-x64') {
    throw '离线运行时清单 architecture 必须为 windows-x64。'
}
if (-not $manifest.files) {
    throw '离线运行时清单缺少 files 摘要。'
}

$requiredFiles = @(
    'jdk/bin/java.exe',
    'jdk/bin/jcmd.exe',
    'nginx/nginx.exe',
    'nginx/conf/mime.types',
    'winsw/WinSW-x64.exe'
)
$runtimePrefix = [IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar

foreach ($relative in $requiredFiles) {
    $property = $manifest.files.PSObject.Properties[$relative]
    if (-not $property -or [string]$property.Value -notmatch '^[a-fA-F0-9]{64}$') {
        throw "离线运行时清单缺少有效 SHA-256：$relative"
    }

    $file = [IO.Path]::GetFullPath((Join-Path $RuntimeRoot $relative))
    if (-not $file.StartsWith($runtimePrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "离线运行时文件路径越界：$relative"
    }
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "离线运行时文件不存在：$relative"
    }

    $actual = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
    if ($actual -ne ([string]$property.Value).ToUpperInvariant()) {
        throw "离线运行时 SHA-256 不匹配：$relative"
    }
}

Write-Host '离线运行时关键文件摘要验证通过。' -ForegroundColor Green
