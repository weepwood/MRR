[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)]
    [ValidateSet('status','start','stop','restart','maintenance','deploy','rollback','version','versions','logs','doctor')]
    [string]$Command,
    [Parameter(Position = 1)][string]$Target = 'all',
    [string]$Root = 'C:\MRR',
    [string]$Message = '系统维护中，请稍后再试。',
    [int]$Tail = 200,
    [int]$HealthTimeoutSeconds = 90,
    [int]$KeepReleases = 5,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$P = @{
    Current = Join-Path $Root 'current'; Previous = Join-Path $Root 'previous'
    Releases = Join-Path $Root 'releases'; Staging = Join-Path $Root 'staging'
    Maintenance = Join-Path $Root 'config\nginx\maintenance.inc'
    MaintenancePage = Join-Path $Root 'shared\maintenance.html'
    Nginx = Join-Path $Root 'runtime\nginx\nginx.exe'
    NginxHome = Join-Path $Root 'runtime\nginx'
    NginxConfig = Join-Path $Root 'config\nginx\nginx.conf'
    BackendLog = Join-Path $Root 'logs\backend\img-api.log'
    ServiceLogs = Join-Path $Root 'logs\service'
}
$S = @{ Backend = 'MRR-Backend'; Gateway = 'MRR-Gateway' }

function Assert-Admin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    $p = [Security.Principal.WindowsPrincipal]::new($id)
    if (-not $p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw '该操作需要以管理员身份运行 PowerShell。'
    }
}

function Get-LinkTarget([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    $item = Get-Item -LiteralPath $Path -Force
    if (-not ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { return $null }
    if ($item.PSObject.Properties.Name -contains 'Target' -and $item.Target) {
        return [string]($item.Target | Select-Object -First 1)
    }
    if ($item.PSObject.Properties.Name -contains 'LinkTarget') { return [string]$item.LinkTarget }
    return $null
}

function Set-Link([string]$Path, [string]$Destination) {
    if (-not (Test-Path -LiteralPath $Destination -PathType Container)) { throw "版本目录不存在：$Destination" }
    if (Test-Path -LiteralPath $Path) {
        $item = Get-Item -LiteralPath $Path -Force
        if (-not ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            if (@(Get-ChildItem -LiteralPath $Path -Force).Count -gt 0) { throw "拒绝覆盖非空普通目录：$Path" }
        }
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
    $out = cmd.exe /d /c "mklink /J `"$Path`" `"$Destination`"" 2>&1
    if ($LASTEXITCODE -ne 0) { throw "创建目录联接失败：$out" }
}

function Get-Manifest([string]$Path = $P.Current) {
    $file = Join-Path $Path 'manifest.json'
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { return $null }
    return Get-Content -LiteralPath $file -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Test-Health {
    try {
        $h = Invoke-RestMethod 'http://127.0.0.1:18046/actuator/health' -TimeoutSec 3
        return $h.status -eq 'UP'
    } catch { return $false }
}

function Wait-Health {
    $end = (Get-Date).AddSeconds($HealthTimeoutSeconds)
    do {
        if (Test-Health) { return $true }
        Start-Sleep 2
    } while ((Get-Date) -lt $end)
    return $false
}

function Invoke-Nginx([ValidateSet('test','reload','quit')][string]$Action) {
    if (-not (Test-Path -LiteralPath $P.Nginx -PathType Leaf)) { throw "找不到 Nginx：$($P.Nginx)" }
    $nginxArgs = @('-p', $P.NginxHome, '-c', $P.NginxConfig)
    if ($Action -eq 'test') { & $P.Nginx @nginxArgs '-t' }
    if ($Action -eq 'reload') {
        & $P.Nginx @nginxArgs '-t'; if ($LASTEXITCODE -ne 0) { throw 'Nginx 配置校验失败。' }
        & $P.Nginx @nginxArgs '-s' 'reload'
    }
    if ($Action -eq 'quit') { & $P.Nginx @nginxArgs '-s' 'quit' }
    if ($LASTEXITCODE -ne 0) { throw "Nginx 操作失败：$Action" }
}

function Set-Maintenance([bool]$Enabled, [string]$Text = $Message) {
    Assert-Admin
    New-Item -ItemType Directory -Path (Split-Path -Parent $P.Maintenance) -Force | Out-Null
    if ($Enabled) {
        Set-Content $P.Maintenance "return 503;`r`n" -Encoding ASCII
        if (Test-Path $P.MaintenancePage) {
            $safe = [Net.WebUtility]::HtmlEncode($Text)
            $html = Get-Content $P.MaintenancePage -Raw -Encoding UTF8
            $html = [regex]::Replace($html, '<p id="maintenance-message">.*?</p>', "<p id=`"maintenance-message`">$safe</p>")
            Set-Content $P.MaintenancePage $html -Encoding UTF8
        }
    } else {
        Set-Content $P.Maintenance "# maintenance disabled`r`n" -Encoding ASCII
    }
    $gateway = Get-Service $S.Gateway -ErrorAction SilentlyContinue
    if ($gateway -and $gateway.Status -eq 'Running') { Invoke-Nginx reload }
}

function Assert-Checksums([string]$Release) {
    $list = Join-Path $Release 'SHA256SUMS'
    if (-not (Test-Path $list)) { throw '发布包缺少 SHA256SUMS。' }
    $root = [IO.Path]::GetFullPath($Release).TrimEnd('\') + '\'
    foreach ($line in Get-Content $list -Encoding UTF8) {
        if ($line -notmatch '^([a-fA-F0-9]{64})\s+\*?(.+)$') { throw "无效校验行：$line" }
        $relative = $Matches[2].Trim().TrimStart('.', '/', '\').Replace('/', '\')
        $file = [IO.Path]::GetFullPath((Join-Path $Release $relative))
        if (-not $file.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) { throw "非法文件路径：$relative" }
        if (-not (Test-Path $file -PathType Leaf)) { throw "校验文件不存在：$relative" }
        if ((Get-FileHash $file -Algorithm SHA256).Hash -ne $Matches[1].ToUpperInvariant()) { throw "SHA-256 不匹配：$relative" }
    }
}

function Assert-Release([string]$Release) {
    foreach ($r in 'manifest.json','backend\mrr-backend.jar','frontend\index.html') {
        if (-not (Test-Path (Join-Path $Release $r))) { throw "发布包缺少：$r" }
    }
    $m = Get-Manifest $Release
    if (-not $m -or -not $m.productVersion -or -not $m.gitCommit) { throw 'manifest.json 缺少 productVersion 或 gitCommit。' }
    Assert-Checksums $Release
}

function Resolve-PackageRoot([string]$Path) {
    if (Test-Path (Join-Path $Path 'manifest.json')) { return $Path }
    $dirs = @(Get-ChildItem $Path -Directory)
    if ($dirs.Count -eq 1 -and (Test-Path (Join-Path $dirs[0].FullName 'manifest.json'))) { return $dirs[0].FullName }
    throw '发布包根目录中未找到 manifest.json。'
}

function Deploy([string]$Zip) {
    Assert-Admin
    if ($Zip -eq 'all' -or -not (Test-Path $Zip -PathType Leaf)) { throw '请提供存在的发布 ZIP 路径。' }
    New-Item -ItemType Directory -Path $P.Releases,$P.Staging -Force | Out-Null
    $stage = Join-Path $P.Staging ([guid]::NewGuid().ToString('N'))
    $old = Get-LinkTarget $P.Current; $switched = $false
    try {
        Expand-Archive $Zip $stage -Force
        $source = Resolve-PackageRoot $stage
        Assert-Release $source
        $m = Get-Manifest $source
        $commit = ([string]$m.gitCommit -replace '[^0-9A-Za-z]','')
        if ($commit.Length -gt 8) { $commit = $commit.Substring(0,8) }
        $name = (([string]$m.productVersion -replace '[^0-9A-Za-z._-]','-') + '-' + $commit)
        $release = Join-Path $P.Releases $name
        if (Test-Path $release) { throw "版本已存在：$name" }
        Move-Item $source $release
        Set-Maintenance $true "正在部署 $($m.productVersion)，请稍后再试。"
        Stop-Service $S.Backend -ErrorAction SilentlyContinue
        if ($old) { Set-Link $P.Previous $old }
        Set-Link $P.Current $release; $switched = $true
        Start-Service $S.Backend
        if (-not (Wait-Health)) { throw '新版本健康检查失败。' }
        $gateway = Get-Service $S.Gateway -ErrorAction SilentlyContinue
        if ($gateway -and $gateway.Status -eq 'Running') { Invoke-Nginx reload } else { Start-Service $S.Gateway }
        Set-Maintenance $false
        $protected = @((Get-LinkTarget $P.Current),(Get-LinkTarget $P.Previous))
        Get-ChildItem $P.Releases -Directory | Sort-Object LastWriteTime -Descending | Select-Object -Skip $KeepReleases | ForEach-Object {
            if ($protected -notcontains $_.FullName) { Remove-Item $_.FullName -Recurse -Force }
        }
        Write-Host "部署成功：$name" -ForegroundColor Green
    } catch {
        $err = $_
        if ($switched -and $old -and (Test-Path $old)) {
            Stop-Service $S.Backend -ErrorAction SilentlyContinue
            Set-Link $P.Current $old
            Start-Service $S.Backend
            if (Wait-Health) { Set-Maintenance $false } else { Write-Warning '原版本恢复后仍不健康，维护模式保持开启。' }
        }
        throw $err
    } finally {
        Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Resolve-Version([string]$Value) {
    if ($Value -in @('all','previous','')) {
        $previous = Get-LinkTarget $P.Previous
        if (-not $previous) { throw '没有 previous 版本。' }
        return $previous
    }
    $matches = @(Get-ChildItem $P.Releases -Directory | Where-Object { $_.Name -eq $Value -or $_.Name -like "$Value-*" })
    if ($matches.Count -ne 1) { throw "版本匹配数量不是 1：$Value" }
    return $matches[0].FullName
}

function Rollback([string]$Value) {
    Assert-Admin
    $old = Get-LinkTarget $P.Current
    if (-not $old) { throw 'current 不是受管理的目录联接。' }
    $currentManifest = Get-Manifest $P.Current
    if ($currentManifest -and $currentManifest.PSObject.Properties.Name -contains 'databaseBackwardCompatible') {
        if (-not [bool]$currentManifest.databaseBackwardCompatible -and -not $Force) {
            throw '当前版本声明数据库不向后兼容。完成数据库恢复后使用 -Force。'
        }
    }
    $release = Resolve-Version $Value
    Assert-Release $release
    if ([IO.Path]::GetFullPath($release) -eq [IO.Path]::GetFullPath($old)) { throw '目标已经是当前版本。' }
    Set-Maintenance $true '系统正在回滚，请稍后再试。'
    Stop-Service $S.Backend -ErrorAction SilentlyContinue
    Set-Link $P.Current $release
    try {
        Start-Service $S.Backend
        if (-not (Wait-Health)) { throw '回滚版本健康检查失败。' }
        Set-Link $P.Previous $old
        Invoke-Nginx reload
        Set-Maintenance $false
        Write-Host "回滚成功：$(Split-Path $release -Leaf)" -ForegroundColor Green
    } catch {
        Stop-Service $S.Backend -ErrorAction SilentlyContinue
        Set-Link $P.Current $old
        Start-Service $S.Backend
        if (Wait-Health) { Set-Maintenance $false }
        throw
    }
}

function Status {
    $m = Get-Manifest
    $backend = Get-Service $S.Backend -ErrorAction SilentlyContinue
    $gateway = Get-Service $S.Gateway -ErrorAction SilentlyContinue
    $maintenance = (Test-Path $P.Maintenance) -and ((Get-Content $P.Maintenance -Raw) -match 'return\s+503')
    $drive = Get-PSDrive ([IO.Path]::GetPathRoot($Root).Trim('\:')) -ErrorAction SilentlyContinue
    [pscustomobject]@{
        Version = if ($m) { $m.productVersion } else { 'unknown' }
        Commit = if ($m) { $m.gitCommit } else { 'unknown' }
        Current = Get-LinkTarget $P.Current
        BackendService = if ($backend) { $backend.Status } else { 'NotInstalled' }
        GatewayService = if ($gateway) { $gateway.Status } else { 'NotInstalled' }
        BackendHealth = if (Test-Health) { 'UP' } else { 'DOWN' }
        Maintenance = if ($maintenance) { 'ON' } else { 'OFF' }
        FreeDiskGB = if ($drive) { [math]::Round($drive.Free / 1GB,2) } else { $null }
    } | Format-List
}

function Assert-ServiceTarget([string]$Name) {
    if ($Name -notin @('backend','gateway','all')) { throw '服务目标可选：backend、gateway、all。' }
}

function Start-Mrr([string]$Name) {
    Assert-Admin
    Assert-ServiceTarget $Name
    if ($Name -in @('backend','all')) { Start-Service $S.Backend; if (-not (Wait-Health)) { throw '后端健康检查失败。' } }
    if ($Name -in @('gateway','all')) { Start-Service $S.Gateway }
}

function Stop-Mrr([string]$Name) {
    Assert-Admin
    Assert-ServiceTarget $Name
    if ($Name -eq 'all') { Set-Maintenance $true '系统正在停止服务。' }
    if ($Name -in @('backend','all')) { Stop-Service $S.Backend -ErrorAction SilentlyContinue }
    if ($Name -in @('gateway','all')) { try { Invoke-Nginx quit } catch {}; Stop-Service $S.Gateway -ErrorAction SilentlyContinue }
}

function Restart-Mrr([string]$Name) {
    Assert-Admin
    Assert-ServiceTarget $Name
    if ($Name -in @('backend','all')) {
        Set-Maintenance $true '系统正在重启，请稍后再试。'
        Restart-Service $S.Backend -Force
        if (-not (Wait-Health)) { throw '后端重启后健康检查失败，维护模式保持开启。' }
    }
    if ($Name -eq 'gateway') { Invoke-Nginx reload }
    elseif ($Name -eq 'all') {
        $gateway = Get-Service $S.Gateway -ErrorAction SilentlyContinue
        if ($gateway -and $gateway.Status -eq 'Running') { Invoke-Nginx reload } else { Start-Service $S.Gateway }
        Set-Maintenance $false
    } else { Set-Maintenance $false }
}

function Show-Versions {
    $current = Get-LinkTarget $P.Current; $previous = Get-LinkTarget $P.Previous
    Get-ChildItem $P.Releases -Directory -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | ForEach-Object {
        $m = Get-Manifest $_.FullName
        [pscustomobject]@{
            Role = if ($_.FullName -eq $current) {'current'} elseif ($_.FullName -eq $previous) {'previous'} else {''}
            Directory = $_.Name; Version = if ($m) {$m.productVersion} else {'unknown'}
            Commit = if ($m) {$m.gitCommit} else {'unknown'}; ModifiedAt = $_.LastWriteTime
        }
    } | Format-Table -AutoSize
}

function Doctor {
    Status
    Invoke-Nginx test
    $rows = foreach ($port in 80,18045,18046,5432) {
        $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        $ids = ($c | Select-Object -ExpandProperty OwningProcess -Unique -ErrorAction SilentlyContinue) -join ','
        [pscustomobject]@{ Port=$port; Listening=[bool]$c; ProcessId=$ids }
    }
    $rows | Format-Table -AutoSize
}

switch ($Command) {
    status { Status }
    start { Start-Mrr $Target }
    stop { Stop-Mrr $Target }
    restart { Restart-Mrr $Target }
    maintenance {
        if ($Target -eq 'on') { Set-Maintenance $true $Message }
        elseif ($Target -eq 'off') { Set-Maintenance $false }
        else { throw 'maintenance 后必须指定 on 或 off。' }
    }
    deploy { Deploy $Target }
    rollback { Rollback $Target }
    version { $m = Get-Manifest; if ($m) { $m | Format-List } else { Write-Host '没有当前版本信息。' } }
    versions { Show-Versions }
    logs {
        $file = if ($Target -eq 'backend') { $P.BackendLog } else { Join-Path $P.ServiceLogs "$Target.wrapper.log" }
        Get-Content $file -Tail $Tail -Encoding UTF8
    }
    doctor { Doctor }
}
