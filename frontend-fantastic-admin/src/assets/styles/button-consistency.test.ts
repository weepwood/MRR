import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { readElementPlusCss } from './read-element-plus-css'

const styleDir = path.dirname(fileURLToPath(import.meta.url))
const read = (...segments: string[]) => fs.readFileSync(path.join(styleDir, ...segments), 'utf8')
const stripComments = (css: string) => css.replace(/\/\*[\s\S]*?\*\//g, '')

const tokensCss = read('tokens.css')
const elementPlusCss = stripComments(readElementPlusCss())
const formControlsCss = stripComments(read('components/form-controls.css'))
const themeCss = stripComments(read('themes/warm-flat.css'))
/** 取出某个选择器块里的声明文本（只看第一层，够用即可）。 */
function blockOf(css: string, selector: string): string {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = css.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`))
  return match?.[1] ?? ''
}

describe('唯一按钮实现的令牌契约', () => {
  it('按钮尺寸令牌齐备，且是按钮的唯一数值来源', () => {
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

  it('图标按钮的方形规格读取控件高度，而非硬编码尺寸', () => {
    const square = blockOf(formControlsCss, '.el-button.mrr-button-square')
    expect(square).toContain('width: var(--mrr-control-height)')
    expect(square).toContain('padding: 0')
  })

  it('主题层通过令牌换密度，不再单独重写按钮的 padding / font-size', () => {
    expect(themeCss).toContain('--mrr-button-padding-x: 11px')
    expect(themeCss).toContain('--mrr-button-font-size: 13px')

    // 主题层若直接写 padding，会把调用方的工具类覆盖一起压掉（账户按钮、搜索框都靠它微调）。
    for (const selector of ['.el-button {']) {
      const block = blockOf(themeCss, `html[data-mrr-palette="gr8r"] ${selector}`)
      expect(block, `${selector} 不应在主题层写内边距/字号`).not.toMatch(/(padding|font-size):/)
    }
  })

  it('唯一实现覆盖正常、悬停、焦点、禁用、加载与辅助操作', () => {
    expect(elementPlusCss).toContain('.el-button:focus-visible')
    expect(elementPlusCss).toContain('.el-button.is-loading')
    const disabled = blockOf(elementPlusCss, '.el-button.is-disabled:not(.is-text):not(.is-link),\n.el-button.is-disabled:not(.is-text):not(.is-link):hover,\n.el-button.is-disabled:not(.is-text):not(.is-link):focus')
    expect(disabled).toContain('background: var(--mrr-muted)')
    expect(disabled).toContain('color: var(--mrr-muted-foreground)')
    expect(formControlsCss).toContain('.el-button.mrr-button-secondary.is-text:not(.is-disabled):hover')
    expect(formControlsCss).toContain('.el-button.mrr-button-secondary.is-text.is-disabled')
    expect(elementPlusCss).toContain('.el-button.is-text:not(.is-disabled):hover')
    expect(elementPlusCss).toContain('.el-button--danger:focus-visible')
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

  it('按钮组共享描边并保留单按钮外缘', () => {
    expect(blockOf(formControlsCss, '.el-button-group.mrr-joined-buttons > .el-button + .el-button')).toContain('margin-left: -1px')
    expect(blockOf(formControlsCss, '.el-button-group.mrr-joined-buttons > .el-button:only-child')).toContain('border-radius: var(--mrr-control-radius)')
    expect(elementPlusCss).toContain('.el-button-group .el-button:not(:first-child):not(:last-child)')
  })
})
