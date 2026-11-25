import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const styleDir = path.dirname(fileURLToPath(import.meta.url))

/** Read the actual bridge cascade, following the declared import order. */
export function readElementPlusCss() {
  const entry = fs.readFileSync(path.join(styleDir, 'element-plus-overrides.css'), 'utf8')
  return entry.replace(/@import "\.\/(element-plus\/[a-z-]+\.css)";/g, (_, file: string) => fs.readFileSync(path.join(styleDir, file), 'utf8'))
}
