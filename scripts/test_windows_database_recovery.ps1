#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$PostgresBin,
    [Parameter(Mandatory=$true)][string]$Jar
)
$ErrorActionPreference = 'Stop'
$PostgresBin = (Resolve-Path -LiteralPath $PostgresBin).Path
$Jar = (Resolve-Path -LiteralPath $Jar).Path
$version = & (Join-Path $PostgresBin 'pg_dump.exe') --version
if ($LASTEXITCODE -ne 0 -or $version -notmatch 'PostgreSQL\) 16\.') { throw 'PostgreSQL 16 Windows tools are required.' }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe).Source }
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw 'Configure JAVA_HOME with an installed Java 21 runtime.' }
$recovery = Join-Path (Split-Path $PSScriptRoot -Parent) 'deploy/windows/database-recovery.ps1'
$temporaryParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
$temporary = Join-Path $temporaryParent ('mrr-recovery-test-' + [Guid]::NewGuid().ToString('N'))
$data = Join-Path $temporary 'cluster'
$archive = Join-Path $temporary 'mrr-synthetic.dump'
$oldPath = $env:PATH
$pgEnvironment = @{}
Get-ChildItem Env: | Where-Object Name -Like 'PG*' | ForEach-Object { $pgEnvironment[$_.Name] = $_.Value }
$application = $null
$clusterStarted = $false

function Invoke-TestSql([string]$Service, [string]$Sql) {
    $output = & (Join-Path $PostgresBin 'psql.exe') -X -qAt -v ON_ERROR_STOP=1 --dbname "service=$Service" --command $Sql
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic database command failed.' }
    return ($output -join "`n").Trim()
}
function Assert-Rejected([scriptblock]$Operation, [string]$ExpectedMessage) {
    $rejected = $false
    try { & $Operation | Out-Null }
    catch {
        if ($_.Exception.Message -notlike ('*' + $ExpectedMessage + '*')) { throw }
        $rejected = $true
    }
    if (-not $rejected) { throw 'Unsafe recovery operation was unexpectedly accepted.' }
}
function Get-TestPort {
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    return $port
}

try {
    New-Item -ItemType Directory -Path $temporary | Out-Null
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetOwner($sid)
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($allowed in @($sid.Value, 'S-1-5-18', 'S-1-5-32-544')) {
        $identity = New-Object Security.Principal.SecurityIdentifier($allowed)
        $rule = New-Object Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $temporary -AclObject $acl
    Get-ChildItem Env: | Where-Object Name -Like 'PG*' | ForEach-Object { Remove-Item -LiteralPath ('Env:' + $_.Name) }
    $env:PATH = $PostgresBin + ';' + $oldPath
    $password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $passwordFile = Join-Path $temporary 'init-password'
    [IO.File]::WriteAllText($passwordFile, $password, [Text.Encoding]::ASCII)
    & (Join-Path $PostgresBin 'initdb.exe') -D $data -U mrr_rehearsal --auth=scram-sha-256 --pwfile $passwordFile --encoding UTF8 --locale C *> (Join-Path $temporary 'initdb.log')
    if ($LASTEXITCODE -ne 0) { throw 'Isolated initdb failed.' }
    $port = Get-TestPort
    # Avoid a PowerShell native pipeline: postgres inherits its pipe and would keep it open.
    $control = New-Object Diagnostics.ProcessStartInfo
    $control.FileName = Join-Path $PostgresBin 'pg_ctl.exe'
    $control.Arguments = '-D "' + $data + '" -l "' + (Join-Path $temporary 'postgres.log') + '" -o "-h 127.0.0.1 -p ' + $port + '" -w start'
    $control.UseShellExecute = $false
    $control.CreateNoWindow = $true
    $control.RedirectStandardOutput = $true
    $control.RedirectStandardError = $true
    $clusterStarted = $true
    $starter = [Diagnostics.Process]::Start($control)
    $controlOut = $starter.StandardOutput.ReadToEndAsync()
    $controlError = $starter.StandardError.ReadToEndAsync()
    if (-not $starter.WaitForExit(60000)) { $starter.Kill(); throw 'Isolated PostgreSQL startup timed out.' }
    if ($starter.ExitCode -ne 0) { throw 'Isolated PostgreSQL failed to start.' }
    $env:PGPASSFILE = Join-Path $temporary 'pgpass.conf'
    $env:PGSERVICEFILE = Join-Path $temporary 'pg_service.conf'
    [IO.File]::WriteAllText($env:PGPASSFILE, "127.0.0.1:${port}:*:mrr_rehearsal:${password}`n", [Text.Encoding]::ASCII)
    $services = @()
    foreach ($name in @('postgres', 'mrr_source', 'mrr_replica')) {
        $services += "[$name]`nhost=127.0.0.1`nport=$port`nuser=mrr_rehearsal`ndbname=$name`nsslmode=disable`n"
    }
    [IO.File]::WriteAllText($env:PGSERVICEFILE, ($services -join "`n"), [Text.Encoding]::ASCII)
    Invoke-TestSql postgres 'CREATE DATABASE mrr_source' | Out-Null
    Invoke-TestSql postgres 'CREATE DATABASE mrr_replica' | Out-Null

    # Run the actual application's Flyway migrations, with no inherited production environment.
    $backendPort = Get-TestPort
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $java
    $start.Arguments = '-jar "' + $Jar + '" --spring.config.location=classpath:/application.properties'
    $start.WorkingDirectory = $temporary
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.EnvironmentVariables.Clear()
    foreach ($key in @('PATH','SystemRoot','WINDIR','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA')) {
        $value = [Environment]::GetEnvironmentVariable($key)
        if ($value) { $start.EnvironmentVariables[$key] = $value }
    }
    $appEnv = @{
        SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${port}/mrr_source?currentSchema=app";
        SPRING_DATASOURCE_USERNAME='mrr_rehearsal'; SPRING_DATASOURCE_PASSWORD=$password;
        JWT_SECRET_KEY=([Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N'));
        AES_SECRET_KEY=[Guid]::NewGuid().ToString('N'); MRR_AUDIT_HMAC_KEY=[Guid]::NewGuid().ToString('N');
        SERVER_PORT=[string]$backendPort; SERVER_ADDRESS='127.0.0.1'; MANAGEMENT_SERVER_PORT='0';
        APP_STATUS_ENABLED='false'; APP_LOG_RETENTION_ENABLED='false';
        ARCHIVE_EXPORT_TEMP_DIRECTORY=(Join-Path $temporary 'exports')
    }
    foreach ($entry in $appEnv.GetEnumerator()) { $start.EnvironmentVariables[$entry.Key] = $entry.Value }
    $application = [Diagnostics.Process]::Start($start)
    $stdout = $application.StandardOutput.ReadToEndAsync()
    $stderr = $application.StandardError.ReadToEndAsync()
    $deadline = (Get-Date).AddSeconds(150)
    $ready = $false
    while ((Get-Date) -lt $deadline) {
        if ($application.HasExited) { throw 'Synthetic application exited before Flyway readiness.' }
        try { Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$backendPort/api/v1/auth/me" -TimeoutSec 2 | Out-Null }
        catch { if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) { $ready = $true; break } }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Synthetic application readiness timed out.' }
    $application.Kill()
    $application.WaitForExit()
    # Stop all application writers before taking the full database backup.
    $migrations = Invoke-TestSql mrr_source 'SELECT count(*) FROM app.flyway_schema_history WHERE success'
    if ([int]$migrations -lt 1) { throw 'Actual Flyway migrations did not run.' }
    Invoke-TestSql mrr_source "INSERT INTO app.mr_patient (bah,name) VALUES ('REHEARSAL-ONLY-0001','Synthetic Patient')" | Out-Null
    $source = Invoke-TestSql mrr_source 'SELECT count(*) FROM app.mr_patient'
    & $recovery -Action Backup -Service mrr_source -Archive $archive | Out-Null
    & $recovery -Action Verify -Service mrr_source -Archive $archive | Out-Null
    Assert-Rejected { & $recovery -Action Backup -Service mrr_source -Archive $archive } 'Refusing to overwrite'
    Assert-Rejected { & $recovery -Action RestoreToEmptyReplica -Service mrr_replica -Archive $archive } 'explicitly selected isolated'
    & $recovery -Action RestoreToEmptyReplica -Service mrr_replica -Archive $archive -IsolatedReplica | Out-Null
    if ((Invoke-TestSql mrr_replica 'SELECT count(*) FROM app.mr_patient') -ne $source) { throw 'Restored patient count differs.' }
    if ((Invoke-TestSql mrr_replica 'SELECT count(*) FROM app.flyway_schema_history WHERE success') -ne $migrations) { throw 'Restored migration history differs.' }
    if ((Invoke-TestSql mrr_replica "SELECT count(*) FROM app.mr_patient WHERE bah='REHEARSAL-ONLY-0001' AND name='Synthetic Patient'") -ne '1') { throw 'Restored synthetic content differs.' }
    Assert-Rejected { & $recovery -Action RestoreToEmptyReplica -Service mrr_replica -Archive $archive -IsolatedReplica } 'not empty'
    Write-Output "Windows PowerShell $($PSVersionTable.PSVersion); $version; full backup/verify/empty restore/content/Flyway checks passed. Overwrite, missing opt-in and nonempty restore refused. Synthetic archive is deleted after rehearsal."
}
finally {
    $applicationStopped = $true
    try { if ($application -and -not $application.HasExited) { $application.Kill(); $application.WaitForExit() } }
    catch { $applicationStopped = $false; Write-Warning 'Test application could not be stopped; temporary directory retained.' }
    $canDelete = $true
    if ($clusterStarted) {
        & (Join-Path $PostgresBin 'pg_ctl.exe') -D $data -m immediate -w stop *> $null
        $canDelete = $LASTEXITCODE -eq 0
    }
    Get-ChildItem Env: | Where-Object Name -Like 'PG*' | ForEach-Object { Remove-Item -LiteralPath ('Env:' + $_.Name) }
    foreach ($key in $pgEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $pgEnvironment[$key], 'Process') }
    $env:PATH = $oldPath
    $resolved = [IO.Path]::GetFullPath($temporary)
    if ($canDelete -and $applicationStopped -and $resolved.StartsWith($temporaryParent + '\', [StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolved -Leaf) -match '^mrr-recovery-test-[a-f0-9]{32}$') {
        if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
    } elseif (-not $canDelete) { Write-Warning 'Test PostgreSQL stop failed; temporary cluster retained and must be stopped before cleanup.' }
}
