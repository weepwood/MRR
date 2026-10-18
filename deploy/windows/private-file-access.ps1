#requires -Version 5.1
function Assert-MrrPrivatePath {
    param([Parameter(Mandatory=$true)][string]$LiteralPath,
          [string[]]$AllowedServiceSid=@())
    foreach($sid in $AllowedServiceSid){if($sid -in @('S-1-1-0','S-1-5-11','S-1-5-7','S-1-5-32-545','S-1-5-32-546')){throw 'Broad groups cannot be approved as a private service identity.'}}
    $allowed=@('S-1-5-18','S-1-5-32-544',[System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value)+$AllowedServiceSid
    foreach($sid in $allowed){if($sid -notmatch '^S-[0-9-]+$'){throw 'Invalid allowed service SID.'}}
    $acl=Get-Acl -LiteralPath $LiteralPath -ErrorAction Stop
    $rules=@($acl.GetAccessRules($true,$true,[System.Security.Principal.SecurityIdentifier]))
    if($rules.Count -eq 0){throw 'Private path has no verifiable access rules.'}
    foreach($rule in $rules){
        if($rule.AccessControlType -eq [System.Security.AccessControl.AccessControlType]::Allow -and $allowed -notcontains $rule.IdentityReference.Value){
            throw 'Private path grants access to an unapproved identity. Restrict the ACL or explicitly name the service SID.'
        }
    }
}
