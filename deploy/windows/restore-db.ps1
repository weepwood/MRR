[CmdletBinding()]
<#
.SYNOPSIS
    把 MRR 备份恢复到一个临时数据库，并比对关键表行数，作为可执行的恢复演练。

.DESCRIPTION
    备份"存在"不等于备份"可用"。本脚本把指定 dump 恢复到一个一次性数据库，
    逐表比对源库与恢复库的行数，任一行数不一致即失败。

    步骤：
    1. 校验 .sha256 同名校验文件，确认 dump 未被截断或篡改；
    2. 终止并删除同名目标库（-KeepDatabase 时保留旧库则直接失败）；
    3. 以超级用户创建空库并恢复（--no-owner --no-privileges）；
    4. 比对 mr_scan / mr_archive / mr_statistics / access_log / mr_archive_box_record；
    5. 默认恢复校验完成后删除目标库，避免占用磁盘。

    该脚本面向人工演练，需要超级用户口令。生产日常备份由 backup-db.ps1 负责。

.PARAMETER DumpFile
    dump 文件路径；省略时使用 <BackupRoot>\latest.json 指向的最新备份。

.PARAMETER KeepDatabase
    保留目标库以便人工检查（默认恢复成功即删除）。

.PARAMETER CompareWith
    manifest（默认）表示与备份清单记录的备份时行数比对；
    live 表示与当前在线库比对，只适用于停机窗口内紧接着备份执行的演练。
#>
param(
    [string]$Root,
    [string]$BackupRoot,
    [string]$DumpFile,
    [string]$Database = 'imgapi',
    [string]$TargetDatabase,
    [string]$DbHost = '127.0.0.1',
    [int]$Port = 5432,
    [ValidateSet('manifest', 'live')]
    [string]$CompareWith = 'manifest',
    [string]$Superuser = 'postgres',
    [string]$SuperuserPassword,
    [string]$PgBin,
    [switch]$KeepDatabase,
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
if ([string]::IsNullOrWhiteSpace($TargetDatabase)) { $TargetDatabase = '{0}_restorecheck' -f $Database }
$Database = $Database -replace '[^A-Za-z0-9_]', ''
$TargetDatabase = $TargetDatabase -replace '[^A-Za-z0-9_]', ''
if ([string]::IsNullOrWhiteSpace($TargetDatabase)) { throw '目标数据库名不合法。' }

$verifyTables = @('mr_scan', 'mr_archive', 'mr_statistics', 'access_log', 'mr_archive_box_record')

function Write-Step([string]$Message) {
    Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $Message)
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

function Resolve-DumpManifest([string]$Path) {
    $sibling = $Path -replace '\.dump$', '.json'
    if (Test-Path -LiteralPath $sibling -PathType Leaf) {
        return Get-Content -LiteralPath $sibling -Raw -Encoding UTF8 | ConvertFrom-Json
    }
    $latest = Join-Path $BackupRoot 'latest.json'
    if (Test-Path -LiteralPath $latest -PathType Leaf) {
        $pointer = Get-Content -LiteralPath $latest -Raw -Encoding UTF8 | ConvertFrom-Json
        if ([string]$pointer.path -eq $Path) { return $pointer }
    }
    return $null
}

function Resolve-DumpFile {
    if (-not [string]::IsNullOrWhiteSpace($DumpFile)) {
        if (-not (Test-Path -LiteralPath $DumpFile -PathType Leaf)) { throw "备份文件不存在：$DumpFile" }
        return (Resolve-Path -LiteralPath $DumpFile).Path
    }
    $latest = Join-Path $BackupRoot 'latest.json'
    if (-not (Test-Path -LiteralPath $latest -PathType Leaf)) {
        throw "没有 latest.json，无法确定最新备份；请显式指定 -DumpFile。"
    }
    $pointer = Get-Content -LiteralPath $latest -Raw -Encoding UTF8 | ConvertFrom-Json
    $path = [string]$pointer.path
    if ([string]::IsNullOrWhiteSpace($path) -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "latest.json 指向的备份不存在：$path"
    }
    return (Resolve-Path -LiteralPath $path).Path
}

function Assert-DumpIntegrity([string]$Path) {
    $sidecar = "$Path.sha256"
    if (-not (Test-Path -LiteralPath $sidecar -PathType Leaf)) {
        throw "缺少校验文件：$sidecar，拒绝恢复未校验的备份。"
    }
    $expected = ((Get-Content -LiteralPath $sidecar -TotalCount 1) -split '\s+')[0]
    if ([string]::IsNullOrWhiteSpace($expected)) { throw "校验文件内容为空：$sidecar" }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    if (-not $actual.Equals($expected, [StringComparison]::OrdinalIgnoreCase)) {
        throw "SHA256 校验失败：期望 $expected，实际 $actual。备份可能已被截断或篡改。"
    }
    return $actual
}

function Invoke-Psql([string]$DbName, [string]$Sql) {
    $output = & $psqlTool --host $DbHost --port "$Port" --username $Superuser --dbname $DbName `
        --no-password --quiet --tuples-only --no-align -v ON_ERROR_STOP=1 -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql 执行失败：$Sql"
    }
    return $output
}

function Get-TableCount([string]$DbName, [string]$Table) {
    $value = Invoke-Psql $DbName "select count(*) from app.$Table"
    $text = "$value".Trim()
    if ([string]::IsNullOrWhiteSpace($text)) { return 0L }
    return [long]$text
}

$psqlTool = $null
$restoreTool = $null
$createDbTool = $null
$dropDbTool = $null
try {
    $psqlTool = Resolve-PostgresTool 'psql'
    $restoreTool = Resolve-PostgresTool 'pg_restore'
    $createDbTool = Resolve-PostgresTool 'createdb'
    $dropDbTool = Resolve-PostgresTool 'dropdb'
}
catch {
    if (-not $SelfTest) { throw }
    Write-Step $_.Exception.Message
}

if ($SelfTest) {
    [pscustomobject]@{
        Root = $Root
        BackupRoot = $BackupRoot
        Database = $Database
        TargetDatabase = $TargetDatabase
        Host = $DbHost
        Port = $Port
        CompareWith = $CompareWith
        Superuser = $Superuser
        Psql = $psqlTool
        PgRestore = $restoreTool
        Createdb = $createDbTool
        Dropdb = $dropDbTool
        VerifyTables = $verifyTables
    } | ConvertTo-Json -Depth 4
    exit 0
}

if ([string]::IsNullOrWhiteSpace($SuperuserPassword)) {
    $secure = Read-Host -Prompt "请输入 PostgreSQL 超级用户 $Superuser 的口令" -AsSecureString
    $SuperuserPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    )
}
if ([string]::IsNullOrWhiteSpace($SuperuserPassword)) { throw '必须提供超级用户口令才能执行恢复演练。' }

$dump = Resolve-DumpFile
$sha256 = Assert-DumpIntegrity $dump
Write-Step "备份校验通过：$dump"

# 行数基线优先取自备份清单：恢复演练通常在生产运行中进行，
# 在线库在备份之后还会继续写入，拿实时行数比对必然误报。
$manifest = Resolve-DumpManifest $dump
$baseline = [ordered]@{}
$compareAgainstLive = $CompareWith -eq 'live'
if (-not $compareAgainstLive -and $null -ne $manifest) {
    $countsProperty = $manifest.PSObject.Properties['tableCounts']
    if ($null -ne $countsProperty -and $null -ne $countsProperty.Value) {
        foreach ($table in $verifyTables) {
            $entry = $countsProperty.Value.PSObject.Properties[$table]
            if ($null -ne $entry -and $null -ne $entry.Value) {
                $baseline[$table] = [long]$entry.Value
            }
        }
    }
}
if (-not $compareAgainstLive -and $baseline.Count -eq 0) {
    Write-Warning '备份清单中没有行数基线（旧版本备份），回退为与在线库比对。'
    $compareAgainstLive = $true
}

$previousPassword = $env:PGPASSWORD
$env:PGPASSWORD = $SuperuserPassword
try {
    if ($compareAgainstLive) {
        Write-Step "读取在线库 $Database 的行数基线（仅适用于停机窗口内的演练）"
        foreach ($table in $verifyTables) {
            $baseline[$table] = Get-TableCount $Database $table
        }
    }
    else {
        Write-Step ("使用备份清单行数基线（{0} 张表，备份于 {1}）" -f $baseline.Count, $manifest.finishedAt)
    }

    $exists = (Invoke-Psql 'postgres' "select count(*) from pg_database where datname = '$TargetDatabase'").Trim()
    if ($exists -ne '0') {
        if ($KeepDatabase) {
            throw "目标库 $TargetDatabase 已存在且指定了 -KeepDatabase，请先手工处理。"
        }
        Write-Step "删除已存在的目标库 $TargetDatabase"
        Invoke-Psql 'postgres' "select pg_terminate_backend(pid) from pg_stat_activity where datname = '$TargetDatabase' and pid <> pg_backend_pid()" | Out-Null
        & $dropDbTool --host $DbHost --port "$Port" --username $Superuser --no-password --if-exists $TargetDatabase
        if ($LASTEXITCODE -ne 0) { throw "删除目标库失败：$TargetDatabase" }
    }

    Write-Step "创建目标库 $TargetDatabase"
    & $createDbTool --host $DbHost --port "$Port" --username $Superuser --no-password -T template0 $TargetDatabase
    if ($LASTEXITCODE -ne 0) { throw "创建目标库失败：$TargetDatabase" }

    Write-Step '开始恢复'
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $restoreTool --host $DbHost --port "$Port" --username $Superuser --dbname $TargetDatabase `
            --no-owner --no-privileges --no-password $dump 2>&1 | ForEach-Object { Write-Verbose "$_" }
        $restoreExit = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedPreference
    }
    if ($restoreExit -ne 0) {
        throw "pg_restore 返回 $restoreExit，恢复未完全成功。请检查上面的错误输出。"
    }

    Write-Step '比对关键表行数'
    $rows = foreach ($table in $verifyTables) {
        if (-not $baseline.Contains($table)) {
            [pscustomobject]@{
                Table = $table
                Baseline = 'n/a'
                Restored = (Get-TableCount $TargetDatabase $table)
                Match = $true
            }
            continue
        }
        $targetCount = Get-TableCount $TargetDatabase $table
        [pscustomobject]@{
            Table = $table
            Baseline = $baseline[$table]
            Restored = $targetCount
            Match = ($baseline[$table] -eq $targetCount)
        }
    }
    $rows | Format-Table -AutoSize | Out-String | Write-Host

    $mismatch = @($rows | Where-Object { -not $_.Match })
    if ($mismatch.Count -gt 0) {
        throw ("恢复校验失败：{0} 张表行数不一致（{1}）。" -f $mismatch.Count, (($mismatch | ForEach-Object { $_.Table }) -join ', '))
    }
    Write-Step '恢复演练通过：所有准入表行数一致'
}
finally {
    if ($null -eq $previousPassword) {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
    else {
        $env:PGPASSWORD = $previousPassword
    }
}

if (-not $KeepDatabase) {
    $env:PGPASSWORD = $SuperuserPassword
    try {
        Write-Step "删除演练库 $TargetDatabase"
        Invoke-Psql 'postgres' "select pg_terminate_backend(pid) from pg_stat_activity where datname = '$TargetDatabase' and pid <> pg_backend_pid()" | Out-Null
        & $dropDbTool --host $DbHost --port "$Port" --username $Superuser --no-password --if-exists $TargetDatabase
        if ($LASTEXITCODE -ne 0) { throw "删除演练库失败：$TargetDatabase" }
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

[pscustomobject]@{
    ok = $true
    dump = $dump
    sha256 = $sha256
    targetDatabase = $TargetDatabase
    kept = [bool]$KeepDatabase
} | ConvertTo-Json -Depth 3
