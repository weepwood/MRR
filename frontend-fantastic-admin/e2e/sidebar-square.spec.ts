import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const palette of ['mrr', 'gr8r']) {
  for (const theme of ['light', 'dark']) {
    test(`折叠侧栏方形状态 ${palette}/${theme}`, async ({ page }, info) => {
      await isolateLocalVisualSettings(page)
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { colorScheme: theme, themePalette: palette, enableMotion: false },
        menu: { mode: 'single', subMenuCollapse: false },
        layout: { enableMobileAdaptation: true },
      })), { palette, theme })
      await page.goto('/users')
      const nav = page.locator('#mrr-sub-navigation')
      const system = nav.getByRole('button', { name: '系统', exact: true })
      const business = nav.getByRole('button', { name: '业务', exact: true })
      await expect(system).toBeVisible()
      const expanded = (await system.boundingBox())!
      await page.getByRole('button', { name: '收起侧边栏', exact: true }).click()
      await page.mouse.move(800, 450)
      await expect(nav).toHaveClass(/\bis-collapse\b/)
      await expect(system).toHaveClass(/\bactive\b/)
      await expect.poll(() => nav.evaluate((el) => {
        const rail = el.getBoundingClientRect()
        const items = [...el.querySelectorAll('.menu > .menu-item > .menu-item-container')].filter(node => node.getBoundingClientRect().width > 0)
        return items.length > 0 && items.every((node) => {
          const r = node.getBoundingClientRect()
          const icon = node.querySelector('.menu-item-container-icon')?.getBoundingClientRect()
          return Math.abs(r.width - 36) < 0.5 && Math.abs(r.height - 36) < 0.5
            && Math.abs(r.x + r.width / 2 - rail.x - rail.width / 2) < 1
            && (!icon || Math.abs(icon.x + icon.width / 2 - r.x - r.width / 2) < 1)
            && (!icon || Math.abs(icon.y + icon.height / 2 - r.y - r.height / 2) < 1)
        })
      })).toBe(true)
      await nav.screenshot({ path: info.outputPath('selected.png') })
      const before = await business.boundingBox()
      const idle = await business.evaluate(el => getComputedStyle(el).backgroundColor)
      await business.hover()
      await expect.poll(() => business.evaluate(el => getComputedStyle(el).backgroundColor)).not.toBe(idle)
      expect(await business.boundingBox()).toEqual(before)
      await page.mouse.move(800, 450)
      await expect(page.locator('.saas-popup-menu')).toHaveCount(0)
      await system.focus()
      await page.keyboard.press('Space')
      const users = page.getByRole('link', { name: '用户管理', exact: true })
      await expect(users).toBeFocused()
      expect((await users.boundingBox())!.width).toBeGreaterThan(100)
      await page.keyboard.press('Escape')
      await expect(system).toBeFocused()
      await expect(system).toHaveCSS('outline-style', 'solid')
      await page.getByRole('button', { name: '展开侧边栏', exact: true }).click()
      await expect(nav).not.toHaveClass(/\bis-collapse\b/)
      await expect.poll(async () => (await system.boundingBox())!.width).toBeCloseTo(expanded.width, 0)
    })
  }
}
