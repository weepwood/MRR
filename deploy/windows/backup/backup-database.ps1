[CmdletBinding()]
param(
    [string]$Root = 'C:\MRR',
    [string]$PostgresBin,
    [string]$SecondaryCopyPath,
    [int]$DailyRetentionDays = 14,
    [int]$WeeklyRetentionDays = 56,
    [int]$MonthlyRetentionDays = 365
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-Properties([string]$Path) {
    $result = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $result }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $text = $line.Trim()
        if (-not $text -or $text.StartsWith('#')) { continue }
        $separator = $text.IndexOf('=')
        if ($separator -lt 1) { continue }
        $result[$text.Substring(0, $separator).Trim()] = $text.Substring($separator + 1).Trim()
    }
    return $result
}

function Resolve-PostgresBin([string]$Configured) {
    if ($Configured -and (Test-Path (Join-Path $Configured 'pg_dump.exe'))) {
        return (Resolve-Path $Configured).Path
    }
    $root = Join-Path $env:ProgramFiles 'PostgreSQL'
    $candidate = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
        Sort-Object { [version]($_.Name -replace '[^0-9.]', '') } -Descending |
        ForEach-Object { Join-Path $_.FullName 'bin' } |
        Where-Object { Test-Path (Join-Path $_ 'pg_dump.exe') } |
        Select-Object -First 1
    if (-not $candidate) { throw '未找到 PostgreSQL 工具目录，请通过 -PostgresBin 指定。' }
    return $candidate
}

function Copy-BackupSet([string]$DumpFile, [string]$ManifestFile, [string]$ConfigFile, [string]$Destination) {
    if (-not $Destination) { return }
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    Copy-Item -LiteralPath $DumpFile, $ManifestFile, $ConfigFile -Destination $Destination -Force
}

function Remove-Expired([string]$Path, [int]$Days) {
    if (-not (Test-Path $Path)) { return }
    Get-ChildItem $Path -File | Where-Object {
        $_.LastWriteTime -lt (Get-Date).AddDays(-$Days)
    } | Remove-Item -Force
}

$config = Read-Properties (Join-Path $Root 'config\application-prod.properties')
$secrets = Read-Properties (Join-Path $Root 'secrets\application-secrets.properties')
foreach ($key in $secrets.Keys) { $config[$key] = $secrets[$key] }

$jdbcUrl = [string]$config['spring.datasource.url']
$dbUser = [string]$config['spring.datasource.username']
$dbPassword = [string]$config['spring.datasource.password']
if (-not $jdbcUrl -or -not $dbUser -or -not $dbPassword) {
    throw '数据库 URL、用户名或密码未配置，无法执行备份。'
}
if ($jdbcUrl -notmatch '^jdbc:postgresql://(?<host>[^:/?]+)(:(?<port>\d+))?/(?<database>[^?]+)') {
    throw "无法解析 PostgreSQL JDBC URL：$jdbcUrl"
}

$hostName = $Matches.host
$port = if ($Matches.port) { [int]$Matches.port } else { 5432 }
$database = $Matches.database
$pgBin = Resolve-PostgresBin $PostgresBin
$pgDump = Join-Path $pgBin 'pg_dump.exe'
$pgRestore = Join-Path $pgBin 'pg_restore.exe'

$backupRoot = Join-Path $Root 'backups\postgresql'
$dailyDir = Join-Path $backupRoot 'daily'
$weeklyDir = Join-Path $backupRoot 'weekly'
$monthlyDir = Join-Path $backupRoot 'monthly'
$stateDir = Join-Path $Root 'state\backup'
New-Item -ItemType Directory -Force -Path $dailyDir, $weeklyDir, $monthlyDir, $stateDir | Out-Null

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dumpFile = Join-Path $dailyDir "$database-$timestamp.dump"
$tempDump = "$dumpFile.partial"
$configArchive = Join-Path $dailyDir "mrr-config-$timestamp.zip"
$manifestFile = "$dumpFile.json"
$tempConfigDir = Join-Path $env:TEMP "mrr-config-$timestamp"
$startedAt = Get-Date

$secondary = $SecondaryCopyPath
if (-not $secondary -and $config.ContainsKey('app.backup.secondary-path')) {
    $secondary = [string]$config['app.backup.secondary-path']
}

$env:PGPASSWORD = $dbPassword
try {
    & $pgDump --host=$hostName --port=$port --username=$dbUser --dbname=$database `
        --format=custom --compress=6 --no-owner --no-acl --file=$tempDump
    if ($LASTEXITCODE -ne 0) { throw "pg_dump 失败，退出代码 $LASTEXITCODE" }

    & $pgRestore --list $tempDump | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "pg_restore 目录校验失败，退出代码 $LASTEXITCODE" }
    Move-Item -LiteralPath $tempDump -Destination $dumpFile -Force

    New-Item -ItemType Directory -Path $tempConfigDir -Force | Out-Null
    foreach ($source in @(
        (Join-Path $Root 'config'),
        (Join-Path $Root 'secrets'),
        (Join-Path $Root 'current\manifest.json')
    )) {
        if (Test-Path -LiteralPath $source) {
            Copy-Item -LiteralPath $source -Destination $tempConfigDir -Recurse -Force
        }
    }
    Compress-Archive -Path (Join-Path $tempConfigDir '*') -DestinationPath $configArchive -CompressionLevel Optimal -Force

    $dumpItem = Get-Item $dumpFile
    $configItem = Get-Item $configArchive
    $manifest = [ordered]@{
        result = 'SUCCESS'
        type = 'single-server-backup'
        database = $database
        host = $hostName
        startedAt = $startedAt.ToUniversalTime().ToString('o')
        completedAt = (Get-Date).ToUniversalTime().ToString('o')
        dumpFile = $dumpItem.Name
        dumpSizeBytes = $dumpItem.Length
        dumpSha256 = (Get-FileHash -Algorithm SHA256 $dumpFile).Hash.ToLowerInvariant()
        configFile = $configItem.Name
        configSizeBytes = $configItem.Length
        configSha256 = (Get-FileHash -Algorithm SHA256 $configArchive).Hash.ToLowerInvariant()
        verifiedBy = 'pg_restore --list'
        secondaryCopyPath = $secondary
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestFile -Encoding UTF8
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $stateDir 'last-backup.json') -Encoding UTF8
    Remove-Item -LiteralPath (Join-Path $stateDir 'last-backup-error.json') -Force -ErrorAction SilentlyContinue

    if ((Get-Date).DayOfWeek -eq 'Sunday') {
        Copy-BackupSet $dumpFile $manifestFile $configArchive $weeklyDir
    }
    if ((Get-Date).Day -eq 1) {
        Copy-BackupSet $dumpFile $manifestFile $configArchive $monthlyDir
    }
    if ($secondary) {
        Copy-BackupSet $dumpFile $manifestFile $configArchive $secondary
    }

    Remove-Expired $dailyDir $DailyRetentionDays
    Remove-Expired $weeklyDir $WeeklyRetentionDays
    Remove-Expired $monthlyDir $MonthlyRetentionDays

    Write-Host "备份完成并通过目录校验：$dumpFile" -ForegroundColor Green
    if ($secondary) { Write-Host "已复制到第二备份位置：$secondary" -ForegroundColor Green }
}
catch {
    Remove-Item -LiteralPath $tempDump -Force -ErrorAction SilentlyContinue
    [ordered]@{
        result = 'FAILED'
        failedAt = (Get-Date).ToUniversalTime().ToString('o')
        error = $_.Exception.Message
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stateDir 'last-backup-error.json') -Encoding UTF8
    throw
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $tempConfigDir -Recurse -Force -ErrorAction SilentlyContinue
}
