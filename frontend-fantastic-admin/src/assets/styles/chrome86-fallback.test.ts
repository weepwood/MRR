import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/*
 * Chrome 86 的 color-mix 回退契约。
 *
 * 这些断言不是在测样式「长什么样」，而是在守两条无法靠肉眼发现的规则：
 * 1. 「先静态值、再 color-mix」的双声明不是回退，全仓必须为 0 处；
 * 2. 已确认必须回退的分层表面与数据编码，其 @supports not 块不能在某次重构里被删掉。
 */

const srcRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')

function walk(directory: string, collected: string[] = []): string[] {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      walk(target, collected)
    }
    else if (target.endsWith('.vue') || target.endsWith('.css')) {
      collected.push(target)
    }
  }
  return collected
}

// 注释先抹成等长空白，避免注释里的示例代码被当成真实声明。
function stripComments(css: string): string {
  return css.replace(/\/\*[\s\S]*?\*\//g, match => match.replace(/[^\n]/g, ' '))
}

function read(file: string): string {
  return stripComments(fs.readFileSync(path.join(srcRoot, file), 'utf8'))
}

function fakeFallbacks(file: string): string[] {
  const css = read(file)
  const hits: string[] = []
  for (const block of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    const selector = block[1].trim().replace(/\s+/g, ' ')
    if (!selector || selector.startsWith('@')) {
      continue
    }
    const valuesByProperty = new Map<string, string[]>()
    for (const declaration of block[2].split(';')) {
      const separator = declaration.indexOf(':')
      if (separator < 1) {
        continue
      }
      const property = declaration.slice(0, separator).trim()
      if (property.startsWith('--')) {
        continue
      }
      valuesByProperty.set(property, [...(valuesByProperty.get(property) ?? []), declaration.slice(separator + 1).trim()])
    }
    for (const [property, values] of valuesByProperty) {
      const last = values.at(-1) ?? ''
      const earlierStatic = values.slice(0, -1).some(value => !value.includes('color-mix(') && value !== '')
      if (earlierStatic && last.includes('var(') && last.includes('color-mix(')) {
        hits.push(`${selector} { ${property} }`)
      }
    }
  }
  return hits
}

function notBlockAwareness(file: string) {
  const css = read(file)
  const covering = new Set<string>()
  const marker = /@supports\s+not\s*\(\s*color\s*:\s*color-mix\(/g
  for (const match of css.matchAll(marker)) {
    const open = css.indexOf('{', match.index + match[0].length)
    if (open < 0) {
      continue
    }
    let depth = 0
    let cursor = open
    for (; cursor < css.length; cursor += 1) {
      if (css[cursor] === '{') {
        depth += 1
      }
      else if (css[cursor] === '}') {
        depth -= 1
        if (depth === 0) {
          break
        }
      }
    }
    for (const rule of css.slice(open + 1, cursor).matchAll(/([^{}]+)\{/g)) {
      covering.add(rule[1].trim().replace(/\s+/g, ' '))
    }
  }
  return { hasBlock: covering.size > 0, covering }
}

describe('chrome 86 color-mix 回退契约', () => {
  it('全仓不存在「先静态值再 color-mix」的假回退', () => {
    const offenders = walk(srcRoot)
      .flatMap(file => fakeFallbacks(path.relative(srcRoot, file).split(path.sep).join('/'))
        .map(hit => `${path.relative(srcRoot, file).split(path.sep).join('/')} -> ${hit}`))
    expect(offenders).toEqual([])
  })

  it('档案袋的三层表面与卡面都带静态回退', () => {
    const { covering } = notBlockAwareness('views/statistics-detail/ArchiveDetailContent.vue')
    for (const selector of ['.folder-layer', '.folder-layer-back', '.folder-layer-middle', '.folder-card-body']) {
      expect(covering.has(selector)).toBe(true)
    }
  })

  it('热力图四档色阶在 86 上仍可分辨', () => {
    const { covering } = notBlockAwareness('components/charts/MrrRequestHeatmap.vue')
    for (const selector of ['.level-0', '.level-1', '.level-2', '.level-3']) {
      expect(covering.has(selector)).toBe(true)
    }
  })

  it('表格表头与行悬停底色由变量级重设兜底', () => {
    const css = read('assets/styles/element-plus-overrides.css')
    expect(css).toMatch(/--el-table-header-bg-color:\s*color-mix\(/)
    expect(css).toMatch(/@supports not \(color: color-mix\([^)]*\)\) \{[^@]*--el-table-header-bg-color:\s*var\(--mrr-muted\)/)
  })

  it('工作台 widget 着色底面带静态回退', () => {
    const { covering } = notBlockAwareness('views/home/DashboardWorkspace.vue')
    for (const selector of [
      '.dashboard-widget--color-blue',
      '.dashboard-widget--color-green',
      '.dashboard-widget--color-orange',
      '.dashboard-widget--color-red',
      '.dashboard-widget__icon, .widget-manager-item__icon',
      '.pin-badge',
    ]) {
      expect(covering.has(selector)).toBe(true)
    }
  })

  it('帮助页文档卡与注册页审核条带静态回退', () => {
    const help = notBlockAwareness('views/help/index.vue')
    expect(help.covering.has('.documentation-card')).toBe(true)
    expect(help.covering.has('.card-icon')).toBe(true)

    const register = notBlockAwareness('components/AccountForm/RegisterForm.vue')
    expect(register.covering.has('.review-flow')).toBe(true)
    expect(register.covering.has('.form-section')).toBe(true)
  })

  it('危险模式徽标与安全标识块带静态回退', () => {
    const badge = notBlockAwareness('layouts/components/Logo/index.vue')
    expect(badge.covering.has('.developer-mode-badge')).toBe(true)

    const mark = notBlockAwareness('views/password/change-required.vue')
    expect(mark.covering.has('.security-mark')).toBe(true)
  })

  it('焦点环不自己算混色，走已带回退的 --mrr-ring', () => {
    for (const file of ['components/MrrTableActions/index.vue', 'components/SystemAdminContactPopover/index.vue']) {
      const css = read(file)
      expect(css).toMatch(/outline:\s*2px solid var\(--mrr-ring\)/)
      expect(css).not.toMatch(/outline:\s*2px solid color-mix\(/)
    }
  })
})
