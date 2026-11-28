import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('真实可用宽度驱动筛选栏，保留表格布局及查询按钮', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { enableMotion: false, pageTitleStyle: 'card' }, layout: { enableMobileAdaptation: true } })))
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/records')
  const filter = page.locator('.mrr-filter-bar').first()
  await expect(filter).toBeVisible()
  await expect(page.locator('.records-table')).toBeVisible()
  // Simulate available content width reduced by a workbench/rail, independently of viewport.
  await page.addStyleTag({ content: '.mrr-page-shell { max-width: 540px !important; }' })
  await expect(filter).toHaveClass(/mrr-filter-bar--narrow/)
  await expect(filter).toHaveCSS('flex-direction', 'column')
  await expect(filter.getByRole('button', { name: '查询', exact: true })).toBeVisible()
  await expect.poll(() => filter.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await expect.poll(() => page.locator('.mrr-data-table-panel__pagination').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await expect(page.locator('.records-table th').first()).toHaveCSS('height', '42px')
})

test('记录详情长字段完整呈现，窄屏单列且操作始终可见', async ({ page }, testInfo) => {
  await isolateLocalVisualSettings(page)
  const file = `synthetic-${'abcdef'.repeat(40)}.pdf`
  await page.route('**/api/v1/scan/cursor/condition?**', route => route.fulfill({ json: { code: 200, data: { list: [{ id: 1, bah: 'SYN-001', brxh: 'P001', sjh: 'S001', filename: file, pages: 3, btype: 1, folder: `/synthetic/${'folder'.repeat(40)}`, migrationStatus: 'waiting_sjh' }], size: 20, hasMore: false } } }))
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/records')
  await page.getByRole('button', { name: '查看详情', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '记录详情' })
  const details = dialog.locator('.mrr-detail-descriptions')
  await expect(details).toContainText(file)
  await expect(details).toContainText('等待上架号')
  for (const width of [1440, 375]) {
    await page.setViewportSize({ width, height: 900 })
    await expect.poll(() => details.evaluate((el) => {
      const table = el.querySelector('table')!
      return table.scrollWidth <= el.clientWidth + 1 && Array.from(table.querySelectorAll('td')).every(cell => cell.scrollWidth <= cell.clientWidth + 1)
    })).toBe(true)
    await expect(dialog.locator('.el-dialog__headerbtn')).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath(`record-detail-${width}.png`), animations: 'disabled' })
  }
  await dialog.locator('.el-dialog__headerbtn').click()
  await expect(dialog).not.toBeVisible()
})

test('公共导航不再以渐隐遮罩裁切左侧操作', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { enableMotion: false }, layout: { enableMobileAdaptation: true }, menu: { mode: 'side' } })))
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/users')
  const left = page.locator('.toolbar-container .left-side')
  await expect(left).toBeVisible()
  await expect(left).toHaveCSS('mask-image', 'none')
  const breadcrumb = page.getByRole('navigation', { name: '面包屑导航' })
  await expect(breadcrumb).toBeVisible()
  await expect.poll(() => breadcrumb.locator('.breadcrumb-item:last-child .text').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await page.setViewportSize({ width: 375, height: 812 })
  const toggle = page.getByRole('button', { name: '切换侧栏导航' })
  await expect(toggle).toBeVisible()
  await expect.poll(() => toggle.evaluate((el) => {
    const r = el.getBoundingClientRect()
    const p = el.closest('.left-side')!.getBoundingClientRect()
    return r.left >= p.left - 1 && r.right <= p.right + 1
  })).toBe(true)
})
