import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const palette of ['mrr', 'gr8r']) {
  for (const theme of ['light', 'dark']) {
    test(`侧栏分组间距 ${palette}/${theme}`, async ({ page }, testInfo) => {
      await isolateLocalVisualSettings(page)
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { colorScheme: theme, themePalette: palette, enableMotion: false },
        menu: { mode: 'single', subMenuCollapse: false },
        layout: { enableMobileAdaptation: true },
      })), { palette, theme })
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto('/patients')
      const nav = page.locator('#mrr-sub-navigation')
      await expect(nav.getByRole('link', { name: '患者管理', exact: true })).toHaveAttribute('aria-current', 'page')
      for (const width of [1920, 1440, 760, 375]) {
        await page.setViewportSize({ width, height: 900 })
        if (width < 768 && !await nav.isVisible()) {
          await page.getByRole('button', { name: '切换侧栏导航' }).click()
        }
        await expect.poll(() => nav.evaluate((el) => {
          const sidebar = el.getBoundingClientRect()
          const items = [...el.querySelectorAll('.sub-menu .menu-item-container')].filter(node => node.getBoundingClientRect().width > 0 && !node.closest('[aria-hidden=true]'))
          return items.length > 0 && items.every((node) => {
            const branch = node.closest('.sub-menu')!
            const r = node.getBoundingClientRect()
            const b = branch.getBoundingClientRect()
            const guide = b.left + Number.parseFloat(getComputedStyle(branch, '::before').left) + 1
            const icon = node.querySelector('.menu-item-container-icon')?.getBoundingClientRect()
            const label = node.querySelector('.truncate')
            return Math.abs(r.left - guide - 8) <= 1
              && Math.abs(sidebar.right - r.right - 12) <= 1
              && (!icon || Math.abs(icon.left - r.left - 15) <= 1)
              && (!label || label.scrollWidth <= label.clientWidth + 1)
          })
        })).toBe(true)
        if (width === 1440 || width === 375) {
          await nav.screenshot({ path: testInfo.outputPath(`sidebar-${width}.png`), animations: 'disabled' })
        }
      }
    })
  }
}
