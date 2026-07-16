[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$CsvFile,

    [string]$HostName = '127.0.0.1',
    [int]$Port = 5432,
    [string]$Username = 'postgres',
    [string]$Database = 'mrr-app',
    [string]$PsqlPath = 'psql'
)

$ErrorActionPreference = 'Stop'

# 避免 Windows 控制台把 SQL 脚本中的中文显示为乱码。
chcp 65001 | Out-Null
$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

if (-not (Test-Path -LiteralPath $CsvFile -PathType Leaf)) {
    Write-Host ''
    Write-Host 'CSV 文件不存在：' -ForegroundColor Red
    Write-Host "  $CsvFile" -ForegroundColor Yellow
    Write-Host ''
    Write-Host '请先使用下面的命令查找实际文件路径：'
    Write-Host "  Get-ChildItem 'C:\BA_Service' -Recurse -Filter 'mr_archive_box_record.csv' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName"
    exit 2
}

$sqlFile = Join-Path $PSScriptRoot 'import-archive-box-record.sql'
if (-not (Test-Path -LiteralPath $sqlFile -PathType Leaf)) {
    throw "找不到 SQL 脚本：$sqlFile"
}

try {
    $psqlCommand = Get-Command $PsqlPath -ErrorAction Stop
} catch {
    throw "找不到 psql。请把 PostgreSQL bin 目录加入 PATH，或通过 -PsqlPath 指定 psql.exe 完整路径。"
}

$resolvedCsv = (Resolve-Path -LiteralPath $CsvFile).Path -replace '\\', '/'
$resolvedSql = (Resolve-Path -LiteralPath $sqlFile).Path

Write-Host "CSV 文件：$resolvedCsv"
Write-Host "目标数据库：$Username@$HostName`:$Port/$Database"
Write-Host ''

& $psqlCommand.Source `
    -X `
    -v ON_ERROR_STOP=1 `
    -h $HostName `
    -p $Port `
    -U $Username `
    -d $Database `
    -v "csv_file=$resolvedCsv" `
    -f $resolvedSql

if ($LASTEXITCODE -ne 0) {
    throw "导入失败，psql 退出码：$LASTEXITCODE"
}

Write-Host ''
Write-Host '导入执行完成。' -ForegroundColor Green
