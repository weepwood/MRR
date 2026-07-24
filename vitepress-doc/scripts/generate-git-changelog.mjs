import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const scriptsRoot = path.dirname(fileURLToPath(import.meta.url))
const docsRoot = path.resolve(scriptsRoot, '..')
const repositoryRoot = path.resolve(docsRoot, '..')
const outputPath = path.join(docsRoot, 'user-guide', 'changelog.md')
const cachePath = path.join(docsRoot, '.cache', 'github-changelog.json')

const parsedLimit = Number.parseInt(process.env.MRR_CHANGELOG_LIMIT ?? '1000', 10)
const commitLimit = Number.isInteger(parsedLimit) && parsedLimit > 0
  ? Math.min(parsedLimit, 1000)
  : 1000

const TYPE_META = {
  feat: { label: '新增', order: 10 },
  fix: { label: '修复', order: 20 },
  perf: { label: '性能', order: 30 },
  refactor: { label: '重构', order: 40 },
  docs: { label: '文档', order: 50 },
  test: { label: '测试', order: 60 },
  build: { label: '构建', order: 70 },
  ci: { label: 'CI', order: 80 },
  chore: { label: '维护', order: 90 },
  revert: { label: '回退', order: 100 },
  merge: { label: '合并', order: 110 },
  other: { label: '其他', order: 120 },
}

function parseBoundedInteger(rawValue, fallback, maximum) {
  const parsed = Number.parseInt(rawValue ?? '', 10)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return fallback
  }
  return Math.min(parsed, maximum)
}

function runGit(args) {
  return execFileSync('git', args, {
    cwd: repositoryRoot,
    encoding: 'utf8',
    maxBuffer: 32 * 1024 * 1024,
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim()
}

function tryRunGit(args) {
  try {
    return { ok: true, output: runGit(args), error: null }
  }
  catch (error) {
    return { ok: false, output: '', error }
  }
}

function escapeMarkdown(value = '') {
  return String(value)
    .replace(/\\/g, '\\\\')
    .replace(/([`*_\[\]<>])/g, '\\$1')
    .replace(/\r?\n/g, ' ')
}

export function normalizeRepositorySlug(remoteUrl) {
  if (!remoteUrl) {
    return null
  }

  const normalized = remoteUrl.trim().replace(/\.git$/i, '')
  const sshMatch = normalized.match(/^git@github\.com:([^/]+\/.+)$/i)
  if (sshMatch) {
    return sshMatch[1]
  }

  const httpsMatch = normalized.match(/^https?:\/\/github\.com\/([^/]+\/.+)$/i)
  if (httpsMatch) {
    return httpsMatch[1]
  }

  return null
}

export function normalizeBranchName(branch) {
  return String(branch ?? '')
    .trim()
    .replace(/^refs\/heads\//, '')
    .replace(/^refs\/remotes\/origin\//, '')
    .replace(/^origin\//, '')
}

function resolveRepositorySlug() {
  const configured = process.env.MRR_GITHUB_REPOSITORY?.trim()
  if (configured) {
    return configured
  }

  try {
    return normalizeRepositorySlug(runGit(['config', '--get', 'remote.origin.url'])) ?? 'weepwood/MRR'
  }
  catch {
    return 'weepwood/MRR'
  }
}

function resolveGitHubBranch(currentBranch) {
  const configured = normalizeBranchName(process.env.MRR_CHANGELOG_BASE_BRANCH)
  if (configured) {
    return configured
  }

  const actionsBaseBranch = normalizeBranchName(process.env.GITHUB_BASE_REF)
  if (actionsBaseBranch) {
    return actionsBaseBranch
  }

  const remoteHeadResult = tryRunGit(['symbolic-ref', '--short', 'refs/remotes/origin/HEAD'])
  if (remoteHeadResult.ok) {
    const remoteHead = normalizeBranchName(remoteHeadResult.output)
    if (remoteHead) {
      return remoteHead
    }
  }

  return 'main'
}

function isRemoteFetchEnabled() {
  const configured = process.env.MRR_CHANGELOG_FETCH_REMOTE?.trim().toLowerCase()
  return configured !== 'false' && configured !== '0' && configured !== 'off'
}

function isValidBranchName(branch) {
  if (!branch) {
    return false
  }
  return tryRunGit(['check-ref-format', '--branch', branch]).ok
}

function gitRefExists(ref) {
  if (!ref) {
    return false
  }
  return tryRunGit(['rev-parse', '--verify', '--quiet', `${ref}^{commit}`]).ok
}

function refreshRemoteBranch(branch) {
  if (!branch) {
    return { attempted: false, refreshed: false, reason: '未指定远程目标分支' }
  }
  if (!isRemoteFetchEnabled()) {
    return { attempted: false, refreshed: false, reason: 'MRR_CHANGELOG_FETCH_REMOTE 已禁用' }
  }
  if (!isValidBranchName(branch)) {
    return { attempted: false, refreshed: false, reason: `无效的远程分支名：${branch}` }
  }
  if (!tryRunGit(['remote', 'get-url', 'origin']).ok) {
    return { attempted: false, refreshed: false, reason: '未配置 origin 远程仓库' }
  }

  const fetchArgs = ['fetch', '--quiet', '--prune', '--no-tags']
  const shallowResult = tryRunGit(['rev-parse', '--is-shallow-repository'])
  if (shallowResult.ok && shallowResult.output === 'true') {
    fetchArgs.push(`--deepen=${commitLimit}`)
  }
  fetchArgs.push('origin', `+refs/heads/${branch}:refs/remotes/origin/${branch}`)

  const fetchResult = tryRunGit(fetchArgs)
  if (fetchResult.ok) {
    return { attempted: true, refreshed: true, reason: '' }
  }

  const reason = fetchResult.error?.stderr?.trim()
    || fetchResult.error?.message
    || '未知 Git fetch 错误'
  console.warn(`Unable to refresh origin/${branch}; using available Git refs: ${reason}`)
  return { attempted: true, refreshed: false, reason }
}

export function chooseCommitRef({ currentBranch, targetBranch, remoteRefAvailable, remoteRefFresh }) {
  const normalizedTargetBranch = normalizeBranchName(targetBranch)
  if (normalizedTargetBranch && remoteRefAvailable) {
    return {
      ref: `refs/remotes/origin/${normalizedTargetBranch}`,
      branch: normalizedTargetBranch,
      source: remoteRefFresh ? 'remote' : 'remote-cache',
    }
  }

  const normalizedCurrentBranch = normalizeBranchName(currentBranch)
  return {
    ref: normalizedCurrentBranch && normalizedCurrentBranch !== 'HEAD'
      ? `refs/heads/${normalizedCurrentBranch}`
      : 'HEAD',
    branch: normalizedCurrentBranch || 'HEAD',
    source: 'local',
  }
}

function extractPullRequestNumber(...subjects) {
  for (const subject of subjects) {
    if (!subject) {
      continue
    }

    const mergeMatch = subject.match(/^Merge pull request #(\d+)/i)
    if (mergeMatch) {
      return mergeMatch[1]
    }

    const squashMatch = subject.match(/\(#(\d+)\)\s*$/)
    if (squashMatch) {
      return squashMatch[1]
    }
  }

  return null
}

export function parseConventionalSubject(subject, originalSubject = subject) {
  const conventionalMatch = subject.match(/^([a-zA-Z]+)(?:\(([^)]+)\))?(!)?:\s*(.+)$/)
  const pullRequest = extractPullRequestNumber(originalSubject, subject)

  if (conventionalMatch) {
    const rawType = conventionalMatch[1].toLowerCase()
    const type = TYPE_META[rawType] ? rawType : 'other'
    return {
      type,
      scope: conventionalMatch[2]?.trim() ?? '',
      breaking: Boolean(conventionalMatch[3]),
      description: conventionalMatch[4].replace(/\s*\(#\d+\)\s*$/, '').trim(),
      pullRequest,
    }
  }

  return {
    type: /^Merge\b/i.test(originalSubject) ? 'merge' : 'other',
    scope: '',
    breaking: false,
    description: subject.replace(/\s*\(#\d+\)\s*$/, '').trim(),
    pullRequest,
  }
}

function effectiveSubject(subject, body) {
  if (!/^Merge\b/i.test(subject)) {
    return subject
  }

  const summary = body
    .split(/\r?\n/)
    .map(line => line.trim())
    .find(line => line && !/^Co-authored-by:/i.test(line))

  return summary ?? subject
}

export function parseLog(rawLog) {
  if (!rawLog) {
    return []
  }

  return rawLog
    .split('\x1e')
    .map(record => record.trim())
    .filter(Boolean)
    .map((record) => {
      const [hash, shortHash, date, author, subject, body = '', parents = ''] = record.split('\x1f')
      const displaySubject = effectiveSubject(subject, body)
      const parsedSubject = parseConventionalSubject(displaySubject, subject)

      return {
        kind: 'commit',
        hash,
        shortHash,
        date,
        author,
        parents: parents.trim().split(/\s+/).filter(Boolean),
        originalSubject: subject,
        ...parsedSubject,
      }
    })
}

export function extractIssueReferences(body = '') {
  const references = new Map()

  for (const rawLine of body.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line) {
      continue
    }

    const closes = /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\b/i.test(line)
    const related = /\b(?:related\s+to|relates\s+to|references?)\b/i.test(line)
      || /(?:关联|相关)/.test(line)

    if (!closes && !related) {
      continue
    }

    for (const match of line.matchAll(/#(\d+)/g)) {
      const number = Number.parseInt(match[1], 10)
      const relationship = closes ? 'closes' : 'related'
      if (references.get(number) !== 'closes') {
        references.set(number, relationship)
      }
    }
  }

  return [...references.entries()].map(([number, relationship]) => ({ number, relationship }))
}

function isGitHubEnabled() {
  const configured = process.env.MRR_CHANGELOG_GITHUB?.trim().toLowerCase()
  if (configured === 'false' || configured === '0' || configured === 'off') {
    return false
  }
  if (configured === 'true' || configured === '1' || configured === 'on') {
    return true
  }
  return Boolean(process.env.GITHUB_TOKEN || process.env.GH_TOKEN)
}

function readGitHubCache(repositorySlug, branch, cacheTtlSeconds) {
  if (!existsSync(cachePath)) {
    return null
  }

  try {
    const cached = JSON.parse(readFileSync(cachePath, 'utf8'))
    if (cached.repository !== repositorySlug || cached.branch !== branch || !cached.fetchedAt) {
      return null
    }

    const ageMilliseconds = Date.now() - Date.parse(cached.fetchedAt)
    return {
      ...cached,
      fresh: Number.isFinite(ageMilliseconds) && ageMilliseconds <= cacheTtlSeconds * 1000,
    }
  }
  catch (error) {
    console.warn(`Unable to read GitHub changelog cache: ${error.message}`)
    return null
  }
}

function writeGitHubCache(data) {
  try {
    mkdirSync(path.dirname(cachePath), { recursive: true })
    writeFileSync(cachePath, `${JSON.stringify(data, null, 2)}\n`, 'utf8')
  }
  catch (error) {
    console.warn(`Unable to write GitHub changelog cache: ${error.message}`)
  }
}

async function fetchGitHubJson(url, token) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 15_000)

  try {
    const headers = {
      Accept: 'application/vnd.github+json',
      'User-Agent': 'MRR-VitePress-Changelog',
      'X-GitHub-Api-Version': '2022-11-28',
    }
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }

    const response = await fetch(url, { headers, signal: controller.signal })
    if (!response.ok) {
      const details = await response.text()
      throw new Error(`GitHub API ${response.status}: ${details.slice(0, 200)}`)
    }
    return response.json()
  }
  finally {
    clearTimeout(timeout)
  }
}

async function fetchPaginatedGitHubItems({ endpoint, token, maximumPages, oldestDate }) {
  const items = []

  for (let page = 1; page <= maximumPages; page += 1) {
    const separator = endpoint.includes('?') ? '&' : '?'
    const pageItems = await fetchGitHubJson(`${endpoint}${separator}per_page=100&page=${page}`, token)
    if (!Array.isArray(pageItems)) {
      throw new TypeError('GitHub API returned an unexpected payload.')
    }

    items.push(...pageItems)
    if (pageItems.length < 100) {
      break
    }

    const lastUpdatedAt = pageItems.at(-1)?.updated_at
    if (oldestDate && lastUpdatedAt && lastUpdatedAt.slice(0, 10) < oldestDate) {
      break
    }
  }

  return items
}

function normalizePullRequest(pullRequest) {
  return {
    number: pullRequest.number,
    title: pullRequest.title ?? `Pull Request #${pullRequest.number}`,
    body: pullRequest.body ?? '',
    url: pullRequest.html_url,
    mergedAt: pullRequest.merged_at,
    mergeCommitSha: pullRequest.merge_commit_sha,
    headSha: pullRequest.head?.sha ?? null,
    baseBranch: pullRequest.base?.ref ?? '',
    author: pullRequest.user?.login ?? 'unknown',
    labels: (pullRequest.labels ?? []).map(label => label.name).filter(Boolean),
  }
}

function normalizeIssue(issue) {
  return {
    number: issue.number,
    title: issue.title ?? `Issue #${issue.number}`,
    url: issue.html_url,
    closedAt: issue.closed_at,
    stateReason: issue.state_reason ?? 'completed',
    author: issue.user?.login ?? 'unknown',
  }
}

async function fetchGitHubChangelogData({ repositorySlug, branch, oldestDate }) {
  const cacheTtlSeconds = parseBoundedInteger(process.env.MRR_CHANGELOG_CACHE_TTL, 1800, 86_400)
  const maximumPages = parseBoundedInteger(process.env.MRR_CHANGELOG_GITHUB_PAGES, 10, 20)
  const cached = readGitHubCache(repositorySlug, branch, cacheTtlSeconds)
  if (cached?.fresh) {
    return { ...cached, source: 'cache' }
  }

  const token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN || ''
  const apiBase = (process.env.MRR_GITHUB_API_URL ?? 'https://api.github.com').replace(/\/$/, '')
  const encodedRepository = repositorySlug.split('/').map(encodeURIComponent).join('/')
  const baseQuery = branch ? `&base=${encodeURIComponent(branch)}` : ''

  try {
    const pullsEndpoint = `${apiBase}/repos/${encodedRepository}/pulls?state=closed&sort=updated&direction=desc${baseQuery}`
    const issuesEndpoint = `${apiBase}/repos/${encodedRepository}/issues?state=closed&sort=updated&direction=desc`
    const [rawPullRequests, rawIssues] = await Promise.all([
      fetchPaginatedGitHubItems({ endpoint: pullsEndpoint, token, maximumPages, oldestDate }),
      fetchPaginatedGitHubItems({ endpoint: issuesEndpoint, token, maximumPages, oldestDate }),
    ])

    const data = {
      repository: repositorySlug,
      branch,
      fetchedAt: new Date().toISOString(),
      pullRequests: rawPullRequests.filter(item => item.merged_at).map(normalizePullRequest),
      issues: rawIssues
        .filter(item => !item.pull_request && item.closed_at && item.state_reason !== 'not_planned')
        .map(normalizeIssue),
    }
    writeGitHubCache(data)
    return { ...data, source: 'api' }
  }
  catch (error) {
    if (cached) {
      console.warn(`Unable to refresh GitHub changelog data; using stale cache: ${error.message}`)
      return { ...cached, source: 'stale-cache', warning: error.message }
    }
    throw error
  }
}

function issueReferenceWithDetails(reference, issueByNumber) {
  const issue = issueByNumber.get(reference.number)
  return {
    ...reference,
    title: issue?.title ?? '',
    url: issue?.url ?? null,
  }
}

export function combineChangelogEntries(commits, githubData, repositorySlug) {
  if (!githubData) {
    return commits
  }

  const pullRequestByNumber = new Map(githubData.pullRequests.map(pr => [pr.number, pr]))
  const pullRequestByCommit = new Map()
  for (const pullRequest of githubData.pullRequests) {
    if (pullRequest.mergeCommitSha) {
      pullRequestByCommit.set(pullRequest.mergeCommitSha, pullRequest)
    }
    if (pullRequest.headSha) {
      pullRequestByCommit.set(pullRequest.headSha, pullRequest)
    }
  }

  const issueByNumber = new Map(githubData.issues.map(issue => [issue.number, issue]))
  const matchedPullRequests = new Set()
  const referencedIssues = new Set()

  const enrichedCommits = commits.map((commit) => {
    const pullRequest = (commit.pullRequest
      ? pullRequestByNumber.get(Number.parseInt(commit.pullRequest, 10))
      : null) ?? pullRequestByCommit.get(commit.hash)

    if (!pullRequest) {
      return commit
    }

    matchedPullRequests.add(pullRequest.number)
    const parsedTitle = parseConventionalSubject(pullRequest.title, pullRequest.title)
    const issueReferences = extractIssueReferences(pullRequest.body).map((reference) => {
      referencedIssues.add(reference.number)
      return issueReferenceWithDetails(reference, issueByNumber)
    })

    return {
      ...commit,
      ...parsedTitle,
      pullRequest: String(pullRequest.number),
      pullRequestUrl: pullRequest.url,
      author: pullRequest.author || commit.author,
      issueReferences,
    }
  })

  const oldestDate = commits.at(-1)?.date ?? '0000-00-00'
  const latestDate = commits[0]?.date ?? '9999-99-99'
  const unmatchedPullRequests = githubData.pullRequests
    .filter(pullRequest => !matchedPullRequests.has(pullRequest.number))
    .filter(pullRequest => pullRequest.mergedAt)
    .filter((pullRequest) => {
      const date = pullRequest.mergedAt.slice(0, 10)
      return date >= oldestDate && date <= latestDate
    })
    .map((pullRequest) => {
      const parsedTitle = parseConventionalSubject(pullRequest.title, pullRequest.title)
      const issueReferences = extractIssueReferences(pullRequest.body).map((reference) => {
        referencedIssues.add(reference.number)
        return issueReferenceWithDetails(reference, issueByNumber)
      })
      return {
        kind: 'pull-request',
        date: pullRequest.mergedAt.slice(0, 10),
        author: pullRequest.author,
        pullRequest: String(pullRequest.number),
        pullRequestUrl: pullRequest.url,
        issueReferences,
        ...parsedTitle,
      }
    })

  const standaloneIssues = githubData.issues
    .filter(issue => !referencedIssues.has(issue.number))
    .filter(issue => issue.closedAt)
    .filter((issue) => {
      const date = issue.closedAt.slice(0, 10)
      return date >= oldestDate && date <= latestDate
    })
    .map(issue => ({
      kind: 'issue',
      date: issue.closedAt.slice(0, 10),
      number: issue.number,
      title: issue.title,
      url: issue.url ?? `https://github.com/${repositorySlug}/issues/${issue.number}`,
    }))

  return [...enrichedCommits, ...unmatchedPullRequests, ...standaloneIssues]
}

function groupByDate(entries) {
  const groups = new Map()
  const sortedEntries = [...entries].sort((left, right) => {
    const dateComparison = right.date.localeCompare(left.date)
    if (dateComparison !== 0) {
      return dateComparison
    }
    const leftKindOrder = left.kind === 'commit' ? 0 : left.kind === 'pull-request' ? 1 : 2
    const rightKindOrder = right.kind === 'commit' ? 0 : right.kind === 'pull-request' ? 1 : 2
    return leftKindOrder - rightKindOrder
  })

  for (const entry of sortedEntries) {
    const current = groups.get(entry.date) ?? []
    current.push(entry)
    groups.set(entry.date, current)
  }
  return groups
}

function renderIssueReference(reference, repositorySlug) {
  const relationship = reference.relationship === 'closes' ? '完成' : '关联'
  const issueUrl = reference.url ?? `https://github.com/${repositorySlug}/issues/${reference.number}`
  const title = reference.title ? `：${escapeMarkdown(reference.title)}` : ''
  return `${relationship} [#${reference.number}${title}](${issueUrl})`
}

function renderChangeEntry(entry, repositorySlug) {
  const metadata = TYPE_META[entry.type] ?? TYPE_META.other
  const scope = entry.scope ? ` \`${escapeMarkdown(entry.scope)}\`` : ''
  const breaking = entry.breaking ? ' **BREAKING**' : ''
  const description = escapeMarkdown(entry.description)
  const links = []

  if (entry.kind === 'commit') {
    const commitUrl = `https://github.com/${repositorySlug}/commit/${entry.hash}`
    links.push(`[\`${entry.shortHash}\`](${commitUrl})`)
  }

  if (entry.pullRequest) {
    const pullRequestUrl = entry.pullRequestUrl
      ?? `https://github.com/${repositorySlug}/pull/${entry.pullRequest}`
    links.push(`[PR #${entry.pullRequest}](${pullRequestUrl})`)
  }

  for (const issueReference of entry.issueReferences ?? []) {
    links.push(renderIssueReference(issueReference, repositorySlug))
  }

  const linkText = links.length > 0 ? `（${links.join(' · ')}）` : ''
  const author = entry.author ? `— ${escapeMarkdown(entry.author)}` : ''
  return `- **${metadata.label}**${scope}${breaking} ${description}${linkText}${author ? ` ${author}` : ''}`
}

function renderIssueEntry(entry) {
  return `- **Issue** 已完成 [#${entry.number}：${escapeMarkdown(entry.title)}](${entry.url})`
}

function renderCommitSource(commitSource, branch) {
  if (commitSource?.source === 'remote') {
    return `远程 \`origin/${escapeMarkdown(commitSource.branch)}\`（已刷新）`
  }
  if (commitSource?.source === 'remote-cache') {
    return `远程 \`origin/${escapeMarkdown(commitSource.branch)}\`（使用现有远程引用）`
  }
  return `本地 \`${escapeMarkdown(commitSource?.branch ?? branch)}\`（远程分支不可用）`
}

export function renderDocument({ commits, entries, branch, githubBranch, repositorySlug, githubStatus, commitSource }) {
  const latest = commits[0]
  const groups = groupByDate(entries)
  const githubSummary = githubStatus.enabled
    ? `已启用（${githubStatus.pullRequestCount} 个已合并 PR、${githubStatus.issueCount} 个已完成 Issue；来源：${githubStatus.source}）`
    : githubStatus.reason
      ? `已降级为纯 Git 日志（${escapeMarkdown(githubStatus.reason)}）`
      : '未启用（仅 Git 提交）'

  const lines = [
    '---',
    'title: 更新记录',
    'description: 从 Git 提交历史及 GitHub Pull Request、Issue 自动生成的 MRR 项目更新记录',
    'outline: [2, 3]',
    '---',
    '',
    '# 更新记录',
    '',
    '> 本页由 `vitepress-doc/scripts/generate-git-changelog.mjs` 自动生成，请勿手工维护更新条目。默认刷新并读取远程 `main` 提交；远程不可用时才降级为已有远程引用或本地 Git 历史。',
    '',
    `- 当前分支：\`${escapeMarkdown(branch)}\``,
    `- GitHub 目标分支：${githubBranch ? `\`${escapeMarkdown(githubBranch)}\`` : '未指定'}`,
    `- Git 提交来源：${renderCommitSource(commitSource, branch)}`,
    `- 更新至：${latest ? `${latest.date} · \`${latest.shortHash}\`` : '暂无提交'}`,
    `- 记录范围：最近 ${commits.length} 条第一父级提交（上限 ${commitLimit} 条）`,
    `- GitHub 增强：${githubSummary}`,
    '',
  ]

  if (entries.length === 0) {
    lines.push('当前没有可展示的更新记录。', '')
  }
  else {
    for (const [date, dateEntries] of groups) {
      lines.push(`## ${date}`, '')
      for (const entry of dateEntries) {
        lines.push(entry.kind === 'issue'
          ? renderIssueEntry(entry)
          : renderChangeEntry(entry, repositorySlug))
      }
      lines.push('')
    }
  }

  lines.push(
    '## 生成方式',
    '',
    '### Windows 推荐方式',
    '',
    '在 `vitepress-doc` 目录执行：',
    '',
    '```powershell',
    '.\\update-changelog.ps1',
    '```',
    '',
    '脚本会刷新远程目标分支，优先通过 GitHub CLI 获取令牌，生成更新日志并显示文件差异。使用 `-SkipPull` 可跳过额外的当前分支拉取，使用 `-GitOnly` 可禁用 GitHub PR/Issue 增强。',
    '',
    '### 通用方式',
    '',
    '```bash',
    'npm run docs:changelog',
    '```',
    '',
    '`docs:dev:user`、`docs:dev:internal`、`docs:build:user` 和 `docs:build:internal` 也会在启动或构建前自动刷新本页。',
    '',
    '可用环境变量：',
    '',
    '- `GITHUB_TOKEN` 或 `GH_TOKEN`：访问私有仓库的 GitHub 令牌；',
    '- `MRR_CHANGELOG_GITHUB`：设置为 `true`/`false` 强制启用或禁用 GitHub PR/Issue 增强；',
    '- `MRR_CHANGELOG_BASE_BRANCH`：指定提交记录和 PR 的远程目标分支，默认 `main`；',
    '- `MRR_CHANGELOG_FETCH_REMOTE`：设置为 `false` 可禁止执行 `git fetch`，但仍优先使用已有 `origin/<目标分支>`；',
    '- `MRR_CHANGELOG_LIMIT`：Git 提交数量，允许范围为 1～1000；',
    '- `MRR_CHANGELOG_CACHE_TTL`：GitHub 本地缓存秒数，默认 1800；',
    '- `MRR_CHANGELOG_GITHUB_PAGES`：GitHub 分页上限，默认 10，最大 20。',
    '',
  )

  return `${lines.join('\n')}\n`
}

function writeFallbackDocument() {
  if (existsSync(outputPath)) {
    console.warn('Git history is unavailable; keeping the committed changelog snapshot.')
    return
  }

  mkdirSync(path.dirname(outputPath), { recursive: true })
  writeFileSync(outputPath, [
    '# 更新记录',
    '',
    '> 当前环境无法读取 Git 历史。请在完整 Git 仓库中执行 `npm run docs:changelog` 生成更新记录。',
    '',
  ].join('\n'), 'utf8')
}

export async function main() {
  try {
    runGit(['rev-parse', '--is-inside-work-tree'])

    const branch = runGit(['rev-parse', '--abbrev-ref', 'HEAD']) || 'HEAD'
    const repositorySlug = resolveRepositorySlug()
    const githubBranch = resolveGitHubBranch(branch)
    const fetchStatus = refreshRemoteBranch(githubBranch)
    const remoteRef = githubBranch ? `refs/remotes/origin/${githubBranch}` : ''
    const commitSource = chooseCommitRef({
      currentBranch: branch,
      targetBranch: githubBranch,
      remoteRefAvailable: gitRefExists(remoteRef),
      remoteRefFresh: fetchStatus.refreshed,
    })

    if (commitSource.source === 'local' && fetchStatus.reason) {
      console.warn(`Remote changelog source unavailable; falling back to ${commitSource.ref}: ${fetchStatus.reason}`)
    }

    const prettyFormat = '%H%x1f%h%x1f%ad%x1f%an%x1f%s%x1f%b%x1f%P%x1e'
    const rawLog = runGit([
      'log',
      '--first-parent',
      `--max-count=${commitLimit}`,
      '--date=short',
      `--pretty=format:${prettyFormat}`,
      commitSource.ref,
    ])
    const commits = parseLog(rawLog)
    let githubData = null
    let githubStatus = { enabled: false }

    if (isGitHubEnabled()) {
      try {
        githubData = await fetchGitHubChangelogData({
          repositorySlug,
          branch: githubBranch,
          oldestDate: commits.at(-1)?.date,
        })
        githubStatus = {
          enabled: true,
          source: githubData.source,
          pullRequestCount: githubData.pullRequests.length,
          issueCount: githubData.issues.length,
        }
      }
      catch (error) {
        console.warn(`Unable to enrich changelog with GitHub data: ${error.message}`)
        githubStatus = { enabled: false, reason: error.message }
      }
    }

    const entries = combineChangelogEntries(commits, githubData, repositorySlug)
    mkdirSync(path.dirname(outputPath), { recursive: true })
    writeFileSync(outputPath, renderDocument({
      commits,
      entries,
      branch,
      githubBranch,
      repositorySlug,
      githubStatus,
      commitSource,
    }), 'utf8')
    console.log(`Generated ${path.relative(repositoryRoot, outputPath)} from ${commits.length} Git commits at ${commitSource.ref}${githubData ? `, ${githubData.pullRequests.length} pull requests and ${githubData.issues.length} issues` : ''}.`)
  }
  catch (error) {
    console.warn(`Unable to generate Git changelog: ${error.message}`)
    writeFallbackDocument()
  }
}

const isMainModule = process.argv[1]
  && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href
if (isMainModule) {
  await main()
}
