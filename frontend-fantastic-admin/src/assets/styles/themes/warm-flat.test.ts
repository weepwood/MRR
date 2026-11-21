import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const styleDir = path.dirname(fileURLToPath(import.meta.url))
const themeCss = fs.readFileSync(path.join(styleDir, 'warm-flat.css'), 'utf8')
/** 去掉注释后再做规则检查：注释里会提到被禁用的语法本身。 */
const themeCode = themeCss.replace(/\/\*[\s\S]*?\*\//g, '')
const globalsCss = fs.readFileSync(path.join(styleDir, '../globals.css'), 'utf8')

describe('暖色扁平风格的设计层契约', () => {
  it('整份规则都挂在 data-mrr-palette="gr8r" 下，退出调色板即刻失效', () => {
    // 主题层一旦有「裸选择器」，切回 MRR 经典后仍会残留部分外观，用户无法回到原样。
    const selectors = themeCode
      .split('\n')
      .map(line => line.trim())
      .filter(line => line.endsWith('{'))
      .filter(line => !line.startsWith('@'))

    expect(selectors.length).toBeGreaterThan(10)
    for (const selector of selectors) {
      expect(selector, `未被调色板限定的选择器：${selector}`).toMatch(/^html\[data-mrr-palette="gr8r"\]/)
    }
  })

  it('在 globals.css 中最后加载，避免同权重的默认外观反盖主题层', () => {
    const imports = globalsCss.match(/@import\s+"[^"]+";/g) ?? []
    expect(imports.at(-1)).toContain('themes/warm-flat.css')
  })

  it('不与内联变量争夺同一令牌，且不写死色值', () => {
    // 阴影由 theme-palette.ts 写入 <html> 内联样式（优先级更高）；主题层再定义会变成死代码。
    const inlineOwned = /--mrr-shadow-(xs|sm|md|lg)\s*:/
    expect(themeCode).not.toMatch(inlineOwned)
    // 颜色一律引用令牌：写死色值会让主题退化成「只对某个默认主色有效」。
    const hexColors = themeCode.match(/#[0-9a-f]{3,8}\b/gi) ?? []
    expect(hexColors).toEqual([])
  })

  it('遵守 Chrome 86 红线', () => {
    // :is()/:has() 在 86 上整条规则失效，会让主题部分生效且难以察觉。
    expect(themeCode).not.toMatch(/:is\(/)
    expect(themeCode).not.toMatch(/:has\(/)
    expect(themeCode).not.toMatch(/color-mix\(/)
    // 视口单位不得使用 dvh/svh（86 不支持，缺 vh 兜底时高度会塌）。
    expect(themeCode).not.toMatch(/\d(dvh|svh|lvh)\b/)
  })

  it('圆角令牌跟随滑块缩放，不写死固定值', () => {
    // 写死会让「全局圆角」在暖色主题下失灵，用户会以为设置坏了。
    expect(themeCss).toMatch(/--mrr-radius-sm:\s*min\(calc\(var\(--radius/)
    expect(themeCss).toMatch(/--mrr-radius-lg:\s*min\(calc\(var\(--radius/)
    expect(themeCss).toMatch(/--mrr-control-radius:\s*var\(--mrr-radius-md\)/)
  })
})
