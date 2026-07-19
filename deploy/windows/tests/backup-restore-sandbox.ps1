[CmdletBinding()]
param(
    [string]$SandboxRoot = (Join-Path ([IO.Path]::GetTempPath()) "mrr-windows-test-$([guid]::NewGuid().ToString('N'))"),
    [switch]$KeepSandbox
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$windowsRoot = Split-Path -Parent $PSScriptRoot
$postgresBin = Join-Path $SandboxRoot 'fake-postgresql\bin'
$mrrRoot = Join-Path $SandboxRoot 'mrr'
$fakeLog = Join-Path $SandboxRoot 'fake-postgresql.log'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "断言失败：$Message" }
}

function New-FakePostgresTools {
    New-Item -ItemType Directory -Path $postgresBin -Force | Out-Null
    $source = @'
using System;
using System.Diagnostics;
using System.IO;
using System.Linq;

public static class FakePostgresTool
{
    public static int Main(string[] args)
    {
        var tool = Path.GetFileNameWithoutExtension(Process.GetCurrentProcess().MainModule.FileName).ToLowerInvariant();
        var log = Environment.GetEnvironmentVariable("MRR_FAKE_PG_LOG");
        if (!string.IsNullOrEmpty(log)) File.AppendAllText(log, tool + " " + string.Join(" ", args) + Environment.NewLine);

        if (tool == "pg_dump")
        {
            var output = args.FirstOrDefault(value => value.StartsWith("--file=", StringComparison.Ordinal));
            if (output == null) return 2;
            File.WriteAllText(output.Substring(7), "MRR_FAKE_CUSTOM_DUMP");
            return 0;
        }
        if (tool == "pg_restore")
        {
            if (args.Contains("--list")) Console.WriteLine("1; 0 0 TABLE app mr_scan postgres");
            return 0;
        }
        if (tool == "psql")
        {
            Console.WriteLine("{\"schema_exists\":true,\"mr_scan_rows\":3,\"mr_statistics_rows\":2,\"access_log_rows\":1}");
            return 0;
        }
        return tool == "createdb" || tool == "dropdb" ? 0 : 3;
    }
}
'@
    $compiled = Join-Path $postgresBin 'fake-postgres.exe'
    $sourceFile = Join-Path $postgresBin 'fake-postgres.cs'
    Set-Content -LiteralPath $sourceFile -Value $source -Encoding UTF8
    $compiler = Get-ChildItem (Join-Path $env:WINDIR 'Microsoft.NET\Framework64') -Filter csc.exe -Recurse |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    if (-not $compiler) { throw '未找到 Windows .NET Framework C# 编译器，无法创建 PostgreSQL 沙箱工具。' }
    & $compiler /nologo /target:exe "/out:$compiled" $sourceFile
    if ($LASTEXITCODE -ne 0) { throw "创建 PostgreSQL 沙箱工具失败，退出代码 $LASTEXITCODE" }
    foreach ($name in 'pg_dump.exe', 'pg_restore.exe', 'createdb.exe', 'dropdb.exe', 'psql.exe') {
        Copy-Item -LiteralPath $compiled -Destination (Join-Path $postgresBin $name)
    }
    Remove-Item -LiteralPath $compiled, $sourceFile
}

function New-SandboxConfiguration {
    $configDir = Join-Path $mrrRoot 'config'
    $secretDir = Join-Path $mrrRoot 'secrets'
    $nginxDir = Join-Path $configDir 'nginx'
    New-Item -ItemType Directory -Path $configDir, $secretDir, $nginxDir -Force | Out-Null
    @'
spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/imgapi?currentSchema=app
spring.datasource.username=mrr_app
spring.datasource.password=SHOULD_BE_REDACTED
app.public-setting=kept
'@ | Set-Content -LiteralPath (Join-Path $configDir 'application-prod.properties') -Encoding UTF8
    @'
spring.datasource.password=SANDBOX_DATABASE_PASSWORD
JWT_SECRET_KEY=SANDBOX_JWT_SECRET
'@ | Set-Content -LiteralPath (Join-Path $secretDir 'application-secrets.properties') -Encoding UTF8
    'events {}' | Set-Content -LiteralPath (Join-Path $nginxDir 'nginx.conf') -Encoding UTF8
    'PRIVATE KEY' | Set-Content -LiteralPath (Join-Path $nginxDir 'server.key') -Encoding UTF8
}

function Test-RuntimeManifest {
    $runtime = Join-Path $SandboxRoot 'runtime'
    $contents = [ordered]@{
        'jdk/bin/java.exe' = 'fake-java'
        'jdk/bin/jcmd.exe' = 'fake-jcmd'
        'nginx/nginx.exe' = 'fake-nginx'
        'nginx/conf/mime.types' = 'text/plain text'
        'winsw/WinSW-x64.exe' = 'fake-winsw'
    }
    $hashes = [ordered]@{}
    foreach ($entry in $contents.GetEnumerator()) {
        $path = Join-Path $runtime $entry.Key
        New-Item -ItemType Directory -Path (Split-Path $path -Parent) -Force | Out-Null
        Set-Content -LiteralPath $path -Value $entry.Value -Encoding ASCII -NoNewline
        $hashes[$entry.Key] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    [ordered]@{ architecture = 'windows-x64'; files = $hashes } |
        ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath (Join-Path $runtime 'runtime-manifest.json') -Encoding UTF8

    & (Join-Path $windowsRoot 'runtime\verify-runtime.ps1') -RuntimeRoot $runtime
    Add-Content -LiteralPath (Join-Path $runtime 'nginx\nginx.exe') -Value 'tampered' -Encoding ASCII
    $failed = $false
    try { & (Join-Path $windowsRoot 'runtime\verify-runtime.ps1') -RuntimeRoot $runtime }
    catch { $failed = $_.Exception.Message -match 'SHA-256 不匹配' }
    Assert-True $failed '运行时摘要校验必须拒绝被篡改的可执行文件'
}

try {
    New-Item -ItemType Directory -Path $SandboxRoot -Force | Out-Null
    New-FakePostgresTools
    New-SandboxConfiguration
    $env:MRR_FAKE_PG_LOG = $fakeLog

    & (Join-Path $windowsRoot 'backup\backup-database.ps1') -Root $mrrRoot -PostgresBin $postgresBin
    Assert-True (-not (Test-Path Env:PGPASSWORD)) '备份完成后必须清除 PGPASSWORD'

    $dump = Get-ChildItem (Join-Path $mrrRoot 'backups\postgresql\daily') -Filter '*.dump' -File | Select-Object -First 1
    Assert-True ([bool]$dump) '备份脚本必须生成数据库 dump'
    & (Join-Path $windowsRoot 'backup\verify-backup.ps1') -Root $mrrRoot -BackupFile $dump.FullName -PostgresBin $postgresBin

    $manifest = Get-Content -LiteralPath "$($dump.FullName).json" -Raw -Encoding UTF8 | ConvertFrom-Json
    $archivePath = Join-Path $dump.DirectoryName ([string]$manifest.configFile)
    $extractPath = Join-Path $SandboxRoot 'config-archive'
    Expand-Archive -LiteralPath $archivePath -DestinationPath $extractPath
    $archivedConfig = Get-Content -LiteralPath (Join-Path $extractPath 'config\application-prod.properties') -Raw -Encoding UTF8
    Assert-True ($archivedConfig -notmatch 'SHOULD_BE_REDACTED|SANDBOX_DATABASE_PASSWORD|SANDBOX_JWT_SECRET') '配置包不得泄漏敏感值'
    Assert-True ($archivedConfig -match 'spring.datasource.password=\[REDACTED\]') '敏感属性必须被替换为 REDACTED'
    Assert-True (-not (Test-Path (Join-Path $extractPath 'nginx-conf\server.key'))) '配置包不得包含私钥文件'

    $securePassword = ConvertTo-SecureString 'sandbox-admin-password' -AsPlainText -Force
    $credential = [PSCredential]::new('postgres', $securePassword)
    & (Join-Path $windowsRoot 'backup\restore-drill.ps1') -Root $mrrRoot -BackupFile $dump.FullName `
        -PostgresBin $postgresBin -Credential $credential -RestoreDatabase 'imgapi_restore_sandbox'
    Assert-True (-not (Test-Path Env:PGPASSWORD)) '恢复演练完成后必须清除 PGPASSWORD'

    $report = Get-ChildItem (Join-Path $mrrRoot 'backups\restore-drills') -Filter '*.json' -File |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 |
        Get-Content -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-True ($report.result -eq 'PASS') '恢复演练报告必须为 PASS'
    Assert-True ([bool]$report.validation.schema_exists) '恢复后的 app schema 校验必须通过'
    $calls = Get-Content -LiteralPath $fakeLog -Raw -Encoding UTF8
    Assert-True ($calls -match '(?m)^createdb ') '恢复演练必须创建隔离数据库'
    Assert-True ($calls -match '(?m)^dropdb ') '恢复演练必须清理隔离数据库'

    Test-RuntimeManifest
    Write-Host "Windows 备份、验证、恢复与运行时摘要沙箱测试通过：$SandboxRoot" -ForegroundColor Green
}
finally {
    Remove-Item Env:MRR_FAKE_PG_LOG -ErrorAction SilentlyContinue
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    if (-not $KeepSandbox -and (Test-Path -LiteralPath $SandboxRoot)) {
        Remove-Item -LiteralPath $SandboxRoot -Recurse -Force
    }
}
