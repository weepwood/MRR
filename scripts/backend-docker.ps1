param(
    [ValidateSet('setup', 'up', 'status', 'logs', 'down')]
    [string]$Command = 'up'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$stateDirectory = Join-Path $repoRoot '.mrr-dev'
$envFile = Join-Path $stateDirectory 'backend.env'
$composeFile = Join-Path $repoRoot 'docker-compose.backend-dev.yml'

function New-HexSecret([int]$ByteCount) {
    $buffer = New-Object byte[] $ByteCount
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return [BitConverter]::ToString($buffer).Replace('-', '').ToLowerInvariant()
}

function Protect-Path([string]$Path, [bool]$Directory) {
    $sid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $aclArgs = @($Path, '/inheritance:r', '/grant:r', "*$($sid):(F)", '*S-1-5-18:(F)')
    if ($Directory) { $aclArgs += '/T' }
    & icacls.exe @aclArgs | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not restrict local backend development credentials.' }
}

function Initialize-Environment {
    if (Test-Path -LiteralPath $envFile) {
        throw 'The local backend environment already exists; it was left unchanged.'
    }
    if (-not (Test-Path -LiteralPath $stateDirectory)) {
        New-Item -ItemType Directory -Path $stateDirectory | Out-Null
    }
    $directoryItem = Get-Item -LiteralPath $stateDirectory -Force
    if (-not $directoryItem.PSIsContainer -or ($directoryItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'The local development state path must be a real directory.'
    }
    Protect-Path $stateDirectory $true
    $postgresSecret = New-HexSecret 24
    $jwtSecret = New-HexSecret 32
    $aesSecret = New-HexSecret 16
    $contents = "POSTGRES_PASSWORD=$postgresSecret`r`nJWT_SECRET_KEY=$jwtSecret`r`nAES_SECRET_KEY=$aesSecret`r`nMRR_BACKEND_PORT=18045`r`nMRR_POSTGRES_PORT=15432`r`n"
    [IO.File]::WriteAllText($envFile, $contents, [Text.Encoding]::ASCII)
    Protect-Path $envFile $false
    Write-Host 'Created private local credentials in .mrr-dev/backend.env.'
}

if ($Command -eq 'setup') {
    Initialize-Environment
    exit 0
}
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Run .\scripts\backend-docker.ps1 setup once to generate private local credentials.'
}
$directoryItem = Get-Item -LiteralPath $stateDirectory -Force
$envItem = Get-Item -LiteralPath $envFile -Force
if (-not $directoryItem.PSIsContainer -or ($directoryItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
    $envItem.PSIsContainer -or ($envItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw 'Local development credentials must be regular files inside a real directory.'
}
Protect-Path $stateDirectory $true
Protect-Path $envFile $false

$envValues = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([A-Z_]+)=(.*)$') { $envValues[$Matches[1]] = $Matches[2] }
}
foreach ($key in @('POSTGRES_PASSWORD', 'JWT_SECRET_KEY', 'AES_SECRET_KEY')) {
    if (-not $envValues[$key] -or $envValues[$key] -notmatch '^[0-9a-f]+$') {
        throw "The private local environment has an invalid $key value; inspect it locally and rerun setup only after preserving it."
    }
}
if ($envValues['POSTGRES_PASSWORD'].Length -ne 48 -or $envValues['JWT_SECRET_KEY'].Length -ne 64 -or $envValues['AES_SECRET_KEY'].Length -ne 32) {
    throw 'The private local environment has an invalid secret length; inspect it locally.'
}
$port = 0
if (-not [int]::TryParse($envValues['MRR_BACKEND_PORT'], [ref]$port) -or $port -lt 1024 -or $port -gt 65535) {
    throw 'MRR_BACKEND_PORT must be between 1024 and 65535.'
}
$postgresPort = 0
if (-not [int]::TryParse($envValues['MRR_POSTGRES_PORT'], [ref]$postgresPort) -or $postgresPort -lt 1024 -or $postgresPort -gt 65535) {
    throw 'MRR_POSTGRES_PORT must be between 1024 and 65535.'
}

$revision = (& git -C $repoRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $revision -notmatch '^[0-9a-f]{40}$') { throw 'Could not read the current Git revision.' }
$env:MRR_GIT_COMMIT = $revision
$pathBytes = [Text.Encoding]::UTF8.GetBytes($repoRoot.ToLowerInvariant())
$sha = [Security.Cryptography.SHA256]::Create()
try { $workspaceId = [BitConverter]::ToString($sha.ComputeHash($pathBytes)).Replace('-', '').ToLowerInvariant().Substring(0, 12) }
finally { $sha.Dispose() }
$project = "mrr-backend-dev-$workspaceId"
$composeArgs = @('-p', $project, '--env-file', $envFile, '-f', $composeFile)

Push-Location $repoRoot
try {
    switch ($Command) {
        'up' { & docker compose @composeArgs up --build -d backend }
        'status' { & docker compose @composeArgs ps }
        'logs' { & docker compose @composeArgs logs --follow backend }
        'down' { & docker compose @composeArgs down }
    }
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) { exit $exitCode }
    if ($Command -eq 'up') {
        $ready = $false
        $deadline = (Get-Date).AddMinutes(3)
        while ((Get-Date) -lt $deadline) {
            $statusCode = 0
            try {
                $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$port/api/v1/auth/me" -TimeoutSec 3
                $statusCode = [int]$response.StatusCode
            }
            catch {
                if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                    $statusCode = [int]$_.Exception.Response.StatusCode
                }
            }
            if ($statusCode -eq 401) { $ready = $true; break }
            Start-Sleep -Seconds 2
        }
        if (-not $ready) { throw 'Backend did not become ready; inspect logs with .\scripts\backend-docker.ps1 logs.' }
        Write-Host "Backend API ready at http://127.0.0.1:$port; PostgreSQL at 127.0.0.1:$postgresPort."
    }
}
finally {
    Pop-Location
}