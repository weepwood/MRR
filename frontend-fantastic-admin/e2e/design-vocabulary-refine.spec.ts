import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const [palette, theme] of [['mrr', 'light'], ['gr8r', 'dark']] as const) {
  test(`${palette}/${theme} 紧凑卡片、六条查询历史、摘要及操作表面`, async ({ page }, info) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { themePalette: palette, colorScheme: theme, enableMotion: false, pageTitleStyle: 'plain' },
      menu: { mode: 'single' },
      layout: { enableMobileAdaptation: true },
    })), { palette, theme })
    await page.route('**/api/v1/archive-search-history**', route => route.fulfill({
      json: {
        code: 200,
        data: Array.from({ length: 6 }, (_, i) => ({
          id: i + 1,
          bah: String(94001 + i).padStart(8, '0'),
          sjh: '',
          success: true,
          imageCount: 2,
          queryCount: 1,
          favorite: i === 0,
          searchedAt: new Date(2026, 10, 28, 12, i).toISOString(),
        })),
      },
    }))
    await page.goto('/')
    const grid = page.locator('.dashboard-grid')
    const pinned = grid.locator('.dashboard-widget.is-pinned').first()
    await expect(pinned).toBeVisible()
    await page.mouse.move(0, 0)
    await expect(pinned.locator('.pin-badge')).toHaveCSS('opacity', '0')
    await pinned.hover()
    await expect(pinned.locator('.pin-badge')).toHaveCSS('opacity', '1')
    await expect(grid.locator('[data-widget-id="archive"] .home-lookup')).toBeVisible()
    await page.getByRole('button', { name: '最近查询' }).click()
    const history = page.locator('.home-archive-history-popper:visible .recent-lookups__list')
    const entries = history.getByRole('button')
    await expect(entries).toHaveCount(6)
    expect(await history.evaluate(el => el.clientHeight)).toBeLessThanOrEqual(160)
    await entries.last().focus()
    await expect(entries.last()).toBeFocused()
    await expect.poll(() => history.evaluate(el => el.scrollTop)).toBeGreaterThan(0)
    await page.screenshot({ path: info.outputPath('home-history.png') })
    await page.goto('/records')
    const summary = page.locator('.records-batch-summary')
    await expect(summary).toContainText('当前批')
    const table = page.locator('.records-table')
    await expect(table).toBeVisible()
    await expect(summary).toContainText('20')
    await expect(page.locator('.records-batch-actions__count')).toHaveText('已选 0 条')
    const cells = table.locator('tbody tr').first().locator('.el-checkbox')
    await cells.click()
    await expect(page.locator('.records-batch-actions__count')).toHaveText('已选 1 条')
    await expect(page.getByRole('button', { name: '批量下载' })).toBeEnabled()
    await expect(page.locator('.mrr-page-header__eyebrow')).toHaveCount(0)
    await page.setViewportSize({ width: 375, height: 900 })
    await expect.poll(() => table.evaluate(el => el.getBoundingClientRect().top)).toBeLessThan(680)
    await page.goto('/patients')
    const metrics = page.locator('[aria-label="患者数据概况"]')
    await expect(metrics.locator('.mrr-metric-card')).toHaveCount(4)
    const cards = await metrics.locator('.mrr-metric-card').evaluateAll(els => els.map(el => el.getBoundingClientRect().top))
    expect(new Set(cards).size).toBe(2)
    await expect(metrics.getByText('身份证号为空', { exact: true })).toBeVisible()
    await expect.poll(() => metrics.evaluate(el => el.getBoundingClientRect().height)).toBeLessThan(300)
    await page.screenshot({ path: info.outputPath('patients-mobile.png') })
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/operations-center')
    const entriesGrid = page.locator('.investigation-grid')
    await expect(entriesGrid.locator('button')).toHaveCount(6)
    const entry = entriesGrid.locator('button').first()
    await page.mouse.move(0, 0)
    await expect(entry).toHaveCSS('border-top-color', 'rgba(0, 0, 0, 0)')
    await entry.focus()
    await expect(entry).toBeFocused()
    await expect(entry).toHaveCSS('outline-style', 'solid')
    await page.screenshot({ path: info.outputPath('operations-focus.png') })
  })
}
