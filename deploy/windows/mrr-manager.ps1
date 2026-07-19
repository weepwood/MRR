[CmdletBinding()]
param([string]$Root = 'C:\MRR')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    $arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Root `"$Root`""
    Start-Process powershell.exe -Verb RunAs -ArgumentList $arguments
    exit
}

$ctl = Join-Path $Root 'ops\mrrctl.ps1'
$backup = Join-Path $Root 'ops\backup\backup-database.ps1'
$verify = Join-Path $Root 'ops\backup\verify-backup.ps1'
$restore = Join-Path $Root 'ops\backup\restore-drill.ps1'
$profile = Join-Path $Root 'ops\diagnostics\profile.ps1'
$diagnostics = Join-Path $Root 'ops\diagnostics\export-diagnostics.ps1'

function Pause-Mrr {
    Write-Host ''
    Read-Host '按 Enter 返回菜单' | Out-Null
}

function Invoke-MrrAction([scriptblock]$Action) {
    try {
        Write-Host ''
        & $Action
    }
    catch {
        Write-Host "操作失败：$($_.Exception.Message)" -ForegroundColor Red
    }
    Pause-Mrr
}

while ($true) {
    Clear-Host
    Write-Host '========================================' -ForegroundColor Cyan
    Write-Host '          MRR 单服务器管理器' -ForegroundColor Cyan
    Write-Host '========================================' -ForegroundColor Cyan
    Write-Host ' 1. 查看系统状态'
    Write-Host ' 2. 启动全部服务'
    Write-Host ' 3. 停止全部服务'
    Write-Host ' 4. 重启全部服务'
    Write-Host ' 5. 部署新版本 ZIP'
    Write-Host ' 6. 回滚上一版本'
    Write-Host ' 7. 立即备份数据库和配置'
    Write-Host ' 8. 验证最近一次备份'
    Write-Host ' 9. 查看最近错误日志'
    Write-Host '10. 导出诊断包'
    Write-Host '11. 开始 5 分钟性能采样'
    Write-Host '12. 查看性能采样状态'
    Write-Host '13. 执行完整恢复演练（高级）'
    Write-Host '14. 打开 MRR 网页'
    Write-Host ' 0. 退出'
    Write-Host ''
    $choice = Read-Host '请选择'

    switch ($choice) {
        '1' { Invoke-MrrAction { & $ctl status -Root $Root } }
        '2' { Invoke-MrrAction { & $ctl start all -Root $Root } }
        '3' { Invoke-MrrAction { & $ctl stop all -Root $Root } }
        '4' { Invoke-MrrAction { & $ctl restart all -Root $Root } }
        '5' {
            $zip = Read-Host '输入发布 ZIP 的完整路径'
            Invoke-MrrAction { & $ctl deploy $zip -Root $Root }
        }
        '6' { Invoke-MrrAction { & $ctl rollback previous -Root $Root } }
        '7' { Invoke-MrrAction { & $backup -Root $Root } }
        '8' { Invoke-MrrAction { & $verify -Root $Root } }
        '9' {
            Invoke-MrrAction {
                $errorLog = Join-Path $Root 'logs\backend\mrr-error.log'
                if (-not (Test-Path $errorLog)) { throw '尚未生成错误日志。' }
                Get-Content -LiteralPath $errorLog -Tail 200 -Encoding UTF8
            }
        }
        '10' { Invoke-MrrAction { & $diagnostics -Root $Root } }
        '11' { Invoke-MrrAction { & $profile start -Root $Root -DurationMinutes 5 } }
        '12' { Invoke-MrrAction { & $profile status -Root $Root } }
        '13' {
            Write-Warning '恢复演练会创建临时数据库，需要 PostgreSQL 管理员凭据。'
            $confirm = Read-Host '输入 RESTORE 确认继续'
            if ($confirm -eq 'RESTORE') { Invoke-MrrAction { & $restore -Root $Root } }
        }
        '14' { Start-Process 'http://127.0.0.1/' }
        '0' { return }
        default { Start-Sleep -Milliseconds 700 }
    }
}
