"""Exercise the actual Windows PowerShell 5.1 ACL boundary with synthetic files."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(os.name == 'nt', 'Requires actual Windows NTFS ACLs')
class PrivateFileAccessTest(unittest.TestCase):
    def test_explicit_everyone_permission_is_rejected_even_without_inheritance(self):
        with tempfile.TemporaryDirectory() as directory:
            script = Path(directory) / 'check.ps1'
            script.write_text(r'''
param([string]$Module,[string]$Directory,[string]$KeyScript,[string]$BackupScript)
$ErrorActionPreference='Stop'
. $Module
$folder=Join-Path $Directory 'private'
[void](New-Item -ItemType Directory -Path $folder)
$owner=[System.Security.Principal.WindowsIdentity]::GetCurrent().User
$acl=New-Object System.Security.AccessControl.DirectorySecurity
$acl.SetAccessRuleProtection($true,$false)
foreach($rule in @($acl.Access)){$acl.RemoveAccessRuleSpecific($rule)}
$acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule($owner,'FullControl','ContainerInherit,ObjectInherit','None','Allow')))
[System.IO.Directory]::SetAccessControl($folder,$acl)
Assert-MrrPrivatePath -LiteralPath $folder
$everyone=New-Object System.Security.Principal.SecurityIdentifier('S-1-1-0')
$acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule($everyone,'ReadAndExecute','ContainerInherit,ObjectInherit','None','Allow')))
[System.IO.Directory]::SetAccessControl($folder,$acl)
if(-not (Get-Acl -LiteralPath $folder).AreAccessRulesProtected){throw 'Fixture must have inheritance disabled'}
$rejected=$false
try {Assert-MrrPrivatePath -LiteralPath $folder} catch {$rejected=$true}
if(-not $rejected){throw 'Explicit Everyone access was accepted'}
$file=Join-Path $folder 'application-secrets.properties'
[System.IO.File]::WriteAllText($file,'# synthetic')
$keyRejected=$false
try {& $KeyScript -SecretsFile $file} catch {$keyRejected=$true}
if(-not $keyRejected){throw 'Key initialized under broad ACL'}
if([System.IO.File]::ReadAllText($file) -match 'mrr.audit.hmac-key='){throw 'Key generated before ACL refusal'}
$partial=Join-Path $folder 'synthetic.dump.partial'
[System.IO.File]::WriteAllText($partial,'synthetic-incomplete-backup')
$refused=$false
try {& $BackupScript -Action Backup -Service synthetic -Archive (Join-Path $folder 'synthetic.dump')} catch {$refused=$_.Exception.Message -match 'Refusing to overwrite'}
if(-not $refused -or [System.IO.File]::ReadAllText($partial) -ne 'synthetic-incomplete-backup'){throw 'Existing partial archive was not preserved'}
Write-Output 'ACL boundary verified' 
''', encoding='utf-8-sig')
            environment = dict(os.environ)
            environment = {key: value for key, value in environment.items() if key.lower() != 'psmodulepath'}
            environment['PSModulePath'] = r'C:\Windows\System32\WindowsPowerShell\v1.0\Modules;C:\Program Files\WindowsPowerShell\Modules'
            result = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-File', str(script),
                                     '-Module', str(ROOT / 'deploy/windows/private-file-access.ps1'),
                                     '-Directory', directory, '-KeyScript', str(ROOT / 'deploy/windows/initialize-audit-key.ps1'), '-BackupScript', str(ROOT / 'deploy/windows/database-recovery.ps1')],
                                    capture_output=True, text=True, env=environment)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('ACL boundary verified', result.stdout)
