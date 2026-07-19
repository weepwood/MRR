[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^https?://')]
    [string]$WebhookUrl,
    [string]$Root = 'C:\MRR',
    [string]$AmtoolPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$configDir = Join-Path $Root 'config\monitoring'
$configFile = Join-Path $configDir 'alertmanager.yml'
New-Item -ItemType Directory -Force -Path $configDir | Out-Null

# YAML single-quoted values escape a single quote by doubling it.
$safeWebhookUrl = $WebhookUrl.Replace("'", "''")
$config = @"
global:
  resolve_timeout: 5m

route:
  receiver: mrr-webhook
  group_by: [alertname, severity]
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  routes:
    - matchers:
        - severity="critical"
      repeat_interval: 30m

receivers:
  - name: mrr-webhook
    webhook_configs:
      - url: '$safeWebhookUrl'
        send_resolved: true
        max_alerts: 20

inhibit_rules:
  - source_matchers:
      - severity="critical"
    target_matchers:
      - severity="warning"
    equal: [alertname]
"@

Set-Content -LiteralPath $configFile -Value $config -Encoding UTF8
& icacls.exe $configFile /inheritance:r | Out-Null
& icacls.exe $configFile /grant:r '*S-1-5-32-544:F' '*S-1-5-18:F' | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Unable to protect Alertmanager configuration: $configFile"
}

if ($AmtoolPath) {
    if (-not (Test-Path -LiteralPath $AmtoolPath -PathType Leaf)) {
        throw "amtool executable not found: $AmtoolPath"
    }
    & $AmtoolPath check-config $configFile
    if ($LASTEXITCODE -ne 0) {
        throw 'Alertmanager configuration validation failed.'
    }
}

Write-Host "Alertmanager webhook configuration written to: $configFile"
Write-Host 'Configure the Alertmanager Windows service to use this file, then restart the service.'
