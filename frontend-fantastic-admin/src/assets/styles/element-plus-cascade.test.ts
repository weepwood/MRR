import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { readElementPlusCss } from './read-element-plus-css'

const dir = path.dirname(fileURLToPath(import.meta.url))

describe('element Plus 样式模块级联契约', () => {
  it('桥接模块按实际声明顺序加载，且无遗漏或重复', () => {
    const entry = fs.readFileSync(path.join(dir, 'element-plus-overrides.css'), 'utf8')
    const modules = [...entry.matchAll(/@import "\.\/element-plus\/([a-z-]+\.css)";/g)].map(match => match[1])
    expect(modules).toEqual(['foundation-buttons.css', 'fields.css', 'floating-feedback.css', 'data-display.css', 'forms-geometry-viewer.css', 'overlay-shells.css', 'menus.css', 'auxiliary-controls.css'])
    expect([...modules].sort()).toEqual(fs.readdirSync(path.join(dir, 'element-plus')).filter(name => name.endsWith('.css')).sort())
    expect(readElementPlusCss()).not.toContain('@import')
  })

  it('全局桥接先于共享组件和最终主题', () => {
    const entry = fs.readFileSync(path.join(dir, 'globals.css'), 'utf8')
    const order = ['tokens.css', 'browser-compatibility.css', 'element-plus-overrides.css', 'components/corner-shape.css', 'components/form-controls.css', 'themes/warm-flat.css'].map(name => entry.indexOf(`@import "./${name}";`))
    expect(order.every(i => i >= 0)).toBe(true)
    expect(order).toEqual([...order].sort((a, b) => a - b))
  })
})
