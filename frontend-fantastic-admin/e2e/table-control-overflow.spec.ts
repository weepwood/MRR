import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('业务表格的标签与操作控件完整显示', async ({ page }) => {
  test.setTimeout(180_000)
  await isolateLocalVisualSettings(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.route('**/api/v1/ocr/batches**', route => route.fulfill({ json: { code: 200, data: [{ id: 'synthetic-style', status: 'RUNNING', cursorId: 100, processedCount: 80, dispatchedCount: 120, successCount: 78, failedCount: 2, autoAppliedCount: 0, reviewRequiredCount: 0, autoApply: false }] } }))
  const issues: unknown[] = []
  for (const route of ['records', 'users', 'patients', 'logs', 'audit-images', 'archive-boxes', 'runtime-errors', 'statistics', 'ocr-batches', 'oss-migration', 'operations-center', 'monitoring', 'data-relations', 'response-analysis', 'ui-components']) {
    await page.goto(`/${route}`)
    await page.waitForLoadState('networkidle', { timeout: 2500 }).catch(() => {})
    if (['records', 'users', 'patients', 'logs', 'audit-images', 'archive-boxes', 'runtime-errors', 'ocr-batches', 'oss-migration'].includes(route)) {
      await expect(page.locator('td.el-table__cell .mrr-status-tag, td.el-table__cell .el-tag, td.el-table__cell .mrr-table-actions').first()).toBeVisible()
    }
    const result = await page.evaluate(() => {
      const findings: unknown[] = []
      const controls = [...document.querySelectorAll<HTMLElement>('td.el-table__cell .mrr-status-tag, td.el-table__cell .el-tag, td.el-table__cell .mrr-table-actions, td.el-table__cell .el-progress, td.el-table__cell .el-switch, td.el-table__cell .copy-link, td.el-table__cell .mrr-table-text-link')]
      for (const control of controls) {
        const cell = control.closest<HTMLElement>('.cell')
        if (!cell || !control.getClientRects().length || !control.getBoundingClientRect().width) { continue }
        const r = control.getBoundingClientRect()
        const c = cell.getBoundingClientRect()
        const cs = getComputedStyle(cell)
        const right = c.right - Number.parseFloat(cs.paddingRight)
        const left = c.left + Number.parseFloat(cs.paddingLeft)
        if (r.right > right + 1.5 || r.left < left - 1.5 || control.scrollWidth > control.clientWidth + 1) {
          findings.push({ text: control.textContent, control: control.className, width: Math.round(r.width), available: Math.round(right - left), excess: Math.round(Math.max(r.right - right, left - r.left, control.scrollWidth - control.clientWidth)), column: cell.parentElement?.className })
        }
      }
      return { count: controls.length, findings: [...new Map(findings.map(x => [JSON.stringify(x), x])).values()] }
    })
    if (route === 'records') {
      await expect(page.locator('.records-table .mrr-status-tag').filter({ hasText: '等待上架号' }).first()).toBeVisible()
    }
    await test.info().attach(route, { body: JSON.stringify(result), contentType: 'application/json' })
    issues.push(...result.findings.map(x => ({ route, issue: x })))
  }
  expect(issues).toEqual([])
})

test('超长未知迁移状态保留完整标签，长 Request ID 保留复制按钮', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  const code = 'future_migration_status_with_an_unexpected_long_code'
  await page.route('**/api/v1/scan/cursor/condition**', route => route.fulfill({ json: { code: 200, data: { list: [{ id: 1, bah: '00123', brxh: '0001', sjh: 'INVALID-1', filename: 'synthetic.jpg', btype: 8, pages: 1, migrationStatus: code }], hasMore: false, size: 20 } } }))
  await page.goto('/records')
  const badge = page.locator('.records-table .mrr-status-tag')
  await expect(badge).toHaveText(code)
  await expect(badge).toHaveAttribute('title', code)
  for (const width of [1440, 375]) {
    await page.setViewportSize({ width, height: 900 })
    await expect.poll(() => badge.evaluate((el) => {
      const cell = el.closest('.cell')!
      const label = el.querySelector('.mrr-status-tag__label')!
      const dot = el.querySelector('.mrr-status-tag__dot')!
      const rect = el.getBoundingClientRect()
      const box = cell.getBoundingClientRect()
      return rect.right <= box.right - Number.parseFloat(getComputedStyle(cell).paddingRight) + 1
        && label.scrollWidth <= label.clientWidth + 1
        && label.scrollHeight <= label.clientHeight + 1
        && Math.abs(dot.getBoundingClientRect().width - 6) < 1
    })).toBe(true)
  }
  const requestId = `synthetic-request-${'1234567890'.repeat(12)}`
  await page.route('**/api/v1/logs/search**', route => route.fulfill({ json: { code: 200, data: { list: [{ id: 1, requestId, username: 'synthetic', method: 'GET', requestUri: '/test', responseStatus: 200, accessTime: '2026-11-26T10:00:00' }], total: 1, page: 1, size: 20 } } }))
  await page.goto('/logs')
  const group = page.locator('.mrr-table-inline-content')
  await expect(group.locator('.mrr-table-inline-content__text')).toHaveAttribute('title', requestId)
  await expect(group.getByRole('button', { name: '复制 Request ID' })).toBeVisible()
  await expect.poll(() => group.evaluate((el) => {
    const text = el.querySelector('.mrr-table-inline-content__text')!
    const button = el.querySelector('button')!
    return text.scrollWidth > text.clientWidth && button.getBoundingClientRect().right <= el.getBoundingClientRect().right + 1
  })).toBe(true)
})
