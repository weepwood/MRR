import { spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { fileURLToPath } from 'node:url'

const MODES = new Set(['user', 'internal'])
const COMMANDS = new Set(['dev', 'build', 'preview'])
const NETWORK_COMMANDS = new Set(['dev', 'preview'])
const MAX_PORT_ATTEMPTS = 2048

const [mode = 'user', command = 'dev', ...forwardedArgs] = process.argv.slice(2)
if (!MODES.has(mode) || !COMMANDS.has(command)) {
  console.error('Usage: node scripts/run-docs.mjs <user|internal> <dev|build|preview> [vitepress options]')
  process.exit(1)
}

const projectRoot = fileURLToPath(new URL('../', import.meta.url))
const vitepressCli = fileURLToPath(new URL('../node_modules/vitepress/bin/vitepress.js', import.meta.url))

function readOption(args, name) {
  const equalsPrefix = `${name}=`
  const equalsValue = args.find(arg => arg.startsWith(equalsPrefix))
  if (equalsValue) {
    return equalsValue.slice(equalsPrefix.length)
  }

  const index = args.indexOf(name)
  if (index === -1) {
    return undefined
  }

  const next = args[index + 1]
  return next && !next.startsWith('-') ? next : ''
}

function removeOption(args, name) {
  const result = []

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index]
    if (arg.startsWith(`${name}=`)) {
      continue
    }
    if (arg === name) {
      const next = args[index + 1]
      if (next && !next.startsWith('-')) {
        index += 1
      }
      continue
    }
    result.push(arg)
  }

  return result
}

function probePort(host, port) {
  return new Promise((resolve, reject) => {
    const server = createServer()
    server.unref()

    server.once('error', (error) => {
      if (error.code === 'EACCES' || error.code === 'EADDRINUSE') {
        resolve({ available: false, code: error.code })
        return
      }
      reject(error)
    })

    server.listen({ host, port, exclusive: true }, () => {
      server.close(() => resolve({ available: true }))
    })
  })
}

async function resolveNetworkArgs(args) {
  const hostOption = readOption(args, '--host')
  const host = hostOption === '' ? '0.0.0.0' : (hostOption ?? '127.0.0.1')
  const portOption = readOption(args, '--port')
  const defaultPort = command === 'preview' ? 4173 : 5173
  const requestedPort = portOption === undefined || portOption === ''
    ? defaultPort
    : Number.parseInt(portOption, 10)

  if (!Number.isInteger(requestedPort) || requestedPort < 1 || requestedPort > 65535) {
    throw new Error(`Invalid port: ${portOption}`)
  }

  let selectedPort = requestedPort
  let unavailableCode
  let attempts = 0

  while (selectedPort <= 65535 && attempts < MAX_PORT_ATTEMPTS) {
    const probe = await probePort(host, selectedPort)
    if (probe.available) {
      break
    }
    unavailableCode ??= probe.code
    selectedPort += 1
    attempts += 1
  }

  if (selectedPort > 65535 || attempts >= MAX_PORT_ATTEMPTS) {
    throw new Error(
      `No available TCP port found in ${requestedPort}-${Math.min(requestedPort + MAX_PORT_ATTEMPTS - 1, 65535)} on ${host}`,
    )
  }

  if (selectedPort !== requestedPort) {
    console.warn(
      `Port ${requestedPort} is unavailable (${unavailableCode ?? 'unknown'}); using ${host}:${selectedPort} instead.`,
    )
  }

  return [
    ...removeOption(removeOption(args, '--host'), '--port'),
    '--host',
    host,
    '--port',
    String(selectedPort),
  ]
}

let vitepressArgs = forwardedArgs
try {
  if (NETWORK_COMMANDS.has(command)) {
    vitepressArgs = await resolveNetworkArgs(forwardedArgs)
  }
}
catch (error) {
  console.error(`Failed to resolve VitePress network options: ${error.message}`)
  process.exit(1)
}

const child = spawn(
  process.execPath,
  [vitepressCli, command, '.', ...vitepressArgs],
  {
    cwd: projectRoot,
    env: {
      ...process.env,
      MRR_DOCS_MODE: mode,
    },
    stdio: 'inherit',
  },
)

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
