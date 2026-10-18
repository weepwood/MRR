#requires -Version 5.1
[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Service,
      [Parameter(Mandatory=$true)][string]$Checkpoint,
      [ValidateRange(100,100000)][int]$BatchSize=10000,
      [ValidateRange(1,1000)][int]$MaxBatches=10)
$ErrorActionPreference='Stop'
$psql=Get-Command psql -ErrorAction Stop
$checkpointPath=[System.IO.Path]::GetFullPath($Checkpoint)
[void][System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($checkpointPath))
function Invoke-QualityJson([string]$Sql) {
    $raw=$Sql | & $psql.Source -X -qAt --set ON_ERROR_STOP=1 --dbname "service=$Service" --file - 2>$null
    if($LASTEXITCODE -ne 0){throw 'Quality batch failed; checkpoint unchanged. Check database inside the hospital, then retry.'}
    return (($raw -join '') | ConvertFrom-Json)
}
$identity=Invoke-QualityJson "BEGIN READ ONLY; SET LOCAL statement_timeout='3s'; SELECT json_build_object('databaseKey',md5((SELECT system_identifier::text FROM pg_control_system())||':'||(SELECT oid::text FROM pg_database WHERE datname=current_database())),'highWater',COALESCE(MAX(id),0)) FROM app.mr_scan; ROLLBACK;"
$sha=[System.Security.Cryptography.SHA256]::Create()
try {$identity.databaseKey=[BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Service+'|'+$identity.databaseKey))).Replace('-','').ToLowerInvariant()} finally {$sha.Dispose()}
$ruleVersion='scan-links-20261018-v1'
if(Test-Path -LiteralPath $checkpointPath){
    $state=Get-Content -LiteralPath $checkpointPath -Raw | ConvertFrom-Json
    if($state.databaseKey -ne $identity.databaseKey -or $state.ruleVersion -ne $ruleVersion){throw 'Checkpoint belongs to a different database, service or rule version.'}
} else {
    $state=[pscustomobject]@{version=1;ruleVersion=$ruleVersion;databaseKey=$identity.databaseKey;highWater=[long]$identity.highWater;afterId=[long]0;checked=[long]0;missing=[long]0;orphan=[long]0;mismatch=[long]0;invalidState=[long]0;updatedAt='';status='RUNNING'}
}
for($batch=0;$batch -lt $MaxBatches -and [long]$state.afterId -lt [long]$state.highWater;$batch++){
    $after=[long]$state.afterId
    $until=[Math]::Min($after+$BatchSize,[long]$state.highWater)
    $sql=@"
BEGIN READ ONLY;
SET LOCAL statement_timeout='10s';
SELECT json_build_object('checked',COUNT(*),
 'missing',COUNT(*) FILTER(WHERE s.uploadflag<>0 AND s.archive_id IS NULL),
 'orphan',COUNT(*) FILTER(WHERE s.archive_id IS NOT NULL AND a.id IS NULL),
 'mismatch',COUNT(*) FILTER(WHERE a.id IS NOT NULL AND (
    (NULLIF(BTRIM(s.bah),'') IS NOT NULL AND a.bah IS NOT NULL AND app.numeric_code_key(s.bah) IS DISTINCT FROM app.numeric_code_key(a.bah)) OR
    (NULLIF(BTRIM(s.sjh),'') IS NOT NULL AND a.sjh IS NOT NULL AND app.numeric_code_key(s.sjh) IS DISTINCT FROM app.numeric_code_key(a.sjh)))),
 'invalidState',COUNT(*) FILTER(WHERE s.uploadflag IS NULL OR s.uploadflag NOT IN (0,1)))
FROM app.mr_scan s LEFT JOIN app.mr_archive a ON a.id=s.archive_id WHERE s.id>$after AND s.id<=$until;
ROLLBACK;
"@
    $counts=Invoke-QualityJson $sql
    foreach($name in @('checked','missing','orphan','mismatch','invalidState')){$state.$name=[long]$state.$name+[long]$counts.$name}
    $state.afterId=$until
    $state.updatedAt=[DateTime]::UtcNow.ToString('o')
    if($until -eq [long]$state.highWater){$state.status='SCANNED_WITH_CONCURRENT_WRITE_CAVEAT'}
    $temporary=$checkpointPath+'.partial'
    $state | ConvertTo-Json | Set-Content -LiteralPath $temporary -Encoding UTF8
    Move-Item -LiteralPath $temporary -Destination $checkpointPath -Force
}
if([long]$state.highWater -eq 0){$state.status='SCANNED_WITH_CONCURRENT_WRITE_CAVEAT';$state.updatedAt=[DateTime]::UtcNow.ToString('o');$state | ConvertTo-Json | Set-Content -LiteralPath ($checkpointPath+'.partial') -Encoding UTF8;Move-Item -LiteralPath ($checkpointPath+'.partial') -Destination $checkpointPath -Force}
Write-Output ('Checked ID window through {0}/{1}; concurrent modifications require a later recheck. No medical data changed.' -f $state.afterId,$state.highWater)
