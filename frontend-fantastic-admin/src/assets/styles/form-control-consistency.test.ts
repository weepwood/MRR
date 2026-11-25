import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const root = path.dirname(fileURLToPath(import.meta.url))
const read = (name: string) => fs.readFileSync(path.join(root, name), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '')
const tokens = read('tokens.css')
const bridge = read('element-plus-overrides.css')
const controls = read('components/form-controls.css')
const warm = read('themes/warm-flat.css')
const textarea = read('../../ui/components/FaTextarea/textarea/Textarea.vue')

describe('字段视觉令牌归属', () => {
  it('两族共同引用字段密度、字号、文本域、焦点与投影令牌', () => {
    for (const token of ['field-font-size', 'field-padding-x', 'textarea-min-height', 'textarea-padding-y', 'textarea-line-height', 'field-focus-bg', 'field-shadow']) {
      expect(tokens).toContain(`--mrr-${token}:`)
      expect(bridge).toContain(`var(--mrr-${token})`)
      expect(controls).toContain(`var(--mrr-${token})`)
    }
  })
  it('暖色主题只覆盖字段令牌，不重新争夺原生字段外观', () => {
    for (const token of ['field-padding-x', 'textarea-padding-y', 'field-focus-bg', 'field-shadow']) {
      expect(warm).toContain(`--mrr-${token}:`)
    }
    expect(warm).not.toMatch(/\.el-(input__wrapper|select__wrapper|textarea__inner)\b/)
    expect(textarea).toContain('fa-textarea-control')
    expect(textarea).not.toMatch(/\b(text-sm|rounded-md|bg-background|min-h-20)\b/)
  })
  it('封装控件保留注册表单的错误样式入口、只读与禁用区别', () => {
    for (const name of ['fa-input-control', 'fa-textarea-control']) {
      expect(controls).toContain(`.${name}[readonly]:not(:disabled)`)
      expect(controls).toContain(`.${name}[aria-invalid="true"]:not(:disabled)`)
      expect(controls).toContain(`.${name}.border-destructive:not(:disabled)`)
    }
  })
})
