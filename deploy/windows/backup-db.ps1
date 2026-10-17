[CmdletBinding()]
<#
.SYNOPSIS
    创建 MRR PostgreSQL 逻辑备份，并记录备份成功时间供部署就绪检查读取。

.DESCRIPTION
    1. 调用 pg_dump 生成 custom 格式备份（-Fc，支持 pg_restore 选择性恢复）；
    2. 计算 SHA256 并写入 .sha256 同名校验文件；
    3. 用 pg_restore --list 校验归档可读，并统计 TABLE DATA 条目数；
    4. 写出 .json 清单与 latest.json 指针，供 mrrctl.ps1 backup-status 使用；
    5. 把 lastSuccessfulBackupAt 等键写入 app.mr_system_settings，
       使既有 DeploymentReadinessService.checkBackupAge 生效；
    6. 按保留份数轮转旧备份，删除对应的 .sha256 与 .json。

    失败时不会留下半个备份文件，也不会刷新最后成功时间。

.PARAMETER Keep
    保留最近多少份备份，默认 14。

.PARAMETER SkipRecord
    只生成备份文件，不写入数据库中的备份时间（用于手工演练）。

.EXAMPLE
    .\backup-db.ps1 -Root C:\MRR -Keep 30
#>
param(
    [string]$Root,
    [string]$BackupRoot,
    [int]$Keep = 14,
    [string]$Database = 'imgapi',
    [string]$DbHost = '127.0.0.1',
    [int]$Port = 5432,
    [string]$Username = 'mrr_app',
    [string]$PgBin,
    [string]$SecretsFile,
    [switch]$SkipRecord,
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($Root)) {
    if ((Split-Path $scriptDir -Leaf) -ieq 'ops') {
        $Root = Split-Path $scriptDir -Parent
    }
    else {
        $Root = 'C:\MRR'
    }
}
$Root = [IO.Path]::GetFullPath($Root)
if ([string]::IsNullOrWhiteSpace($BackupRoot)) { $BackupRoot = Join-Path $Root 'backups\db' }
if ([string]::IsNullOrWhiteSpace($SecretsFile)) { $SecretsFile = Join-Path $Root 'secrets\application-secrets.properties' }

$Database = $Database -replace '[^A-Za-z0-9_]', ''
if ([string]::IsNullOrWhiteSpace($Database)) { throw '数据库名不合法。' }
$Keep = [Math]::Max(1, $Keep)

$logDir = Join-Path $Root 'logs\backup'
$logFile = Join-Path $logDir 'backup.log'

function Write-Log([string]$Message, [string]$Level = 'INFO') {
    $line = '{0} [{1}] {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Level, $Message
    if ($SelfTest) {
        Write-Host $line
        return
    }
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    Add-Content -LiteralPath $logFile -Value $line -Encoding UTF8
    Write-Host $line
}

function Resolve-PostgresTool([string]$ToolName) {
    $candidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($PgBin)) {
        $candidates.Add((Join-Path $PgBin "$ToolName.exe"))
    }
    $candidates.Add((Join-Path $Root "runtime\pgsql\bin\$ToolName.exe"))
    $command = Get-Command $ToolName -ErrorAction SilentlyContinue
    if ($command) { $candidates.Add($command.Source) }
    $installed = Get-ChildItem -Path 'C:\Program Files\PostgreSQL' -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending
    foreach ($dir in $installed) {
        $candidates.Add((Join-Path $dir.FullName "bin\$ToolName.exe"))
    }
    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw "找不到 $ToolName.exe。请安装 PostgreSQL 客户端，或使用 -PgBin 指定 bin 目录。"
}

function Read-DatabasePassword([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "找不到密钥文件：$Path"
    }
    $pattern = '^\s*spring\.datasource\.password\s*=\s*(.*)$'
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $match = [regex]::Match($line, $pattern)
        if (-not $match.Success) { continue }
        $value = $match.Groups[1].Value.Trim()
        if ([string]::IsNullOrWhiteSpace($value) -or $value.StartsWith('CHANGE_ME')) {
            throw "密钥文件中的 spring.datasource.password 仍是占位值，请先填写真实密码。"
        }
        return $value
    }
    throw "密钥文件中没有 spring.datasource.password。"
}

function New-Sha256File([string]$FilePath, [string]$Sha256) {
    $sidecar = "$FilePath.sha256"
    $name = Split-Path -Leaf $FilePath
    Set-Content -LiteralPath $sidecar -Value ("{0}  {1}" -f $Sha256.ToLowerInvariant(), $name) -Encoding ASCII
    return $sidecar
}

function Remove-OldBackups([string]$Directory, [string]$Prefix, [int]$Retain) {
    $removed = New-Object System.Collections.Generic.List[string]
    $files = Get-ChildItem -LiteralPath $Directory -Filter "$Prefix*.dump" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending
    $expired = @($files | Select-Object -Skip $Retain)
    foreach ($file in $expired) {
        foreach ($path in @($file.FullName, "$($file.FullName).sha256", ($file.FullName -replace '\.dump$', '.json'))) {
            if (Test-Path -LiteralPath $path -PathType Leaf) {
                Remove-Item -LiteralPath $path -Force
                $removed.Add($path)
            }
        }
    }
    return $removed
}

$dumpTool = $null
$restoreTool = $null
$psqlTool = $null
try {
    $dumpTool = Resolve-PostgresTool 'pg_dump'
    $restoreTool = Resolve-PostgresTool 'pg_restore'
    $psqlTool = Resolve-PostgresTool 'psql'
}
catch {
    if (-not $SelfTest) { throw }
    Write-Log $_.Exception.Message 'ERROR'
}

$password = $null
if (-not $SelfTest) { $password = Read-DatabasePassword $SecretsFile }

$pgDumpVersion = $null
if ($dumpTool) {
    # 合并 stderr 时必须临时放宽 ErrorActionPreference，否则 PowerShell 5.1
    # 会把原生命令的 stderr 当成终止错误。
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $pgDumpVersion = (& $dumpTool --version 2>&1 | Select-Object -First 1)
    }
    finally {
        $ErrorActionPreference = $savedPreference
    }
}

if ($SelfTest) {
    [pscustomobject]@{
        Root = $Root
        BackupRoot = $BackupRoot
        Database = $Database
        Host = $DbHost
        Port = $Port
        Username = $Username
        Keep = $Keep
        SecretsFile = $SecretsFile
        SecretsFileExists = (Test-Path -LiteralPath $SecretsFile -PathType Leaf)
        PgDump = $dumpTool
        PgRestore = $restoreTool
        Psql = $psqlTool
        PgDumpVersion = $pgDumpVersion
    } | ConvertTo-Json -Depth 4
    exit 0
}

New-Item -ItemType Directory -Path $BackupRoot -Force | Out-Null

$startedAt = Get-Date
$stamp = $startedAt.ToString('yyyyMMdd_HHmmss')
$fileName = '{0}_{1}.dump' -f $Database, $stamp
$dumpPath = Join-Path $BackupRoot $fileName
$manifestPath = Join-Path $BackupRoot ('{0}_{1}.json' -f $Database, $stamp)

Write-Log "开始备份：database=$Database host=$DbHost port=$Port target=$dumpPath"

$previousPassword = $env:PGPASSWORD
$env:PGPASSWORD = $password
try {
    $dumpArgs = @(
        '--host', $DbHost, '--port', "$Port", '--username', $Username, '--dbname', $Database,
        '--format=custom', '--compress=6', '--no-owner', '--no-privileges', '--no-password',
        '--file', $dumpPath
    )
    & $dumpTool @dumpArgs
    $dumpExit = $LASTEXITCODE
    if ($dumpExit -ne 0) {
        throw "pg_dump 执行失败，退出码 $dumpExit。"
    }
    if (-not (Test-Path -LiteralPath $dumpPath -PathType Leaf)) {
        throw 'pg_dump 未生成备份文件。'
    }

    $sizeBytes = (Get-Item -LiteralPath $dumpPath).Length
    if ($sizeBytes -le 0) {
        throw '备份文件大小为 0，视为失败。'
    }

    $sha256 = (Get-FileHash -LiteralPath $dumpPath -Algorithm SHA256).Hash
    $sidecar = New-Sha256File $dumpPath $sha256

    $archiveEntries = $null
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $archiveEntries = & $restoreTool --list $dumpPath 2>&1
        $restoreListExit = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedPreference
    }
    if ($restoreListExit -ne 0) {
        throw 'pg_restore --list 无法读取备份归档，备份不可用。'
    }
    $tableDataCount = @($archiveEntries | Where-Object { $_ -match 'TABLE DATA' }).Count
    if ($tableDataCount -le 0) {
        throw '备份归档中没有任何表数据，备份不可用。'
    }
}
catch {
    if (Test-Path -LiteralPath $dumpPath -PathType Leaf) {
        Remove-Item -LiteralPath $dumpPath -Force -ErrorAction SilentlyContinue
    }
    Write-Log ("备份失败：{0}" -f $_.Exception.Message) 'ERROR'
    throw
}
finally {
    if ($null -eq $previousPassword) {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
    else {
        $env:PGPASSWORD = $previousPassword
    }
}

$finishedAt = Get-Date

# 记录各表行数作为备份内容基线。恢复演练以这里为准，而不是与在线库比对——
# 否则业务一继续写入，"恢复库行数 < 在线库行数"就会被误判成恢复失败。
$countTables = @('mr_scan', 'mr_archive', 'mr_statistics', 'access_log', 'mr_archive_box_record')
$tableCounts = [ordered]@{}
$previousPassword = $env:PGPASSWORD
$env:PGPASSWORD = $password
try {
    foreach ($table in $countTables) {
        $value = & $psqlTool --host $DbHost --port "$Port" --username $Username --dbname $Database `
            --no-password --quiet --tuples-only --no-align `
            -c "select count(*) from app.$table" 2>$null
        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace("$value")) {
            $tableCounts[$table] = [long]("$value".Trim())
        }
        else {
            Write-Log "无法读取 $table 行数，备份清单将跳过该表" 'WARN'
        }
    }
}
finally {
    if ($null -eq $previousPassword) {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
    else {
        $env:PGPASSWORD = $previousPassword
    }
}

$manifest = [ordered]@{
    schemaVersion = 1
    database = $Database
    host = $DbHost
    port = $Port
    file = $fileName
    path = $dumpPath
    startedAt = $startedAt.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
    finishedAt = $finishedAt.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
    durationSeconds = [Math]::Round(($finishedAt - $startedAt).TotalSeconds, 1)
    sizeBytes = $sizeBytes
    sha256 = $sha256
    tableDataCount = $tableDataCount
    tableCounts = $tableCounts
    pgDumpVersion = "$pgDumpVersion".Trim()
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $BackupRoot 'latest.json') -Encoding UTF8

Write-Log ("备份完成：{0}，大小 {1} 字节，表数据项 {2}，SHA256 {3}" -f $fileName, $sizeBytes, $tableDataCount, $sha256)

if (-not $SkipRecord) {
    $recordedAt = $finishedAt.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
    $sql = @"
insert into app.mr_system_settings (setting_key, setting_value, description, updated_at, updated_by)
values ('lastSuccessfulBackupAt', '$recordedAt', '最近一次数据库备份成功时间（由 backup-db.ps1 写入）', now(), 'backup-db.ps1')
on conflict (setting_key) do update
set setting_value = excluded.setting_value, updated_at = now(), updated_by = excluded.updated_by;
insert into app.mr_system_settings (setting_key, setting_value, description, updated_at, updated_by)
values ('lastSuccessfulBackupFile', '$fileName', '最近一次数据库备份文件名', now(), 'backup-db.ps1')
on conflict (setting_key) do update
set setting_value = excluded.setting_value, updated_at = now(), updated_by = excluded.updated_by;
insert into app.mr_system_settings (setting_key, setting_value, description, updated_at, updated_by)
values ('lastSuccessfulBackupSizeBytes', '$sizeBytes', '最近一次数据库备份大小（字节）', now(), 'backup-db.ps1')
on conflict (setting_key) do update
set setting_value = excluded.setting_value, updated_at = now(), updated_by = excluded.updated_by;
"@
    $previousPassword = $env:PGPASSWORD
    $env:PGPASSWORD = $password
    try {
        & $psqlTool --host $DbHost --port "$Port" --username $Username --dbname $Database `
            --no-password --quiet -v ON_ERROR_STOP=1 -c $sql | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "写入备份时间失败，退出码 $LASTEXITCODE。备份文件已生成，但部署就绪检查不会看到本次备份。"
        }
        Write-Log "已更新 lastSuccessfulBackupAt=$recordedAt"
    }
    catch {
        Write-Log ("写入备份时间失败：{0}" -f $_.Exception.Message) 'ERROR'
        throw
    }
    finally {
        if ($null -eq $previousPassword) {
            Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
        }
        else {
            $env:PGPASSWORD = $previousPassword
        }
    }
}

$removed = Remove-OldBackups -Directory $BackupRoot -Prefix "$Database`_" -Retain $Keep
foreach ($path in $removed) { Write-Log "轮转删除：$path" }

[pscustomobject]@{
    ok = $true
    file = $dumpPath
    sidecar = $sidecar
    manifest = $manifestPath
    sizeBytes = $sizeBytes
    sha256 = $sha256
    tableDataCount = $tableDataCount
    rotated = $removed.Count
} | ConvertTo-Json -Depth 3
