import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { readElementPlusCss } from './read-element-plus-css'

const root = path.dirname(fileURLToPath(import.meta.url))
const read = (name: string) => fs.readFileSync(path.join(root, name), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '')
const tokens = read('tokens.css')
const bridge = readElementPlusCss()
const controls = read('components/form-controls.css')
const warm = read('themes/warm-flat.css')

describe('字段视觉令牌归属', () => {
  it('统一字段引用密度、字号、文本域、焦点与投影令牌', () => {
    for (const token of ['field-font-size', 'field-padding-x', 'textarea-min-height', 'textarea-padding-y', 'textarea-line-height', 'field-focus-bg', 'field-shadow']) {
      expect(tokens).toContain(`--mrr-${token}:`)
      expect(bridge).toContain(`var(--mrr-${token})`)
    }
  })
  it('暖色主题只覆盖字段令牌，不重新争夺原生字段外观', () => {
    for (const token of ['field-padding-x', 'textarea-padding-y', 'field-focus-bg', 'field-shadow']) {
      expect(warm).toContain(`--mrr-${token}:`)
    }
    expect(warm).not.toMatch(/\.el-(input__wrapper|select__wrapper|textarea__inner)\b/)
  })
  it('统一控件保留错误、只读、禁用及多行尺寸契约', () => {
    expect(bridge).toContain('.el-input.account-field-invalid .el-input__wrapper')
    expect(bridge).toContain('.el-textarea.account-field-invalid .el-textarea__inner')
    expect(bridge).toContain('.el-input.is-disabled .el-input__wrapper')
    expect(bridge).toContain('.el-textarea.is-disabled .el-textarea__inner')
    expect(controls).toContain('.el-input.mrr-readonly-field:not(.is-disabled)')
    expect(controls).toContain('.el-textarea.mrr-textarea-rows .el-textarea__inner')
  })
})
