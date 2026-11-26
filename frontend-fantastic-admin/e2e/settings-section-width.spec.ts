import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const theme of ['light', 'dark']) {
  test(`设置分区外框与标题等宽 ${theme}`, async ({ page }) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(theme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: theme, pageTitleStyle: 'card', enableMotion: false },
      menu: { mode: 'single' },
      layout: { enableMobileAdaptation: true },
    })), theme)
    for (const section of ['system', 'login-support', 'documentation', 'external-links', 'archive', 'ocr', 'department', 'security', 'runtime', 'developer', 'appearance']) {
      await page.setViewportSize({ width: 1920, height: 900 })
      await page.goto(`/settings?section=${section}`)
      const content = page.locator('.settings-content')
      await expect(content).toBeVisible()
      await expect(content.locator(':scope > :first-child')).toBeVisible()
      if (await content.getAttribute('aria-busy') !== null) {
        await expect(content).toHaveAttribute('aria-busy', 'false')
      }
      for (const width of [1920, 1440, 1024, 375]) {
        await page.setViewportSize({ width, height: 900 })
        await expect.poll(() => content.evaluate((el) => {
          const header = document.querySelector('.settings-panel-header')!.getBoundingClientRect()
          const root = el.firstElementChild!.getBoundingClientRect()
          return Math.abs(root.left - header.left) <= 1 && Math.abs(root.right - header.right) <= 1 && el.scrollWidth <= el.clientWidth + 1
        }), { message: `${section} / ${width}` }).toBe(true)
        if (section === 'external-links') {
          await expect.poll(() => content.evaluate((el) => {
            const outer = el.getBoundingClientRect()
            const cards = [...el.querySelectorAll('.settings-card')]
            return cards.length === 2 && cards.every((card) => {
              const r = card.getBoundingClientRect()
              return Math.abs(r.left - outer.left) <= 1 && Math.abs(r.right - outer.right) <= 1
            })
          })).toBe(true)
          if (width >= 1440) {
            const button = content.getByRole('button', { name: '添加外链', exact: true })
            await expect.poll(async () => (await button.boundingBox())!.width).toBeLessThan(160)
          }
        }
      }
    }
  })
}
