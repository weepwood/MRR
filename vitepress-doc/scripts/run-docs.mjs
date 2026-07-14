import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const MODES = new Set(['user', 'internal'])
const COMMANDS = new Set(['dev', 'build', 'preview'])

const [mode = 'user', command = 'dev'] = process.argv.slice(2)
if (!MODES.has(mode) || !COMMANDS.has(command)) {
  console.error('Usage: node scripts/run-docs.mjs <user|internal> <dev|build|preview>')
  process.exit(1)
}

const projectRoot = fileURLToPath(new URL('../', import.meta.url))
const executable = path.join(
  projectRoot,
  'node_modules',
  '.bin',
  process.platform === 'win32' ? 'vitepress.cmd' : 'vitepress',
)

const child = spawn(executable, [command, '.'], {
  cwd: projectRoot,
  env: {
    ...process.env,
    MRR_DOCS_MODE: mode,
  },
  stdio: 'inherit',
  shell: process.platform === 'win32',
})

child.on('error', (error) => {
  console.error(`Failed to start VitePress: ${error.message}`)
  process.exit(1)
})

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal)
    return
  }
  process.exit(code ?? 1)
})
