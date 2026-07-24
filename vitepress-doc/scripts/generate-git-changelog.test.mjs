import test from 'node:test'
import assert from 'node:assert/strict'

import {
  chooseCommitRef,
  combineChangelogEntries,
  extractIssueReferences,
  normalizeBranchName,
  normalizeRepositorySlug,
  parseConventionalSubject,
  renderDocument,
} from './generate-git-changelog.mjs'

test('normalizes SSH and HTTPS GitHub remotes', () => {
  assert.equal(normalizeRepositorySlug('git@github.com:weepwood/MRR.git'), 'weepwood/MRR')
  assert.equal(normalizeRepositorySlug('https://github.com/weepwood/MRR.git'), 'weepwood/MRR')
})

test('normalizes local and remote branch refs', () => {
  assert.equal(normalizeBranchName('refs/remotes/origin/main'), 'main')
  assert.equal(normalizeBranchName('origin/main'), 'main')
  assert.equal(normalizeBranchName('refs/heads/main'), 'main')
})

test('prefers the refreshed remote target branch over the local branch', () => {
  assert.deepEqual(chooseCommitRef({
    currentBranch: 'feature/docs',
    targetBranch: 'main',
    remoteRefAvailable: true,
    remoteRefFresh: true,
  }), {
    ref: 'refs/remotes/origin/main',
    branch: 'main',
    source: 'remote',
  })
})

test('uses an existing remote ref when fetch is unavailable', () => {
  assert.equal(chooseCommitRef({
    currentBranch: 'feature/docs',
    targetBranch: 'main',
    remoteRefAvailable: true,
    remoteRefFresh: false,
  }).source, 'remote-cache')
})

test('falls back to the current local branch only when no remote ref exists', () => {
  assert.deepEqual(chooseCommitRef({
    currentBranch: 'feature/docs',
    targetBranch: 'main',
    remoteRefAvailable: false,
    remoteRefFresh: false,
  }), {
    ref: 'refs/heads/feature/docs',
    branch: 'feature/docs',
    source: 'local',
  })
})

test('parses squash PR numbers and removes the suffix from descriptions', () => {
  assert.deepEqual(parseConventionalSubject('fix(startup): allow slow startup (#164)'), {
    type: 'fix',
    scope: 'startup',
    breaking: false,
    description: 'allow slow startup',
    pullRequest: '164',
  })
})

test('extracts closing and related issue references with closing taking priority', () => {
  assert.deepEqual(extractIssueReferences(`
Closes #162 and #163
Related to #142
关联 #142 和 #156
Fixes #156
`), [
    { number: 162, relationship: 'closes' },
    { number: 163, relationship: 'closes' },
    { number: 142, relationship: 'related' },
    { number: 156, relationship: 'closes' },
  ])
})

test('enriches commits with PR titles and linked issues without duplicating them', () => {
  const commits = [{
    kind: 'commit',
    hash: 'abc123',
    shortHash: 'abc123',
    date: '2026-07-21',
    author: 'Local Author',
    type: 'merge',
    scope: '',
    breaking: false,
    description: 'Merge pull request #164',
    pullRequest: '164',
  }]
  const githubData = {
    pullRequests: [{
      number: 164,
      title: 'fix(startup): 慢网络启动超时改为可继续等待',
      body: 'Closes #162\nRelated to #142',
      url: 'https://github.com/weepwood/MRR/pull/164',
      mergedAt: '2026-07-21T04:45:41Z',
      mergeCommitSha: 'abc123',
      headSha: 'head123',
      author: 'weepwood',
      labels: [],
    }],
    issues: [
      { number: 162, title: '慢网络启动超时恢复', url: 'https://github.com/weepwood/MRR/issues/162', closedAt: '2026-07-21T04:45:41Z' },
      { number: 142, title: '关联数据治理', url: 'https://github.com/weepwood/MRR/issues/142', closedAt: '2026-07-20T04:45:41Z' },
    ],
  }

  const entries = combineChangelogEntries(commits, githubData, 'weepwood/MRR')
  assert.equal(entries.length, 1)
  assert.equal(entries[0].description, '慢网络启动超时改为可继续等待')
  assert.equal(entries[0].type, 'fix')
  assert.deepEqual(entries[0].issueReferences.map(item => [item.number, item.relationship]), [
    [162, 'closes'],
    [142, 'related'],
  ])
})

test('renders remote commit source with PR and Issue information', () => {
  const commits = [{
    kind: 'commit',
    hash: 'abc123',
    shortHash: 'abc123',
    date: '2026-07-21',
    author: 'weepwood',
    type: 'fix',
    scope: 'startup',
    breaking: false,
    description: '慢网络启动超时改为可继续等待',
    pullRequest: '164',
    pullRequestUrl: 'https://github.com/weepwood/MRR/pull/164',
    issueReferences: [{
      number: 162,
      relationship: 'closes',
      title: '慢网络启动超时恢复',
      url: 'https://github.com/weepwood/MRR/issues/162',
    }],
  }]

  const rendered = renderDocument({
    commits,
    entries: commits,
    branch: 'feature/docs',
    githubBranch: 'main',
    repositorySlug: 'weepwood/MRR',
    githubStatus: { enabled: true, source: 'api', pullRequestCount: 1, issueCount: 1 },
    commitSource: { ref: 'refs/remotes/origin/main', branch: 'main', source: 'remote' },
  })

  assert.match(rendered, /PR #164/)
  assert.match(rendered, /完成 \[#162：慢网络启动超时恢复\]/)
  assert.match(rendered, /GitHub 增强：已启用/)
  assert.match(rendered, /Git 提交来源：远程 `origin\/main`（已刷新）/)
  assert.match(rendered, /\\update-changelog\.ps1/)
})
