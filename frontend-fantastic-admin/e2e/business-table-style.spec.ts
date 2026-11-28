import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

const routes = [
  ['users', '用户名'],
  ['records', '文件名'],
  ['patients', '身份证号'],
  ['logs', '真实请求 URI'],
  ['audit-images', '访问类型'],
  ['archive-boxes', '实际箱号'],
  ['runtime-errors', '错误摘要'],
  ['statistics', '设备 ID（CID）'],
  ['ocr-batches', '待审核建议'],
] as const

for (const [palette, theme] of [['mrr', 'light'], ['gr8r', 'dark']] as const) {
  test(`${palette}/${theme} 业务表格共享表头行操作筛选及分页外观`, async ({ page }) => {
    test.setTimeout(150_000)
    await isolateLocalVisualSettings(page)
    await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: theme, themePalette: palette, enableMotion: false, pageTitleStyle: 'card' },
      menu: { mode: 'single' },
      layout: { enableMobileAdaptation: true },
    })), { palette, theme })
    await page.route('**/api/v1/ocr/batches**', route => route.fulfill({ json: {
      code: 200,
      data: [{ id: 'synthetic-style', status: 'RUNNING', cursorId: 100, processedCount: 80, dispatchedCount: 120, successCount: 78, failedCount: 2, autoAppliedCount: 0, reviewRequiredCount: 0, autoApply: false }],
    } }))
    let reference: { height: number, font: string, background: string, radius: string } | undefined
    for (const [route, key] of routes) {
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(`/${route}`)
      const table = page.locator('.mrr-data-table-panel__content .el-table:visible').filter({ has: page.getByRole('columnheader', { name: key, exact: true }) }).first()
      await expect(table).toBeVisible()
      await expect(table.getByRole('columnheader', { name: '序号', exact: true })).toHaveCount(1)
      await expect(table.locator('.el-table__body td.mrr-table-sequence .cell').first()).toHaveText('1')
      await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
      if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
      const panel = table.locator('xpath=ancestor::*[contains(concat(" ",normalize-space(@class)," ")," mrr-section-card ")][1]')
      for (const width of [1440, 375]) {
        await page.setViewportSize({ width, height: 900 })
        await expect.poll(() => page.evaluate(() => document.body.dataset.mode)).toBe(width === 375 ? 'mobile' : 'pc')
        const header = () => table.evaluate((el) => {
          const cell = el.querySelector('th.el-table__cell')!
          const s = getComputedStyle(cell)
          return { height: Math.round(cell.getBoundingClientRect().height), font: s.fontSize, background: s.backgroundColor, radius: getComputedStyle(el).borderRadius }
        })
        if (!reference) { reference = await header() }
        await expect.poll(header, { message: route }).toEqual(reference)
        await expect(table).toHaveClass(/el-table--striped/)
        await expect(panel).not.toHaveClass(/mrr-data-table-panel--quiet/)
        await expect.poll(() => panel.evaluate(el => [...el.querySelectorAll('.mrr-data-table-panel__filters .mrr-filter-bar--embedded')].every(f => getComputedStyle(f).borderBottomWidth === '0px' && getComputedStyle(f).paddingBottom === '0px'))).toBe(true)
        await page.mouse.move(0, 0)
        await expect.poll(() => table.evaluate(el => [...el.querySelectorAll('.mrr-table-actions')].every(a => a.classList.contains('mrr-table-actions--quiet') && [...a.querySelectorAll('.mrr-table-actions__button:not(.is-disabled)')].every(b => getComputedStyle(b).backgroundColor === 'rgba(0, 0, 0, 0)')))).toBe(true)
        await expect.poll(() => panel.evaluate((el) => {
          const footer = el.querySelector('.mrr-data-table-panel__pagination')
          const total = footer?.querySelector('.el-pagination__total')
          if (!total) { return true }
          const pager = total.closest('.el-pagination')!
          return !el.querySelector('.mrr-data-table-panel__count')
            && Math.abs(total.getBoundingClientRect().left - pager.getBoundingClientRect().left) <= 1
            && pager.scrollWidth <= pager.clientWidth + 1
        })).toBe(true)
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
      }
    }
    // The real OCR row demonstrates the shared compact menu outside users.
    const more = page.getByRole('row').filter({ hasText: 'synthetic-style' }).getByRole('button', { name: '更多操作' })
    await more.click()
    const menu = page.locator('.mrr-table-action-menu--quiet:visible')
    await expect(menu).toBeVisible()
    await expect(menu.locator('.el-popper__arrow')).toHaveCount(0)
    await expect(menu.getByRole('menuitem', { name: '取消', exact: true })).toHaveClass(/menu-item--danger/)
    await expect.poll(() => menu.evaluate((el) => {
      const r = el.getBoundingClientRect()
      return r.left >= -1 && r.right <= innerWidth + 1
    })).toBe(true)
    await page.keyboard.press('Escape')
  })
}
