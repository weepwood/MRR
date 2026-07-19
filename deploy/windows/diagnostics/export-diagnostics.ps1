[CmdletBinding()]
param(
    [string]$Root = 'C:\MRR',
    [int]$LogTail = 500
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputDir = Join-Path $Root 'logs\diagnostics'
$tempDir = Join-Path $env:TEMP "mrr-diagnostics-$timestamp"
$archive = Join-Path $outputDir "MRR-Diagnostics-$timestamp.zip"
New-Item -ItemType Directory -Path $outputDir, $tempDir -Force | Out-Null

function Save-Command([string]$FileName, [scriptblock]$Command) {
    try {
        & $Command 2>&1 | Out-String | Set-Content -LiteralPath (Join-Path $tempDir $FileName) -Encoding UTF8
    }
    catch {
        $_ | Out-String | Set-Content -LiteralPath (Join-Path $tempDir $FileName) -Encoding UTF8
    }
}

function Copy-Tail([string]$Source, [string]$Destination) {
    if (Test-Path -LiteralPath $Source -PathType Leaf) {
        Get-Content -LiteralPath $Source -Tail $LogTail -Encoding UTF8 -ErrorAction SilentlyContinue |
            Set-Content -LiteralPath (Join-Path $tempDir $Destination) -Encoding UTF8
    }
}

try {
    $ctl = Join-Path $Root 'ops\mrrctl.ps1'
    if (Test-Path $ctl) {
        Save-Command 'mrr-status.txt' { & $ctl status -Root $Root }
        Save-Command 'mrr-doctor.txt' { & $ctl doctor -Root $Root }
    }
    Save-Command 'windows-services.txt' {
        Get-Service MRR-Backend, MRR-Gateway, postgresql* -ErrorAction SilentlyContinue | Format-Table -AutoSize
    }
    Save-Command 'system.txt' {
        Get-ComputerInfo | Select-Object WindowsProductName, WindowsVersion, OsBuildNumber, CsTotalPhysicalMemory
        Get-CimInstance Win32_OperatingSystem | Select-Object LastBootUpTime, FreePhysicalMemory, TotalVisibleMemorySize
    }
    Save-Command 'disks.txt' { Get-Volume | Select-Object DriveLetter, FileSystemLabel, Size, SizeRemaining | Format-Table -AutoSize }
    Save-Command 'ports.txt' {
        foreach ($port in 80, 18045, 18046, 5432) {
            Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        }
    }
    Save-Command 'health.json' {
        Invoke-RestMethod 'http://127.0.0.1:18046/actuator/health/readiness' -TimeoutSec 5 | ConvertTo-Json -Depth 8
    }

    $manifest = Join-Path $Root 'current\manifest.json'
    if (Test-Path $manifest) { Copy-Item -LiteralPath $manifest -Destination (Join-Path $tempDir 'manifest.json') }

    $prodConfig = Join-Path $Root 'config\application-prod.properties'
    if (Test-Path $prodConfig) {
        Get-Content $prodConfig -Encoding UTF8 | ForEach-Object {
            if ($_ -match '(?i)(password|secret|access-key|token)\s*=') {
                ($_ -replace '=.*$', '=[REDACTED]')
            } else { $_ }
        } | Set-Content -LiteralPath (Join-Path $tempDir 'application-prod-redacted.properties') -Encoding UTF8
    }

    Copy-Tail (Join-Path $Root 'logs\backend\mrr-error.log') 'backend-error-tail.log'
    Copy-Tail (Join-Path $Root 'logs\backend\img-api.log') 'backend-tail.log'
    Copy-Tail (Join-Path $Root 'logs\backend\gc.log') 'gc-tail.log'
    Copy-Tail (Join-Path $Root 'logs\nginx\error.log') 'nginx-error-tail.log'
    Copy-Tail (Join-Path $Root 'logs\service\mrr-backend.wrapper.log') 'backend-service-tail.log'
    Copy-Tail (Join-Path $Root 'logs\service\mrr-gateway.wrapper.log') 'gateway-service-tail.log'

    $backupState = Join-Path $Root 'state\backup'
    if (Test-Path $backupState) {
        Copy-Item -Path (Join-Path $backupState '*.json') -Destination $tempDir -Force -ErrorAction SilentlyContinue
    }

    Compress-Archive -Path (Join-Path $tempDir '*') -DestinationPath $archive -CompressionLevel Optimal -Force
    Write-Host "诊断包已生成：$archive" -ForegroundColor Green
    Write-Warning '诊断包可能包含内部 IP、用户名和错误上下文，请按敏感运维资料管理。'
}
finally {
    Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}
