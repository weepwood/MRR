import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/*
 * 令牌引用契约：任何 var(--mrr-*) 都必须能找到定义。
 *
 * 为什么需要这条：自定义属性未定义时，声明在「计算值阶段」失效并退回 unset。
 * 对 border-color 来说 unset 等于 currentColor —— 表现为「按钮描边变成墨色」，
 * 看起来像样式写重了，实际是变量名写错/漏定义，构建与类型检查都发现不了。
 * 2026-11 就出现过 --mrr-border-strong 从未定义、8 处引用静默退化成墨色描边。
 */

const srcDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const SCAN_EXTENSIONS = ['.css', '.vue', '.ts']

function walk(dir: string, files: string[] = []): string[] {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name !== 'node_modules') {
        walk(full, files)
      }
    }
    else if (SCAN_EXTENSIONS.includes(path.extname(entry.name))) {
      files.push(full)
    }
  }
  return files
}

/** 收集所有定义点：CSS 声明、Vue scoped 样式，以及 theme-palette.ts 的变量映射表。 */
function collectDefinitions(files: string[]) {
  const defined = new Set<string>()
  for (const file of files) {
    const content = fs.readFileSync(file, 'utf8')
    for (const match of content.matchAll(/(--mrr-[a-z0-9-]+)['"]?\s*:/g)) {
      defined.add(match[1])
    }
  }
  return defined
}

/** 收集样式表与组件模板里的引用点（.ts 里只有定义，没有 var() 消费）。 */
function collectReferences(files: string[]) {
  const references = new Map<string, Set<string>>()
  for (const file of files) {
    if (path.extname(file) === '.ts') {
      continue
    }
    const content = fs.readFileSync(file, 'utf8')
    for (const match of content.matchAll(/var\((--mrr-[a-z0-9-]+)/g)) {
      const name = match[1]
      // 形如 var(--mrr-radius-${size}) 的动态拼接无法静态校验，跳过。
      if (name.endsWith('-')) {
        continue
      }
      if (!references.has(name)) {
        references.set(name, new Set())
      }
      references.get(name)!.add(path.relative(srcDir, file).replace(/\\/g, '/'))
    }
  }
  return references
}

const files = walk(srcDir)
const defined = collectDefinitions(files)
const references = collectReferences(files)

describe('设计令牌引用契约', () => {
  it('未定义的 var(--mrr-*) 引用会让边框退化成 currentColor，必须先定义', () => {
    const missing = [...references.entries()]
      .filter(([name]) => !defined.has(name))
      .map(([name, owners]) => `${name} ← ${[...owners].join(', ')}`)

    expect(missing, `存在未定义的令牌引用：\n${missing.join('\n')}`).toEqual([])
  })

  it('强描边令牌使用静态色值，Chrome 86 回退分支才能引用', () => {
    // browser-compatibility.css 的 `@supports not (color-mix)` 分支直接用 shadow/边框令牌，
    // 若它是 color-mix()，86 上仍会整条失效并退回墨色描边。
    const tokensCss = fs.readFileSync(path.join(srcDir, 'assets/styles/tokens.css'), 'utf8')
    const declaration = tokensCss.split('--mrr-border-strong:')[1] ?? ''
    const value = declaration.split(';')[0].trim()
    expect(value, 'tokens.css 缺少 --mrr-border-strong').not.toBe('')
    expect(value).not.toContain('color-mix(')
  })
})
