import { expect, test } from '@playwright/test'

test('演示数据表浏览共享记录、脱敏字段、分页与精确过滤', async ({ page }) => {
  await page.goto('/database-browse')
  await expect(page.getByRole('heading', { name: '数据表浏览', exact: true })).toBeVisible()
  await page.getByRole('combobox').click()
  await page.getByRole('option').filter({ hasText: '影像记录（合成）' }).click()
  await expect(page.locator('.mrr-cursor-pagination .el-pager .is-active')).toHaveText('1')
  await expect(page.locator('.mrr-cursor-pagination__summary')).toContainText('本页 50 行')
  await expect(page.locator('.result-table').getByText('26****31', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(page.locator('.mrr-cursor-pagination .el-pager .is-active')).toHaveText('2')
  await expect(page.locator('.mrr-cursor-pagination__summary')).toContainText('本页 14 行')
  await expect(page.locator('.result-table td.mrr-table-sequence .cell').first()).toHaveText('51')
  await page.getByRole('button', { name: '上一页', exact: true }).click()
  await expect(page.locator('.mrr-cursor-pagination .el-pager .is-active')).toHaveText('1')
  await expect(page.locator('.mrr-cursor-pagination__summary')).toContainText('本页 50 行')
  await page.getByPlaceholder('输入病案号', { exact: true }).fill('26070000')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('.mrr-cursor-pagination .el-pager .is-active')).toHaveText('1')
  await expect(page.locator('.mrr-cursor-pagination__summary')).toContainText('本页 2 行')
  await expect(page.locator('.result-table td.mrr-table-sequence .cell')).toHaveText(['1', '2'])
  await expect(page.locator('.result-table').getByText('26****00', { exact: true })).toHaveCount(2)
  await expect(page.getByRole('button', { name: '下一页', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(page.locator('.mrr-cursor-pagination .el-pager .is-active')).toHaveText('1')
  await expect(page.locator('.mrr-cursor-pagination__summary')).toContainText('本页 50 行')
})

test('游标结果刷新期间保留上一页，首次查询使用独立骨架', async ({ page }) => {
  let release: (() => void) | undefined
  await page.route('**/api/v1/system/database/browse/tables/scan/rows**', async (route) => {
    await new Promise<void>((resolve) => { release = resolve })
    await route.fallback()
  })
  try {
    await page.goto('/database-browse')
    await page.getByRole('button', { name: '浏览影像记录（合成）' }).click()
    await expect.poll(() => Boolean(release)).toBe(true)
    await expect(page.locator('.mrr-data-table-panel__content .app-loading')).toBeVisible()
    await expect(page.locator('.result-table')).toHaveCount(0)
    release?.()
    release = undefined
    await expect(page.locator('.mrr-cursor-pagination .el-pager .is-active')).toHaveText('1')
    await expect(page.locator('.mrr-cursor-pagination__summary')).toContainText('本页 50 行')

    await page.getByRole('button', { name: '下一页', exact: true }).click()
    await expect.poll(() => Boolean(release)).toBe(true)
    await expect(page.locator('.mrr-data-table-panel__content')).toHaveAttribute('aria-busy', 'true')
    await expect(page.locator('.result-table').getByText('26****31', { exact: true }).first()).toBeVisible()
    await expect(page.locator('.mrr-cursor-pagination .el-pager .is-active')).toHaveText('1')
    await expect(page.locator('.mrr-cursor-pagination__summary')).toContainText('本页 50 行')
    await expect(page.getByRole('button', { name: '下一页', exact: true })).toBeDisabled()
    release?.()
    release = undefined
    await expect(page.locator('.mrr-cursor-pagination .el-pager .is-active')).toHaveText('2')
    await expect(page.locator('.mrr-cursor-pagination__summary')).toContainText('本页 14 行')
    await expect(page.locator('.mrr-data-table-panel__content')).not.toHaveAttribute('aria-busy', 'true')
  }
  finally {
    release?.()
  }
})
