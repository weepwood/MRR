import { expect, test } from '@playwright/test'

const reply = (data: unknown) => ({ code: 200, message: 'success', data })

test('历史 OCR 创建绑定预览范围，缺少验收证据时不能授权自动分类', async ({ page }) => {
  let created: Record<string, unknown> | undefined
  let list: unknown[] = []
  await page.route('**/api/v1/ocr/batches**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/preview')) {
      await route.fulfill({ json: reply({ eligibleInLimit: 2, upperId: 9002, truncated: false, readiness: 'READY', autoAvailable: false, policyChecksum: null, autoTypes: [] }) })
    }
    else if (route.request().method() === 'POST') {
      created = route.request().postDataJSON()
      list = [{ id: 'synthetic-batch', status: 'PAUSED', afterId: 0, upperId: 9002, cursorId: 0, maxPages: 10000, dispatchedCount: 0, processedCount: 0, successCount: 0, failedCount: 0, autoAppliedCount: 0, reviewRequiredCount: 0, autoApply: false, reasonCode: 'USER_PAUSED' }]
      await route.fulfill({ json: reply(list[0]) })
    }
    else {
      await route.fulfill({ json: reply(list) })
    }
  })
  await page.goto('/ocr-batches', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: '历史未分类影像识别' })).toBeVisible()
  await expect(page.getByRole('checkbox', { name: /授权本批次/ })).toBeDisabled()
  await page.getByRole('button', { name: '预览范围', exact: true }).click()
  await expect(page.getByText(/扫描上界 9002/)).toBeVisible()
  await page.getByRole('button', { name: '创建此范围的识别批次' }).click()
  await expect(page.getByRole('cell', { name: 'synthetic-batch', exact: true })).toBeVisible()
  expect(created?.upperId).toBe(9002)
  expect(created?.autoApply).toBe(false)
  expect(created?.idempotencyKey).toMatch(/^batch-/)
  await page.screenshot({ path: 'test-results/ocr-batches-preview.png', fullPage: true })
})
