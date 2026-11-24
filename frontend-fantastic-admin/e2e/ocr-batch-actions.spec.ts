import { expect, test } from '@playwright/test'

const reply = (data: unknown) => ({ code: 200, message: 'success', data })

const runningBatch = {
  id: 'synthetic-running',
  status: 'RUNNING',
  afterId: 0,
  upperId: 9002,
  cursorId: 100,
  maxPages: 10000,
  dispatchedCount: 120,
  processedCount: 80,
  successCount: 78,
  failedCount: 2,
  autoAppliedCount: 0,
  reviewRequiredCount: 0,
  autoApply: false,
  reasonCode: '',
}

/**
 * 行操作里的「取消」是不可逆操作（批次进入 CANCEL 流程后无法恢复执行）。
 * 与导出取消、脏表单离开一致，执行前必须弹确认框；
 * 统一 catch 不得吞掉服务端错误信息（半夜排障需要线索）。
 */

test('取消运行中批次必须先确认，保持运行则不发请求', async ({ page }) => {
  let cancelRequested = false
  await page.route('**/api/v1/ocr/batches**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/cancel') && route.request().method() === 'POST') {
      cancelRequested = true
      await route.fulfill({ json: reply({ ...runningBatch, status: 'CANCEL_REQUESTED' }) })
    }
    else {
      await route.fulfill({ json: reply([runningBatch]) })
    }
  })
  await page.goto('/ocr-batches', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('cell', { name: 'synthetic-running', exact: true })).toBeVisible()

  await page.getByRole('row', { name: /synthetic-running/ }).getByRole('button', { name: '取消' }).click()

  // 确认框先出现，且此时还没有发出取消请求
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText('取消后批次「synthetic-running」')
  expect(cancelRequested).toBe(false)

  // 选择保持运行：关闭确认框，不发出请求
  await dialog.getByRole('button', { name: '保持运行' }).click()
  await expect(dialog).not.toBeVisible()
  await page.waitForTimeout(500)
  expect(cancelRequested).toBe(false)

  // 再次取消并确认：发出 POST，行状态变为取消中
  await page.getByRole('row', { name: /synthetic-running/ }).getByRole('button', { name: '取消' }).click()
  await dialog.getByRole('button', { name: '取消批次' }).click()
  await expect.poll(() => cancelRequested).toBe(true)
})

test('操作失败时展示服务端错误信息而不是泛化文案', async ({ page }) => {
  await page.route('**/api/v1/ocr/batches**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/cancel') && route.request().method() === 'POST') {
      await route.fulfill({ status: 500, json: { code: 500, message: 'OCR 服务中断（合成错误）', data: null } })
    }
    else {
      await route.fulfill({ json: reply([runningBatch]) })
    }
  })
  await page.goto('/ocr-batches', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('cell', { name: 'synthetic-running', exact: true })).toBeVisible()

  await page.getByRole('row', { name: /synthetic-running/ }).getByRole('button', { name: '取消' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '取消批次' }).click()

  await expect(page.getByText('OCR 服务中断（合成错误）')).toBeVisible({ timeout: 10_000 })
})
