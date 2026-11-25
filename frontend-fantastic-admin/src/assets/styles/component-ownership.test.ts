import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
function sources(dir: string): string[] {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const file = path.join(dir, entry.name)
    if (entry.isDirectory()) { return entry.name === '__tests__' ? [] : sources(file) }
    return /\.(?:vue|ts|tsx|css|scss)$/.test(entry.name) && !/\.test\.[jt]sx?$/.test(entry.name) ? [file] : []
  })
}

describe('唯一基础控件归属', () => {
  it('复选框已全量迁移，不恢复旧实现或直接导入', () => {
    expect(fs.existsSync(path.join(root, 'ui/components/FaCheckbox'))).toBe(false)
    const legacy = sources(root).filter(file => /\bFaCheckbox\b|\.fa-checkbox\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
  })

  it('开关已经迁移到 Element Plus，不能恢复旧实现或旧样式锚点', () => {
    expect(fs.existsSync(path.join(root, 'ui/components/FaSwitch'))).toBe(false)
    const legacy = sources(root).filter(file => /\bFaSwitch\b|\.fa-switch\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
  })
})
