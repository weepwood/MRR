import { execFileSync, spawnSync } from 'node:child_process'
import path from 'node:path'

const frontendRoot = process.cwd()
const repositoryRoot = path.resolve(frontendRoot, '..')
const headSha = process.env.LINT_HEAD_SHA?.trim() || 'HEAD'
const requestedBaseSha = process.env.LINT_BASE_SHA?.trim()
const baseSha = requestedBaseSha && !/^0+$/.test(requestedBaseSha)
  ? requestedBaseSha
  : `${headSha}^`
const shouldFix = process.argv.includes('--fix')

function getChangedFiles() {
  const output = execFileSync(
    'git',
    [
      'diff',
      '--name-only',
      '--diff-filter=ACMR',
      '-z',
      baseSha,
      headSha,
      '--',
      'frontend-fantastic-admin/src',
    ],
    { cwd: repositoryRoot },
  )

  return output
    .toString('utf8')
    .split('\0')
    .filter(Boolean)
    .map(file => path.relative(frontendRoot, path.resolve(repositoryRoot, file)).split(path.sep).join('/'))
}

function run(command, files) {
  if (!files.length) {
    console.log(`[lint-changed] No changed files for ${command}.`)
    return
  }

  console.log(`[lint-changed] ${command}${shouldFix ? ' --fix' : ''}: ${files.join(', ')}`)
  const args = ['exec', command, ...(shouldFix ? ['--fix'] : []), ...files]
  const result = spawnSync('pnpm', args, {
    cwd: frontendRoot,
    stdio: 'inherit',
  })

  if (result.error) {
    throw result.error
  }
  if (result.status !== 0) {
    process.exitCode = 1
  }
}

const changedFiles = getChangedFiles()
const eslintFiles = changedFiles.filter(file => /\.(?:[cm]?[jt]sx?|vue)$/.test(file))
const stylelintFiles = changedFiles.filter(file => /\.(?:css|scss|vue)$/.test(file))

run('eslint', eslintFiles)
run('stylelint', stylelintFiles)
