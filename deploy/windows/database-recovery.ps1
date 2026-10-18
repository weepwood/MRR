#requires -Version 5.1
[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('Backup','Verify','RestoreToEmptyReplica')][string]$Action,
      [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Service,
      [Parameter(Mandatory=$true)][string]$Archive,
      [switch]$IsolatedReplica,[string[]]$AllowedServiceSid=@())
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'private-file-access.ps1')
$target=[System.IO.Path]::GetFullPath($Archive)
if($Action -eq 'Backup'){
    if((Test-Path -LiteralPath $target) -or (Test-Path -LiteralPath ($target+'.partial'))){throw 'Refusing to overwrite an existing backup or partial archive.'}
    $dump=Get-Command pg_dump -ErrorAction Stop
    $parent=[System.IO.Path]::GetDirectoryName($target)
    if(-not (Test-Path -LiteralPath $parent -PathType Container)){throw 'Create a restricted backup directory first.'}
    Assert-MrrPrivatePath -LiteralPath $parent -AllowedServiceSid $AllowedServiceSid
    $reserved=[System.IO.File]::Open(($target+'.partial'),[System.IO.FileMode]::CreateNew,[System.IO.FileAccess]::Write,[System.IO.FileShare]::None)
    $reserved.Dispose()
    Assert-MrrPrivatePath -LiteralPath ($target+'.partial') -AllowedServiceSid $AllowedServiceSid
    & $dump.Source --dbname "service=$Service" --format custom --file ($target+'.partial') 2>$null
    if($LASTEXITCODE -ne 0){throw 'Backup failed; partial file retained for local diagnosis, do not treat it as a valid backup.'}
    Assert-MrrPrivatePath -LiteralPath ($target+'.partial') -AllowedServiceSid $AllowedServiceSid
    Move-Item -LiteralPath ($target+'.partial') -Destination $target
} elseif(-not (Test-Path -LiteralPath $target -PathType Leaf)){throw 'Backup archive not found.'}
Assert-MrrPrivatePath -LiteralPath $target -AllowedServiceSid $AllowedServiceSid
$restore=Get-Command pg_restore -ErrorAction Stop
& $restore.Source --list $target *> $null
if($LASTEXITCODE -ne 0){throw 'Archive verification failed.'}
if($Action -eq 'RestoreToEmptyReplica'){
    if(-not $IsolatedReplica){throw 'Restore requires an explicitly selected isolated empty replica service.'}
    $psql=Get-Command psql -ErrorAction Stop
    $count=& $psql.Source -X -qAt --dbname "service=$Service" --command "SELECT COUNT(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%' AND c.relkind IN ('r','p');" 2>$null
    if($LASTEXITCODE -ne 0 -or ($count -join '').Trim() -ne '0'){throw 'Restore target is not empty or cannot be verified; no restore attempted.'}
    & $restore.Source --dbname "service=$Service" --exit-on-error --no-owner --no-privileges $target 2>$null
    if($LASTEXITCODE -ne 0){throw 'Replica restore failed; inspect locally and recreate an empty replica before retry.'}
}
$hash=Get-FileHash -LiteralPath $target -Algorithm SHA256
Write-Output ('Archive verified. SHA256={0}. Keep this medical backup only in protected hospital storage.' -f $hash.Hash)
