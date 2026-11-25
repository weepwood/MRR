import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const palette of ['mrr', 'gr8r']) {
  for (const theme of ['light', 'dark']) {
    test(`${palette}/${theme} 固定操作列与整行同步悬停，按钮反馈独立`, async ({ page }) => {
      await isolateLocalVisualSettings(page)
      await page.addInitScript(({ palette, theme }) => {
        localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
          app: { colorScheme: theme, themePalette: palette, enableMotion: true, pageTitleStyle: 'card' },
          menu: { mode: 'single' },
          layout: { enableMobileAdaptation: true },
        }))
      }, { palette, theme })
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto('/users')
      const table = page.locator('.users-table-panel .el-table')
      const rows = table.locator('.el-table__body tr.el-table__row')
      await expect(rows).toHaveCount(3)
      await expect.poll(() => page.evaluate(() => document.documentElement.classList.contains('dark'))).toBe(theme === 'dark')
      if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
      for (const width of [1440, 768, 375]) {
        await page.setViewportSize({ width, height: 900 })
        const row = rows.first()
        const normal = row.locator('td').first()
        const fixed = row.locator('td.el-table-fixed-column--right')
        const more = fixed.getByRole('button', { name: '更多操作' })
        const inspect = () => row.evaluate((el) => {
          const cells = Array.from(el.querySelectorAll('td.el-table__cell'))
          return { colors: [...new Set(cells.map(cell => getComputedStyle(cell).backgroundColor))], transitions: [...new Set(cells.map(cell => getComputedStyle(cell).transitionDuration))] }
        })
        await page.locator('.users-filter-bar').hover()
        await expect.poll(() => row.evaluate(el => !el.matches(':hover') && !el.classList.contains('hover-row'))).toBe(true)
        await expect.poll(async () => (await inspect()).colors.length).toBe(1)
        const base = (await inspect()).colors[0]
        await normal.hover()
        await expect.poll(async () => {
          const { colors } = await inspect()
          return colors.length === 1 && colors[0] !== base
        }).toBe(true)
        const hovered = await inspect()
        expect(hovered.colors[0]).not.toBe(base)
        expect(hovered.transitions).toEqual(['0s'])
        await more.hover()
        await expect.poll(async () => (await inspect()).colors).toEqual(hovered.colors)
        await expect.poll(() => more.evaluate(el => getComputedStyle(el).backgroundColor)).not.toBe('rgba(0, 0, 0, 0)')
        await rows.nth(1).locator('td').first().hover()
        await expect.poll(async () => (await inspect()).colors).toEqual([base])
        await more.hover()
        await expect.poll(async () => (await inspect()).colors).toEqual(hovered.colors)
        await page.locator('.users-filter-bar').hover()
        await expect.poll(async () => (await inspect()).colors).toEqual([base])
        await expect.poll(() => more.evaluate(el => getComputedStyle(el).backgroundColor)).toBe('rgba(0, 0, 0, 0)')
        if (width < 1024) {
          const scroll = table.locator('.el-scrollbar__wrap').first()
          await scroll.evaluate((el) => { el.scrollLeft = el.scrollWidth })
          await expect.poll(() => scroll.evaluate(el => el.scrollLeft)).toBeGreaterThan(0)
          await more.hover()
          await expect.poll(async () => (await inspect()).colors).toEqual(hovered.colors)
          await page.locator('.users-filter-bar').hover()
          await expect.poll(async () => (await inspect()).colors).toEqual([base])
          await scroll.evaluate((el) => { el.scrollLeft = 0 })
        }
      }
    })
  }
}
