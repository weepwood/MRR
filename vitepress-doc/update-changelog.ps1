[CmdletBinding()]
param(
    [switch]$SkipPull,
    [switch]$GitOnly
)

$ErrorActionPreference = 'Stop'
$DocsRoot = $PSScriptRoot
$RepositoryRoot = Split-Path -Parent $DocsRoot
$TemporaryToken = $false
$PreviousGitHubMode = $env:MRR_CHANGELOG_GITHUB

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $Command $($Arguments -join ' ')"
    }
}

Push-Location $RepositoryRoot
try {
    if (-not $SkipPull) {
        & git rev-parse --abbrev-ref --symbolic-full-name '@{u}' *> $null
        if ($LASTEXITCODE -eq 0) {
            Write-Host '正在拉取当前分支的远程更新...'
            Invoke-CheckedCommand git pull --ff-only
        }
        else {
            Write-Warning '当前分支没有上游分支，已跳过 git pull。可先设置 upstream，或继续使用 -SkipPull。'
        }
    }

    Push-Location $DocsRoot
    try {
        if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
            throw '未找到 Node.js，请先安装 Node.js 20 或更高版本。'
        }
        if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
            throw '未找到 npm，请检查 Node.js 安装。'
        }

        if ($GitOnly) {
            $env:MRR_CHANGELOG_GITHUB = 'false'
            Write-Host '已启用纯 Git 模式，不访问 GitHub API。'
        }
        else {
            if (-not $env:GITHUB_TOKEN -and -not $env:GH_TOKEN) {
                $GitHubCli = Get-Command gh -ErrorAction SilentlyContinue
                if ($GitHubCli) {
                    $Token = (& gh auth token 2> $null)
                    if ($LASTEXITCODE -eq 0 -and $Token) {
                        $env:GITHUB_TOKEN = $Token.Trim()
                        $TemporaryToken = $true
                    }
                }
            }

            if ($env:GITHUB_TOKEN -or $env:GH_TOKEN) {
                $env:MRR_CHANGELOG_GITHUB = 'true'
                Write-Host '已启用 GitHub PR/Issue 增强。'
            }
            else {
                $env:MRR_CHANGELOG_GITHUB = 'false'
                Write-Warning '未找到 GitHub Token，将生成纯 Git 日志。可先执行 gh auth login，或设置 GITHUB_TOKEN。'
            }
        }

        Write-Host '正在生成更新日志...'
        Invoke-CheckedCommand npm run docs:changelog

        Write-Host ''
        Write-Host '更新完成：vitepress-doc/user-guide/changelog.md'
        Write-Host '文件差异：'
        Invoke-CheckedCommand git diff -- user-guide/changelog.md
    }
    finally {
        Pop-Location
    }
}
finally {
    if ($TemporaryToken) {
        Remove-Item Env:GITHUB_TOKEN -ErrorAction SilentlyContinue
    }

    if ($null -eq $PreviousGitHubMode) {
        Remove-Item Env:MRR_CHANGELOG_GITHUB -ErrorAction SilentlyContinue
    }
    else {
        $env:MRR_CHANGELOG_GITHUB = $PreviousGitHubMode
    }

    Pop-Location
}
