import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/*
 * 两套按钮的同口径契约。
 *
 * 项目并存 Element Plus 的 `.el-button` 与 reka-ui 封装的 `.fa-button-control`（FaButton）。
 * 二者在同一工具栏里承担同样的角色，曾经各写一套数值（13px vs 14px 字号、11/14/16px 内边距、
 * 8px vs 10px 圆角），并排时一眼能看出是两套控件。
 *
 * 现在的分工是：
 * - tokens.css 的 `--mrr-button-*` 是唯一的数值来源；
 * - element-plus-overrides.css 与 FaButton 的 cva 都只引用令牌，不写死数值；
 * - 主题换密度时改令牌（warm-flat.css 里的 `--mrr-button-*` 覆盖），不在主题层重写按钮规则。
 *
 * 这些断言守的就是这条分工——它们不测「长什么样」，只防止某次改动把数值又写回某一侧。
 */

const styleDir = path.dirname(fileURLToPath(import.meta.url))
const read = (...segments: string[]) => fs.readFileSync(path.join(styleDir, ...segments), 'utf8')
const stripComments = (css: string) => css.replace(/\/\*[\s\S]*?\*\//g, '')

const tokensCss = read('tokens.css')
const elementPlusCss = stripComments(read('element-plus-overrides.css'))
const formControlsCss = stripComments(read('components/form-controls.css'))
const themeCss = stripComments(read('themes/warm-flat.css'))
const cvaSource = fs.readFileSync(
  path.join(styleDir, '../../ui/components/FaButton/button/index.ts'),
  'utf8',
)
/** 注释里会引用被禁止的写法本身，扫描前先抹掉。 */
const cvaCode = cvaSource
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/\/\/.*$/gm, '')

/** 取出某个选择器块里的声明文本（只看第一层，够用即可）。 */
function blockOf(css: string, selector: string): string {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = css.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`))
  return match?.[1] ?? ''
}

describe('两套按钮的同口径契约', () => {
  it('按钮尺寸令牌齐备，且是两套按钮的唯一数值来源', () => {
    for (const [name, value] of [
      ['--mrr-button-font-size', '13px'],
      ['--mrr-button-padding-x', '14px'],
      ['--mrr-button-font-size-sm', '12px'],
      ['--mrr-button-padding-x-sm', '11px'],
      ['--mrr-button-font-size-lg', '14px'],
      ['--mrr-button-padding-x-lg', '16px'],
      ['--mrr-button-padding-x-text', '8px'],
    ]) {
      expect(tokensCss, `缺少令牌 ${name}`).toContain(`${name}: ${value}`)
    }
  })

  it('按钮只引用令牌，不再写死字号与内边距', () => {
    const base = blockOf(elementPlusCss, '.el-button')
    expect(base).toContain('padding: 0 var(--mrr-button-padding-x)')
    expect(base).toContain('font-size: var(--mrr-button-font-size)')

    const small = blockOf(elementPlusCss, '.el-button.el-button--small')
    expect(small).toContain('padding-inline: var(--mrr-button-padding-x-sm)')
    expect(small).toContain('font-size: var(--mrr-button-font-size-sm)')

    const large = blockOf(elementPlusCss, '.el-button.el-button--large')
    expect(large).toContain('padding-inline: var(--mrr-button-padding-x-lg)')
    expect(large).toContain('font-size: var(--mrr-button-font-size-lg)')

    // 写死数值会让主题只能靠更高优先级去盖，最后两侧各留一份「差不多」的数字。
    expect(base).not.toMatch(/font-size:\s*\d+(\.\d+)?px/)
    expect(base).not.toMatch(/padding:\s*0\s+\d+px/)
  })

  it('界面封装的 cva 引用同一组令牌，且字号带 length: 提示', () => {
    expect(cvaCode).toContain('h-[var(--mrr-control-height)]')
    expect(cvaSource).toContain('px-[var(--mrr-button-padding-x)]')
    expect(cvaSource).toContain('text-[length:var(--mrr-button-font-size)]')
    expect(cvaSource).toContain('rounded-[var(--mrr-control-radius)]')
    expect(cvaSource).toContain('px-[var(--mrr-button-padding-x-sm)]')
    expect(cvaSource).toContain('px-[var(--mrr-button-padding-x-lg)]')

    // 旧的写死写法：text-sm 是 14px、rounded-md 是 10px，正是两侧分叉的来源。
    for (const legacy of ['text-sm', 'rounded-md', 'px-4', 'px-3', 'px-8', 'bg-primary', 'border-input']) {
      expect(cvaCode, `cva 里仍有写死外观：${legacy}`).not.toMatch(new RegExp(`['"\\s]${legacy}['"\\s]`))
    }

    // `text-[var(--x)]` 在 UnoCSS 里是「颜色还是字号」的歧义写法，会按颜色处理、字号不生效。
    const ambiguous = [...cvaCode.matchAll(/text-\[(?!length:|color:)([^\]]+)\]/g)].map(match => match[0])
    expect(ambiguous, 'text-[…] 必须带 length:/color: 类型提示').toEqual([])
  })

  it('主题层通过令牌换密度，不再单独重写按钮的 padding / font-size', () => {
    expect(themeCss).toContain('--mrr-button-padding-x: 11px')
    expect(themeCss).toContain('--mrr-button-font-size: 12.5px')

    // 主题层若直接写 padding，会把调用方的工具类覆盖一起压掉（账户按钮、搜索框都靠它微调）。
    for (const selector of ['.el-button {', '.fa-button-control {']) {
      const block = blockOf(themeCss, `html[data-mrr-palette="gr8r"] ${selector}`)
      expect(block, `${selector} 不应在主题层写内边距/字号`).not.toMatch(/(padding|font-size):/)
    }
  })

  it('两套按钮的交互状态由同一份状态层定义', () => {
    // hover / active / focus / disabled / loading 必须逐变体覆盖，缺一个就会出现
    // 「一边悬停变灰、另一边纹丝不动」的错位。
    for (const variant of ['default', 'outline', 'secondary', 'ghost', 'link', 'destructive']) {
      expect(formControlsCss, `缺少 ${variant} 的悬停态`).toContain(`.fa-button-control[data-variant="${variant}"]:not(:disabled):hover`)
    }

    expect(formControlsCss).toContain('.fa-button-control:focus-visible')
    expect(formControlsCss).toContain('.fa-button-control:disabled:not([data-loading])')
    expect(formControlsCss).toContain('.fa-button-control[data-loading]')

    // 禁用态如果只降透明度，浅色主题里仍像可点击；有底色的按钮必须换中性灰。
    const disabled = blockOf(formControlsCss, '.fa-button-control:disabled:not([data-loading]):not([data-variant="ghost"]):not([data-variant="link"])')
    expect(disabled).toContain('background: var(--mrr-muted)')
    expect(disabled).toContain('color: var(--mrr-muted-foreground)')
  })

  it('禁用态规则能压过 outline 规则', () => {
    // Outline 规则用一长串 :not() 排除语义色，特异性高达 (0,8,0)；
    // 少写 :not(.is-disabled)，禁用按钮会继续拿到白底 + 可用色，读起来仍可点击。
    const outline = elementPlusCss
      .split('\n')
      .filter(line => line.includes('.el-button:not(.el-button--primary)') && line.endsWith('{'))
      .join('\n')
    expect(outline).toContain(':not(.is-disabled)')
  })

  it('按钮组按 Element Plus 的方式拼接：相邻共用一条描边', () => {
    expect(formControlsCss).toContain('.fa-button-group > * + *')
    expect(formControlsCss).toContain('margin-left: -1px')
    expect(formControlsCss).toContain('.fa-button-group > *:first-child:not(:last-child)')
    expect(formControlsCss).toContain('.fa-button-group > *:last-child:not(:first-child)')
  })
})
