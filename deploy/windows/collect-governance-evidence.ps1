#requires -Version 5.1
[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Service,
      [Parameter(Mandatory=$true)][string]$OutputDirectory,
      [ValidatePattern('^[0-9A-Za-z.+_-]+$')][string]$ApplicationVersion='unknown')
$ErrorActionPreference='Stop'
# 连接由 pg_service.conf / pgpass 提供，不接受或输出密码、连接串及患者编号。
$psql=Get-Command psql -ErrorAction Stop
$target=[System.IO.Path]::GetFullPath($OutputDirectory)
[void][System.IO.Directory]::CreateDirectory($target)
$sql=@'
BEGIN READ ONLY;
SET LOCAL statement_timeout='5s';
SELECT json_build_object(
  'collectedAt',CURRENT_TIMESTAMP,'postgresVersion',current_setting('server_version'),
  'expectedSchema',current_schema()='app',
  'migrations',(SELECT json_agg(x) FROM (SELECT version,success FROM app.flyway_schema_history ORDER BY installed_rank DESC LIMIT 5)x),
  'tables',(SELECT json_agg(x) FROM (SELECT c.relname,c.reltuples::bigint AS estimated_rows,pg_total_relation_size(c.oid) AS bytes FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='app' AND c.relname IN ('mr_scan','mr_archive','mr_statistics','mr_patient','runtime_log_event'))x),
  'constraints',(SELECT json_agg(x) FROM (SELECT conname,conrelid::regclass::text AS source,confrelid::regclass::text AS target,convalidated,condeferrable,pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE contype='f' AND connamespace='app'::regnamespace)x),
  'triggers',(SELECT json_agg(x) FROM (SELECT tgname,tgrelid::regclass::text AS relation,tgenabled FROM pg_trigger WHERE NOT tgisinternal AND tgrelid IN ('app.mr_scan'::regclass,'app.mr_statistics'::regclass,'app.mr_archive_box_record'::regclass))x),
  'invalidIndexes',(SELECT json_agg(c.relname) FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid WHERE NOT i.indisvalid AND c.relnamespace='app'::regnamespace),
  'waits',(SELECT json_agg(x) FROM (SELECT state,wait_event_type,COUNT(*) AS connections FROM pg_stat_activity WHERE datname=current_database() GROUP BY state,wait_event_type)x)
);
ROLLBACK;
'@
$raw=$sql | & $psql.Source -X -qAt --set ON_ERROR_STOP=1 --dbname "service=$Service" --file - 2>$null
if($LASTEXITCODE -ne 0){throw 'Evidence collection failed; check connection, permissions and schema inside the hospital.'}
$parsed=($raw -join '') | ConvertFrom-Json
$result=[ordered]@{applicationVersion=$ApplicationVersion; evidence=$parsed}
$result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $target 'governance-evidence.json') -Encoding UTF8
Write-Output 'Collected schema, migration, index and wait summaries. No patient rows or query text collected.'
