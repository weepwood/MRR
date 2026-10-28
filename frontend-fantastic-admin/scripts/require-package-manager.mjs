import fs from 'node:fs'
import process from 'node:process'

const { packageManager } = JSON.parse(fs.readFileSync(new URL('../package.json', import.meta.url), 'utf8'))
const expected = packageManager.replace('@', '/')
if ((process.env.npm_config_user_agent || '').split(' ')[0] !== expected) {
  console.error(`Use Corepack with the declared packageManager (${packageManager}).`)
  process.exit(1)
}
