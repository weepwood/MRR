import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => { await isolateLocalVisualSettings(page) })

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`${palette} / ${theme} 统一输入与文本域共享尺寸、表面和只读错误状态`, async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { colorScheme: theme, themePalette: palette, enableMotion: false },
        menu: { mode: 'single' },
      })), { palette, theme })
      await page.goto('/ui-components')
      await page.getByRole('button', { name: /表单与选择/ }).click()
      if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
      else { await expect(page.locator('html')).not.toHaveAttribute('data-mrr-palette', 'gr8r') }
      await page.mouse.move(0, 0)
      await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
      const inspect = (name: string) => page.getByLabel(name, { exact: true }).evaluate((el) => {
        const box = el.closest('.el-input__wrapper') ?? el
        const s = getComputedStyle(box)
        const inner = getComputedStyle(el)
        return { height: box.getBoundingClientRect().height, font: inner.fontSize, radius: s.borderRadius, background: s.backgroundColor, color: inner.color }
      })
      for (const [fa, native] of [['示例名称', '原生名称'], ['示例说明', '原生说明'], ['只读名称', '原生只读名称']]) {
        const a = await inspect(fa)
        const b = await inspect(native)
        expect(Math.abs(a.height - b.height), `${fa} 高度差`).toBeLessThan(1)
        const { height: _a, ...faStyle } = a
        const { height: _b, ...nativeStyle } = b
        expect(faStyle, `${fa} 视觉契约`).toEqual(nativeStyle)
      }
      const invalid = page.getByLabel('独立校验示例', { exact: true })
      await expect(invalid).toHaveAccessibleDescription('请输入示例名称')
      await expect(invalid).toHaveAttribute('aria-invalid', 'true')
      await expect.poll(() => invalid.evaluate(el => getComputedStyle(el.closest('.el-input__wrapper')!).boxShadow)).toContain(await page.locator('html').evaluate((el) => {
        const probe = document.createElement('span')
        probe.style.color = 'var(--mrr-destructive)'
        el.append(probe)
        const color = getComputedStyle(probe).color
        probe.remove()
        return color
      }))
      await invalid.fill('有效')
      await expect(invalid).not.toHaveAttribute('aria-invalid', 'true')
      await expect(page.getByLabel('原生只读名称', { exact: true })).toHaveAttribute('readonly', '')
      await expect(page.getByLabel('只读名称', { exact: true })).toHaveAttribute('readonly', '')
    })
  }
}
