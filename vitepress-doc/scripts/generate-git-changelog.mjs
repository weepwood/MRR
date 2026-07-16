import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptsRoot = path.dirname(fileURLToPath(import.meta.url))
const docsRoot = path.resolve(scriptsRoot, '..')
const repositoryRoot = path.resolve(docsRoot, '..')
const outputPath = path.join(docsRoot, 'user-guide', 'changelog.md')

const parsedLimit = Number.parseInt(process.env.MRR_CHANGELOG_LIMIT ?? '200', 10)
const commitLimit = Number.isInteger(parsedLimit) && parsedLimit > 0
  ? Math.min(parsedLimit, 1000)
  : 200

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

function runGit(args) {
  return execFileSync('git', args, {
    cwd: repositoryRoot,
    encoding: 'utf8',
    maxBuffer: 32 * 1024 * 1024,
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim()
}

function escapeMarkdown(value) {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/([`*_[\]<>])/g, '\\$1')
}

function normalizeRepositorySlug(remoteUrl) {
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

function parseConventionalSubject(subject, originalSubject) {
  const conventionalMatch = subject.match(/^([a-zA-Z]+)(?:\(([^)]+)\))?(!)?:\s*(.+)$/)
  const mergeMatch = originalSubject.match(/^Merge pull request #(\d+)/i)

  if (conventionalMatch) {
    const rawType = conventionalMatch[1].toLowerCase()
    const type = TYPE_META[rawType] ? rawType : 'other'
    return {
      type,
      scope: conventionalMatch[2]?.trim() ?? '',
      breaking: Boolean(conventionalMatch[3]),
      description: conventionalMatch[4].trim(),
      pullRequest: mergeMatch?.[1] ?? null,
    }
  }

  return {
    type: /^Merge\b/i.test(originalSubject) ? 'merge' : 'other',
    scope: '',
    breaking: false,
    description: subject,
    pullRequest: mergeMatch?.[1] ?? null,
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

function parseLog(rawLog) {
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

function groupByDate(commits) {
  const groups = new Map()
  for (const commit of commits) {
    const current = groups.get(commit.date) ?? []
    current.push(commit)
    groups.set(commit.date, current)
  }
  return groups
}

function renderCommit(commit, repositorySlug) {
  const metadata = TYPE_META[commit.type] ?? TYPE_META.other
  const scope = commit.scope ? ` \`${escapeMarkdown(commit.scope)}\`` : ''
  const breaking = commit.breaking ? ' **BREAKING**' : ''
  const description = escapeMarkdown(commit.description)
  const commitUrl = `https://github.com/${repositorySlug}/commit/${commit.hash}`
  const commitLink = `[\`${commit.shortHash}\`](${commitUrl})`
  const pullRequestLink = commit.pullRequest
    ? ` · [#${commit.pullRequest}](https://github.com/${repositorySlug}/pull/${commit.pullRequest})`
    : ''
  const author = escapeMarkdown(commit.author)

  return `- **${metadata.label}**${scope}${breaking} ${description}（${commitLink}${pullRequestLink}）— ${author}`
}

function renderDocument(commits, branch, repositorySlug) {
  const latest = commits[0]
  const groups = groupByDate(commits)
  const lines = [
    '---',
    'title: 更新记录',
    'description: 从 Git 第一父级提交历史自动生成的 MRR 项目更新记录',
    'outline: [2, 3]',
    '---',
    '',
    '# 更新记录',
    '',
    '> 本页由 `vitepress-doc/scripts/generate-git-changelog.mjs` 从 Git 提交历史自动生成，请勿手工维护提交列表。',
    '',
    `- 当前分支：\`${escapeMarkdown(branch)}\``,
    `- 更新至：${latest ? `${latest.date} · \`${latest.shortHash}\`` : '暂无提交'}`,
    `- 记录范围：最近 ${commits.length} 条第一父级提交（上限 ${commitLimit} 条）`,
    '- 合并提交：优先使用 Pull Request 描述的首行作为更新内容',
    '',
  ]

  if (commits.length === 0) {
    lines.push('当前没有可展示的 Git 提交记录。', '')
  }
  else {
    for (const [date, dateCommits] of groups) {
      lines.push(`## ${date}`, '')
      for (const commit of dateCommits) {
        lines.push(renderCommit(commit, repositorySlug))
      }
      lines.push('')
    }
  }

  lines.push(
    '## 生成方式',
    '',
    '在 `vitepress-doc` 目录执行：',
    '',
    '```bash',
    'npm run docs:changelog',
    '```',
    '',
    '`docs:dev:user`、`docs:dev:internal`、`docs:build:user` 和 `docs:build:internal` 也会在启动或构建前自动刷新本页。',
    '',
    '可通过环境变量 `MRR_CHANGELOG_LIMIT` 调整记录数量，允许范围为 1～1000。',
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

try {
  runGit(['rev-parse', '--is-inside-work-tree'])

  const prettyFormat = '%H%x1f%h%x1f%ad%x1f%an%x1f%s%x1f%b%x1f%P%x1e'
  const rawLog = runGit([
    'log',
    '--first-parent',
    `--max-count=${commitLimit}`,
    '--date=short',
    `--pretty=format:${prettyFormat}`,
  ])

  const commits = parseLog(rawLog)
  const branch = runGit(['rev-parse', '--abbrev-ref', 'HEAD']) || 'HEAD'
  const repositorySlug = resolveRepositorySlug()

  mkdirSync(path.dirname(outputPath), { recursive: true })
  writeFileSync(outputPath, renderDocument(commits, branch, repositorySlug), 'utf8')
  console.log(`Generated ${path.relative(repositoryRoot, outputPath)} from ${commits.length} Git commits.`)
}
catch (error) {
  console.warn(`Unable to generate Git changelog: ${error.message}`)
  writeFallbackDocument()
}
