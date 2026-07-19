[CmdletBinding()]
param(
    [string]$BackupFile,
    [string]$Root = 'C:\MRR',
    [string]$PostgresBin,
    [PSCredential]$Credential,
    [string]$RestoreDatabase = "imgapi_restore_$(Get-Date -Format 'yyyyMMddHHmmss')",
    [switch]$KeepDatabase
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
    throw '没有找到可用于恢复演练的数据库备份。'
}

$config = Read-Properties (Join-Path $Root 'config\application-prod.properties')
$jdbcUrl = [string]$config['spring.datasource.url']
if ($jdbcUrl -notmatch '^jdbc:postgresql://(?<host>[^:/?]+)(:(?<port>\d+))?/(?<database>[^?]+)') {
    throw "无法解析 PostgreSQL JDBC URL：$jdbcUrl"
}
$hostName = $Matches.host
$port = if ($Matches.port) { [int]$Matches.port } else { 5432 }

if (-not $Credential) {
    $Credential = Get-Credential -UserName 'postgres' -Message '输入可创建临时数据库的 PostgreSQL 管理员凭据。凭据仅用于本次恢复演练。'
}
$dbUser = $Credential.UserName
$passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Credential.Password)
$dbPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr)

$pgBin = Resolve-PostgresBin $PostgresBin
$createdb = Join-Path $pgBin 'createdb.exe'
$dropdb = Join-Path $pgBin 'dropdb.exe'
$pgRestore = Join-Path $pgBin 'pg_restore.exe'
$psql = Join-Path $pgBin 'psql.exe'
$reportDir = Join-Path $Root 'backups\restore-drills'
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$reportFile = Join-Path $reportDir "restore-drill-$(Get-Date -Format 'yyyyMMdd-HHmmss').json"
$startedAt = Get-Date
$checks = [ordered]@{}
$created = $false
$env:PGPASSWORD = $dbPassword

try {
    & $pgRestore --list $BackupFile | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '备份目录校验失败。' }
    $checks.backupCatalog = 'PASS'

    & $createdb --host=$hostName --port=$port --username=$dbUser --maintenance-db=postgres --encoding=UTF8 $RestoreDatabase
    if ($LASTEXITCODE -ne 0) { throw "无法创建恢复演练数据库 $RestoreDatabase" }
    $created = $true

    & $pgRestore --host=$hostName --port=$port --username=$dbUser --dbname=$RestoreDatabase `
        --no-owner --no-acl --exit-on-error --jobs=2 $BackupFile
    if ($LASTEXITCODE -ne 0) { throw "pg_restore 失败，退出代码 $LASTEXITCODE" }
    $checks.restore = 'PASS'

    $validationSql = @'
SELECT json_build_object(
  'schema_exists', EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'app'),
  'mr_scan_rows', (SELECT COUNT(*) FROM app.mr_scan),
  'mr_statistics_rows', (SELECT COUNT(*) FROM app.mr_statistics),
  'access_log_rows', (SELECT COUNT(*) FROM app.access_log)
);
'@
    $validationOutput = $validationSql | & $psql --host=$hostName --port=$port --username=$dbUser `
        --dbname=$RestoreDatabase --tuples-only --no-align --set ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) { throw '恢复后的核心查询验证失败。' }
    $checks.coreQueries = 'PASS'

    $completedAt = Get-Date
    $report = [ordered]@{
        result = 'PASS'
        backupFile = (Resolve-Path $BackupFile).Path
        backupSha256 = (Get-FileHash -Algorithm SHA256 $BackupFile).Hash.ToLowerInvariant()
        restoreDatabase = $RestoreDatabase
        startedAt = $startedAt.ToUniversalTime().ToString('o')
        completedAt = $completedAt.ToUniversalTime().ToString('o')
        rtoSeconds = [math]::Round(($completedAt - $startedAt).TotalSeconds, 2)
        checks = $checks
        validation = (($validationOutput | Out-String).Trim() | ConvertFrom-Json)
        databaseKept = [bool]$KeepDatabase
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportFile -Encoding UTF8
    Write-Host "恢复演练通过：$reportFile" -ForegroundColor Green
}
catch {
    $completedAt = Get-Date
    [ordered]@{
        result = 'FAIL'
        backupFile = $BackupFile
        restoreDatabase = $RestoreDatabase
        startedAt = $startedAt.ToUniversalTime().ToString('o')
        completedAt = $completedAt.ToUniversalTime().ToString('o')
        rtoSeconds = [math]::Round(($completedAt - $startedAt).TotalSeconds, 2)
        checks = $checks
        error = $_.Exception.Message
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportFile -Encoding UTF8
    throw
}
finally {
    if ($created -and -not $KeepDatabase) {
        & $dropdb --host=$hostName --port=$port --username=$dbUser --maintenance-db=postgres --if-exists $RestoreDatabase | Out-Null
    }
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    $dbPassword = $null
}
