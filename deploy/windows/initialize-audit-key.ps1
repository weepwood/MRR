#requires -Version 5.1
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$SecretsFile,[string[]]$AllowedServiceSid=@())
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'private-file-access.ps1')
$target=[System.IO.Path]::GetFullPath($SecretsFile)
if(-not (Test-Path -LiteralPath $target -PathType Leaf)){throw 'Select the existing protected application-secrets.properties file.'}
Assert-MrrPrivatePath -LiteralPath $target -AllowedServiceSid $AllowedServiceSid
$content=[System.IO.File]::ReadAllText($target)
if($content -match '(?m)^mrr\.audit\.hmac-key=.+$'){throw 'An audit key already exists; refusing to overwrite searchable history.'}
$bytes=New-Object byte[] 48
$rng=[System.Security.Cryptography.RandomNumberGenerator]::Create()
try {$rng.GetBytes($bytes)} finally {$rng.Dispose()}
$key=[Convert]::ToBase64String($bytes)
# 原地修改保留已有 ACL；不打印密钥，也不放进服务命令行。
$content=$content -replace '(?m)^mrr\.audit\.hmac-key=.*(?:\r?\n)?',''
[System.IO.File]::WriteAllText($target,$content+"`r`nmrr.audit.hmac-key="+$key+"`r`n",(New-Object System.Text.UTF8Encoding($false)))
Assert-MrrPrivatePath -LiteralPath $target -AllowedServiceSid $AllowedServiceSid
Write-Output 'Independent audit key initialized. Restart the backend and verify auditHmacConfigured; retain a protected backup of this file.'
