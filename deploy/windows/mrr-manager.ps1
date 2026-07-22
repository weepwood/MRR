[CmdletBinding()]
param(
    [string]$Root,
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Test-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

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

if (-not $SelfTest -and -not (Test-Administrator)) {
    $escapedScript = $PSCommandPath.Replace('"', '\"')
    $escapedRoot = $Root.Replace('"', '\"')
    $arguments = "-NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File `"$escapedScript`" -Root `"$escapedRoot`""
    Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -Verb RunAs -WindowStyle Hidden
    exit
}

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName Microsoft.VisualBasic
[System.Windows.Forms.Application]::EnableVisualStyles()

$mrrCtl = Join-Path $Root 'ops\mrrctl.ps1'
$nginxCtl = Join-Path $Root 'ops\nginxctl.ps1'
if (-not (Test-Path -LiteralPath $mrrCtl -PathType Leaf)) {
    $mrrCtl = Join-Path $scriptDir 'mrrctl.ps1'
}
if (-not (Test-Path -LiteralPath $nginxCtl -PathType Leaf)) {
    $nginxCtl = Join-Path $scriptDir 'nginxctl.ps1'
}

if ($SelfTest) {
    if (-not (Test-Path -LiteralPath $mrrCtl -PathType Leaf)) { throw "找不到 MRR 控制脚本：$mrrCtl" }
    if (-not (Test-Path -LiteralPath $nginxCtl -PathType Leaf)) { throw "找不到 Nginx 控制脚本：$nginxCtl" }
    [pscustomobject]@{
        Title = 'MRR 一键管理中心'
        PowerShellVersion = [string]$PSVersionTable.PSVersion
        PowerShellEdition = [string]$PSVersionTable.PSEdition
        MrrControl = $mrrCtl
        NginxControl = $nginxCtl
        Root = $Root
    } | ConvertTo-Json -Depth 3
    exit 0
}

$manifestPath = Join-Path $Root 'current\manifest.json'
$maintenancePath = Join-Path $Root 'config\nginx\maintenance.inc'
$logsPath = Join-Path $Root 'logs'
$configPath = Join-Path $Root 'config'
$packagesPath = Join-Path $Root 'packages'

$script:activeProcess = $null
$script:activeDescription = ''
$script:actionButtons = New-Object System.Collections.Generic.List[System.Windows.Forms.Button]

function Get-ServiceStateText {
    param([Parameter(Mandatory)][string]$Name)

    $service = Get-Service -Name $Name -ErrorAction SilentlyContinue
    if (-not $service) { return '未安装' }
    switch ([string]$service.Status) {
        'Running' { return '运行中' }
        'Stopped' { return '已停止' }
        'StartPending' { return '正在启动' }
        'StopPending' { return '正在停止' }
        default { return [string]$service.Status }
    }
}

function Get-DashboardSnapshot {
    $version = '未知'
    $commit = '未知'
    if (Test-Path -LiteralPath $manifestPath -PathType Leaf) {
        try {
            $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
            if ($manifest.productVersion) { $version = 'v' + [string]$manifest.productVersion }
            if ($manifest.gitCommit) {
                $commit = [string]$manifest.gitCommit
                if ($commit.Length -gt 8) { $commit = $commit.Substring(0, 8) }
            }
        }
        catch {
            $version = '清单异常'
            $commit = '清单异常'
        }
    }

    $health = 'DOWN'
    try {
        $response = Invoke-RestMethod 'http://127.0.0.1:18046/actuator/health' -TimeoutSec 2
        if ([string]$response.status -eq 'UP') { $health = 'UP' }
    }
    catch {}

    $maintenance = '关闭'
    if (Test-Path -LiteralPath $maintenancePath -PathType Leaf) {
        try {
            if ((Get-Content -LiteralPath $maintenancePath -Raw -Encoding UTF8) -match 'return\s+503') {
                $maintenance = '开启'
            }
        }
        catch {}
    }

    $freeDisk = '未知'
    try {
        $driveName = [IO.Path]::GetPathRoot($Root).TrimEnd('\').TrimEnd(':')
        $drive = Get-PSDrive -Name $driveName -ErrorAction Stop
        $freeDisk = ('{0:N1} GB' -f ($drive.Free / 1GB))
    }
    catch {}

    return [pscustomobject]@{
        Version = $version
        Commit = $commit
        Backend = Get-ServiceStateText 'MRR-Backend'
        Gateway = Get-ServiceStateText 'MRR-Gateway'
        Health = $health
        Maintenance = $maintenance
        FreeDisk = $freeDisk
    }
}

function ConvertTo-PowerShellLiteral {
    param([AllowEmptyString()][string]$Value)
    return "'" + $Value.Replace("'", "''") + "'"
}

function New-EncodedInvocation {
    param(
        [Parameter(Mandatory)][string]$ScriptPath,
        [Parameter(Mandatory)][string[]]$Arguments
    )

    $literalArguments = @($Arguments | ForEach-Object { ConvertTo-PowerShellLiteral ([string]$_) }) -join ', '
    $scriptLiteral = ConvertTo-PowerShellLiteral $ScriptPath
    $command = @"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
`$invokeArguments = @($literalArguments)
try {
    & $scriptLiteral @invokeArguments
    if (-not `$?) { exit 1 }
    exit 0
}
catch {
    Write-Error (`$_ | Out-String)
    exit 1
}
"@
    return [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
}

$form = New-Object System.Windows.Forms.Form
$form.Text = 'MRR 一键管理中心'
$form.StartPosition = 'CenterScreen'
$form.Size = New-Object System.Drawing.Size(1040, 760)
$form.MinimumSize = New-Object System.Drawing.Size(960, 700)
$form.BackColor = [System.Drawing.Color]::FromArgb(245, 247, 250)
$form.Font = New-Object System.Drawing.Font('Microsoft YaHei UI', 9)

$header = New-Object System.Windows.Forms.Panel
$header.Dock = 'Top'
$header.Height = 74
$header.BackColor = [System.Drawing.Color]::FromArgb(31, 41, 55)
$form.Controls.Add($header)

$titleLabel = New-Object System.Windows.Forms.Label
$titleLabel.Text = 'MRR 一键管理中心'
$titleLabel.ForeColor = [System.Drawing.Color]::White
$titleLabel.Font = New-Object System.Drawing.Font('Microsoft YaHei UI', 18, [System.Drawing.FontStyle]::Bold)
$titleLabel.AutoSize = $true
$titleLabel.Location = New-Object System.Drawing.Point(22, 13)
$header.Controls.Add($titleLabel)

$rootLabel = New-Object System.Windows.Forms.Label
$rootLabel.Text = "安装目录：$Root"
$rootLabel.ForeColor = [System.Drawing.Color]::Gainsboro
$rootLabel.AutoSize = $true
$rootLabel.Location = New-Object System.Drawing.Point(25, 48)
$header.Controls.Add($rootLabel)

$statusGroup = New-Object System.Windows.Forms.GroupBox
$statusGroup.Text = '运行状态'
$statusGroup.Location = New-Object System.Drawing.Point(18, 86)
$statusGroup.Size = New-Object System.Drawing.Size(986, 130)
$statusGroup.Anchor = 'Top,Left,Right'
$form.Controls.Add($statusGroup)

$statusDefinitions = @(
    @{ Key = 'Version'; Title = '产品版本'; X = 18 },
    @{ Key = 'Backend'; Title = '后端服务'; X = 178 },
    @{ Key = 'Gateway'; Title = 'Nginx 网关'; X = 338 },
    @{ Key = 'Health'; Title = '后端健康'; X = 498 },
    @{ Key = 'Maintenance'; Title = '维护模式'; X = 658 },
    @{ Key = 'FreeDisk'; Title = '磁盘剩余'; X = 818 }
)
$statusValueLabels = @{}
foreach ($definition in $statusDefinitions) {
    $caption = New-Object System.Windows.Forms.Label
    $caption.Text = $definition.Title
    $caption.ForeColor = [System.Drawing.Color]::DimGray
    $caption.AutoSize = $true
    $caption.Location = New-Object System.Drawing.Point($definition.X, 29)
    $statusGroup.Controls.Add($caption)

    $value = New-Object System.Windows.Forms.Label
    $value.Text = '读取中...'
    $value.Font = New-Object System.Drawing.Font('Microsoft YaHei UI', 12, [System.Drawing.FontStyle]::Bold)
    $value.AutoSize = $true
    $value.Location = New-Object System.Drawing.Point($definition.X, 57)
    $statusGroup.Controls.Add($value)
    $statusValueLabels[$definition.Key] = $value
}

$commitCaption = New-Object System.Windows.Forms.Label
$commitCaption.Text = 'Commit：'
$commitCaption.ForeColor = [System.Drawing.Color]::DimGray
$commitCaption.AutoSize = $true
$commitCaption.Location = New-Object System.Drawing.Point(18, 95)
$statusGroup.Controls.Add($commitCaption)

$commitValue = New-Object System.Windows.Forms.Label
$commitValue.Text = '未知'
$commitValue.AutoSize = $true
$commitValue.Location = New-Object System.Drawing.Point(75, 95)
$statusGroup.Controls.Add($commitValue)

$actionsGroup = New-Object System.Windows.Forms.GroupBox
$actionsGroup.Text = '常用操作'
$actionsGroup.Location = New-Object System.Drawing.Point(18, 226)
$actionsGroup.Size = New-Object System.Drawing.Size(986, 245)
$actionsGroup.Anchor = 'Top,Left,Right'
$form.Controls.Add($actionsGroup)

$flow = New-Object System.Windows.Forms.FlowLayoutPanel
$flow.Dock = 'Fill'
$flow.Padding = New-Object System.Windows.Forms.Padding(12, 16, 12, 10)
$flow.AutoScroll = $true
$flow.WrapContents = $true
$actionsGroup.Controls.Add($flow)

$outputGroup = New-Object System.Windows.Forms.GroupBox
$outputGroup.Text = '操作输出'
$outputGroup.Location = New-Object System.Drawing.Point(18, 481)
$outputGroup.Size = New-Object System.Drawing.Size(986, 205)
$outputGroup.Anchor = 'Top,Bottom,Left,Right'
$form.Controls.Add($outputGroup)

$outputBox = New-Object System.Windows.Forms.TextBox
$outputBox.Multiline = $true
$outputBox.ReadOnly = $true
$outputBox.ScrollBars = 'Vertical'
$outputBox.Dock = 'Fill'
$outputBox.BackColor = [System.Drawing.Color]::White
$outputBox.Font = New-Object System.Drawing.Font('Consolas', 9)
$outputGroup.Controls.Add($outputBox)

$footer = New-Object System.Windows.Forms.StatusStrip
$footerStatus = New-Object System.Windows.Forms.ToolStripStatusLabel
$footerStatus.Text = '就绪'
$footerStatus.Spring = $true
$footerStatus.TextAlign = 'MiddleLeft'
[void]$footer.Items.Add($footerStatus)
$form.Controls.Add($footer)

function Append-Output {
    param([Parameter(Mandatory)][string]$Text)
    $timestamp = Get-Date -Format 'HH:mm:ss'
    $outputBox.AppendText("[$timestamp] $Text`r`n")
    $outputBox.SelectionStart = $outputBox.TextLength
    $outputBox.ScrollToCaret()
}

function Set-ActionsEnabled {
    param([bool]$Enabled)
    foreach ($button in $script:actionButtons) {
        $button.Enabled = $Enabled
    }
}

function Set-StatusColor {
    param(
        [Parameter(Mandatory)][System.Windows.Forms.Label]$Label,
        [Parameter(Mandatory)][string]$Text
    )

    if ($Text -in @('运行中', 'UP', '关闭')) {
        $Label.ForeColor = [System.Drawing.Color]::FromArgb(22, 120, 75)
    }
    elseif ($Text -in @('已停止', 'DOWN', '未安装', '清单异常')) {
        $Label.ForeColor = [System.Drawing.Color]::Firebrick
    }
    elseif ($Text -in @('开启', '正在启动', '正在停止')) {
        $Label.ForeColor = [System.Drawing.Color]::DarkOrange
    }
    else {
        $Label.ForeColor = [System.Drawing.Color]::FromArgb(31, 41, 55)
    }
}

function Refresh-Dashboard {
    try {
        $snapshot = Get-DashboardSnapshot
        foreach ($key in $statusValueLabels.Keys) {
            $text = [string]$snapshot.$key
            $statusValueLabels[$key].Text = $text
            Set-StatusColor -Label $statusValueLabels[$key] -Text $text
        }
        $commitValue.Text = [string]$snapshot.Commit
    }
    catch {
        Append-Output "刷新状态失败：$($_.Exception.Message)"
    }
}

function Start-ControlOperation {
    param(
        [Parameter(Mandatory)][string]$ScriptPath,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$Description
    )

    if ($script:activeProcess -and -not $script:activeProcess.HasExited) {
        [System.Windows.Forms.MessageBox]::Show('已有操作正在执行，请等待完成。', 'MRR 管理中心', 'OK', 'Information') | Out-Null
        return
    }
    if (-not (Test-Path -LiteralPath $ScriptPath -PathType Leaf)) {
        [System.Windows.Forms.MessageBox]::Show("控制脚本不存在：$ScriptPath", 'MRR 管理中心', 'OK', 'Error') | Out-Null
        return
    }

    $encoded = New-EncodedInvocation -ScriptPath $ScriptPath -Arguments $Arguments
    $powershellExe = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $powershellExe
    $startInfo.Arguments = "-NoProfile -ExecutionPolicy Bypass -EncodedCommand $encoded"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.StandardOutputEncoding = [Text.Encoding]::UTF8
    $startInfo.StandardErrorEncoding = [Text.Encoding]::UTF8

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "无法启动操作：$Description"
    }

    $script:activeProcess = $process
    $script:activeDescription = $Description
    Set-ActionsEnabled $false
    $footerStatus.Text = "正在执行：$Description"
    Append-Output "开始：$Description"
    $operationTimer.Start()
}

function Start-MrrCtl {
    param(
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$Description
    )
    Start-ControlOperation -ScriptPath $mrrCtl -Arguments ($Arguments + @('-Root', $Root)) -Description $Description
}

function Start-NginxCtl {
    param(
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$Description
    )
    Start-ControlOperation -ScriptPath $nginxCtl -Arguments ($Arguments + @('-Root', $Root)) -Description $Description
}

$operationTimer = New-Object System.Windows.Forms.Timer
$operationTimer.Interval = 350
$operationTimer.Add_Tick({
    if (-not $script:activeProcess) {
        $operationTimer.Stop()
        return
    }
    if (-not $script:activeProcess.HasExited) { return }

    $operationTimer.Stop()
    $stdout = $script:activeProcess.StandardOutput.ReadToEnd().Trim()
    $stderr = $script:activeProcess.StandardError.ReadToEnd().Trim()
    $exitCode = $script:activeProcess.ExitCode
    $description = $script:activeDescription

    if ($stdout) { Append-Output $stdout }
    if ($stderr) { Append-Output $stderr }
    if ($exitCode -eq 0) {
        Append-Output "完成：$description"
        $footerStatus.Text = '操作完成'
    }
    else {
        Append-Output "失败：$description（退出码 $exitCode）"
        $footerStatus.Text = '操作失败'
        [System.Windows.Forms.MessageBox]::Show("$description 执行失败，请查看操作输出。", 'MRR 管理中心', 'OK', 'Error') | Out-Null
    }

    $script:activeProcess.Dispose()
    $script:activeProcess = $null
    $script:activeDescription = ''
    Set-ActionsEnabled $true
    Refresh-Dashboard
})

function Add-ActionButton {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][scriptblock]$Handler,
        [System.Drawing.Color]$BackColor = [System.Drawing.Color]::White
    )

    $button = New-Object System.Windows.Forms.Button
    $button.Text = $Text
    $button.Size = New-Object System.Drawing.Size(176, 48)
    $button.Margin = New-Object System.Windows.Forms.Padding(7)
    $button.FlatStyle = 'Flat'
    $button.FlatAppearance.BorderColor = [System.Drawing.Color]::FromArgb(209, 213, 219)
    $button.BackColor = $BackColor
    $button.Cursor = 'Hand'
    $button.Add_Click($Handler)
    [void]$flow.Controls.Add($button)
    [void]$script:actionButtons.Add($button)
}

Add-ActionButton -Text '一键启动全部' -BackColor ([System.Drawing.Color]::FromArgb(220, 252, 231)) -Handler {
    Start-MrrCtl -Arguments @('start', 'all') -Description '启动全部服务'
}
Add-ActionButton -Text '一键停止全部' -BackColor ([System.Drawing.Color]::FromArgb(254, 226, 226)) -Handler {
    $answer = [System.Windows.Forms.MessageBox]::Show('确认停止后端与 Nginx 网关吗？', '停止全部服务', 'YesNo', 'Warning')
    if ($answer -eq 'Yes') { Start-MrrCtl -Arguments @('stop', 'all') -Description '停止全部服务' }
}
Add-ActionButton -Text '一键重启全部' -BackColor ([System.Drawing.Color]::FromArgb(219, 234, 254)) -Handler {
    $answer = [System.Windows.Forms.MessageBox]::Show('确认重启后端与 Nginx 网关吗？', '重启全部服务', 'YesNo', 'Question')
    if ($answer -eq 'Yes') { Start-MrrCtl -Arguments @('restart', 'all') -Description '重启全部服务' }
}
Add-ActionButton -Text '刷新运行状态' -Handler { Refresh-Dashboard }

Add-ActionButton -Text '暂停访问（维护）' -BackColor ([System.Drawing.Color]::FromArgb(255, 237, 213)) -Handler {
    $message = [Microsoft.VisualBasic.Interaction]::InputBox('请输入维护提示内容：', '开启维护模式', '系统维护中，请稍后再试。')
    if (-not [string]::IsNullOrWhiteSpace($message)) {
        Start-MrrCtl -Arguments @('maintenance', 'on', '-Message', $message) -Description '开启维护模式'
    }
}
Add-ActionButton -Text '恢复正常访问' -BackColor ([System.Drawing.Color]::FromArgb(220, 252, 231)) -Handler {
    Start-MrrCtl -Arguments @('maintenance', 'off') -Description '关闭维护模式'
}
Add-ActionButton -Text '部署发布 ZIP' -Handler {
    $dialog = New-Object System.Windows.Forms.OpenFileDialog
    $dialog.Title = '选择 MRR Windows 发布包'
    $dialog.Filter = 'MRR 发布包 (*.zip)|*.zip|所有文件 (*.*)|*.*'
    if (Test-Path -LiteralPath $packagesPath -PathType Container) { $dialog.InitialDirectory = $packagesPath }
    if ($dialog.ShowDialog() -eq 'OK') {
        $answer = [System.Windows.Forms.MessageBox]::Show("确认部署以下发布包？`r`n$($dialog.FileName)", '部署发布包', 'YesNo', 'Warning')
        if ($answer -eq 'Yes') {
            Start-MrrCtl -Arguments @('deploy', $dialog.FileName) -Description '部署发布包'
        }
    }
    $dialog.Dispose()
}
Add-ActionButton -Text '查看版本列表' -Handler {
    Start-MrrCtl -Arguments @('versions') -Description '读取版本列表'
}

Add-ActionButton -Text '系统诊断' -Handler {
    Start-MrrCtl -Arguments @('doctor') -Description '执行系统诊断'
}
Add-ActionButton -Text '检查 Nginx 配置' -Handler {
    Start-NginxCtl -Arguments @('test') -Description '检查 Nginx 配置'
}
Add-ActionButton -Text '平滑重载 Nginx' -Handler {
    Start-NginxCtl -Arguments @('reload') -Description '平滑重载 Nginx'
}
Add-ActionButton -Text '打开系统首页' -Handler {
    Start-Process 'http://127.0.0.1/'
}

Add-ActionButton -Text '打开日志目录' -Handler {
    if (Test-Path -LiteralPath $logsPath -PathType Container) { Start-Process explorer.exe -ArgumentList $logsPath }
    else { [System.Windows.Forms.MessageBox]::Show("日志目录不存在：$logsPath", 'MRR 管理中心', 'OK', 'Information') | Out-Null }
}
Add-ActionButton -Text '打开配置目录' -Handler {
    if (Test-Path -LiteralPath $configPath -PathType Container) { Start-Process explorer.exe -ArgumentList $configPath }
    else { [System.Windows.Forms.MessageBox]::Show("配置目录不存在：$configPath", 'MRR 管理中心', 'OK', 'Information') | Out-Null }
}
Add-ActionButton -Text '打开发布包目录' -Handler {
    if (-not (Test-Path -LiteralPath $packagesPath -PathType Container)) {
        New-Item -ItemType Directory -Path $packagesPath -Force | Out-Null
    }
    Start-Process explorer.exe -ArgumentList $packagesPath
}
Add-ActionButton -Text '清空操作输出' -Handler { $outputBox.Clear() }

$refreshTimer = New-Object System.Windows.Forms.Timer
$refreshTimer.Interval = 5000
$refreshTimer.Add_Tick({
    if (-not $script:activeProcess) { Refresh-Dashboard }
})

$form.Add_Shown({
    Append-Output 'MRR 一键管理中心已启动。所有服务与部署操作均调用现有受控脚本。'
    Refresh-Dashboard
    $refreshTimer.Start()
})

$form.Add_FormClosing({
    param($sender, $eventArgs)
    if ($script:activeProcess -and -not $script:activeProcess.HasExited) {
        $answer = [System.Windows.Forms.MessageBox]::Show('当前操作仍在执行。关闭管理中心不会终止后台操作，确认关闭吗？', 'MRR 管理中心', 'YesNo', 'Warning')
        if ($answer -ne 'Yes') { $eventArgs.Cancel = $true }
    }
})

[void]$form.ShowDialog()
