import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/*
 * public/ 不经构建转译、受众是 Chromium<86：任何把 longhand 收敛为 inset 的
 * 自动改写都会让全屏拦截浮层失去定位。锁定四向 longhand 的存在。
 */
describe('browser Upgrade 静态层', () => {
  const css = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../../../public/browser_upgrade/index.css'), 'utf-8')

  it('保留 top/right/bottom/left 四向 longhand（chrome86 门禁同款断言，单测层前移）', () => {
    for (const side of ['top', 'right', 'bottom', 'left']) {
      expect(css, `${side}: 0`).toMatch(new RegExp(`(^|[;{])\\s*${side}:\\s*0\\s*;`))
    }
  })

  // inset shorthand 在 Chromium <86 不支持，public/ 整层（含未来新增静态页）
  // 逐文件不得出现——与 verify-chrome86-build.mjs 门禁、stylelint override 防护三层闭环。
  it('public/**/*.css 全量不含 inset shorthand（未来新增静态页自动纳入）', () => {
    const publicRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../../public')
    const collected: string[] = []
    const walk = (directory: string) => {
      for (const entry of readdirSync(directory, { withFileTypes: true })) {
        const target = join(directory, entry.name)
        if (entry.isDirectory()) {
          walk(target)
        }
        else if (target.endsWith('.css')) {
          collected.push(target)
        }
      }
    }
    walk(publicRoot)
    expect(collected.length, 'public/**/*.css 应至少包含 browser_upgrade/index.css').toBeGreaterThan(0)
    for (const file of collected) {
      expect(readFileSync(file, 'utf-8'), file).not.toMatch(/inset\s*:/)
    }
  })
})
