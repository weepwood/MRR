[CmdletBinding()]
param(
    [string]$Root = 'C:\MRR',
    [string]$BackupFile,
    [string]$PostgresBin
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-PostgresBin([string]$Configured) {
    if ($Configured -and (Test-Path (Join-Path $Configured 'pg_restore.exe'))) {
        return (Resolve-Path $Configured).Path
    }
    $root = Join-Path $env:ProgramFiles 'PostgreSQL'
    $candidate = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName 'bin' } |
        Where-Object { Test-Path (Join-Path $_ 'pg_restore.exe') } |
        Select-Object -First 1
    if (-not $candidate) { throw '未找到 PostgreSQL 工具目录，请通过 -PostgresBin 指定。' }
    return $candidate
}

if (-not $BackupFile) {
    $BackupFile = Get-ChildItem (Join-Path $Root 'backups\postgresql\daily') -Filter '*.dump' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $BackupFile -or -not (Test-Path -LiteralPath $BackupFile -PathType Leaf)) {
    throw '没有找到可验证的数据库备份。'
}

$manifestFile = "$BackupFile.json"
if (-not (Test-Path -LiteralPath $manifestFile -PathType Leaf)) {
    throw "备份清单不存在：$manifestFile"
}
$manifest = Get-Content -LiteralPath $manifestFile -Raw -Encoding UTF8 | ConvertFrom-Json

if ([bool]$manifest.secretsIncluded) {
    throw '安全校验失败：普通备份清单声明包含 secrets。请删除该备份并重新生成。'
}
if ([string]$manifest.configurationPolicy -ne 'sanitized-no-secrets') {
    throw '配置备份策略不是 sanitized-no-secrets，拒绝将其视为合规备份。'
}

$actualDumpHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $BackupFile).Hash.ToLowerInvariant()
if ($actualDumpHash -ne [string]$manifest.dumpSha256) {
    throw '数据库备份 SHA-256 校验失败。'
}

$configFile = Join-Path (Split-Path $BackupFile -Parent) ([string]$manifest.configFile)
if (-not (Test-Path -LiteralPath $configFile -PathType Leaf)) {
    throw "配置备份不存在：$configFile"
}
$actualConfigHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $configFile).Hash.ToLowerInvariant()
if ($actualConfigHash -ne [string]$manifest.configSha256) {
    throw '配置备份 SHA-256 校验失败。'
}

$pgRestore = Join-Path (Resolve-PostgresBin $PostgresBin) 'pg_restore.exe'
& $pgRestore --list $BackupFile | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "pg_restore 目录校验失败，退出代码 $LASTEXITCODE"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($configFile)
try {
    if ($archive.Entries.Count -eq 0) { throw '配置备份 ZIP 为空。' }

    $unsafeEntries = @($archive.Entries | Where-Object {
        $name = $_.FullName.Replace('\', '/').ToLowerInvariant()
        $name -match '(^|/)secrets(/|$)' -or
        $name -match 'application-secrets\.properties$' -or
        $name -match '\.(key|pem|pfx|p12|jks)$'
    })
    if ($unsafeEntries.Count -gt 0) {
        $names = ($unsafeEntries | Select-Object -ExpandProperty FullName) -join ', '
        throw "配置备份包含禁止的 secrets 或私钥文件：$names"
    }

    $notice = $archive.Entries | Where-Object { $_.FullName -match 'SECRETS-NOT-INCLUDED\.txt$' } | Select-Object -First 1
    if (-not $notice) {
        throw '配置备份缺少 SECRETS-NOT-INCLUDED.txt 安全说明。'
    }
}
finally {
    $archive.Dispose()
}

$result = [ordered]@{
    result = 'PASS'
    verifiedAt = (Get-Date).ToUniversalTime().ToString('o')
    backupFile = (Resolve-Path $BackupFile).Path
    dumpSha256 = $actualDumpHash
    configArchive = (Resolve-Path $configFile).Path
    configSha256 = $actualConfigHash
    secretsIncluded = $false
    configurationPolicy = 'sanitized-no-secrets'
    checks = @(
        'manifest',
        'dump-sha256',
        'pg_restore-list',
        'config-sha256',
        'config-zip',
        'secrets-excluded',
        'private-keys-excluded'
    )
}
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $Root 'state\backup\last-verification.json') -Encoding UTF8
$result | Format-List
Write-Host '最近备份验证通过，普通配置包不包含 secrets。' -ForegroundColor Green
