import { expect, test } from '@playwright/test'

test('运行错误详情拒绝错位响应、可重试且关闭后清除旧详情', async ({ page }) => {
  let attempt = 0
  await page.route('**/api/v1/system-errors/1', async (route) => {
    attempt++
    if (attempt === 1) {
      await route.fulfill({ json: { code: 200, data: { id: 2, errorId: 'DEMO-ERR-002', stackTrace: 'OTHER-SYNTHETIC-STACK' } } })
      return
    }
    await route.fallback()
  })
  await page.goto('/runtime-errors')
  const first = page.getByRole('row').filter({ hasText: 'DEMO-ERR-001' })
  await first.getByRole('button', { name: '详情', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '运行错误详情' })
  await expect(dialog.getByText('无法读取运行错误详情，请重试')).toBeVisible()
  await expect(dialog.getByText('OTHER-SYNTHETIC-STACK')).toHaveCount(0)
  await dialog.getByRole('button', { name: /重试/ }).click()
  await expect(dialog.getByText('合成异常摘要：无真实运行堆栈')).toBeVisible()
  await expect(dialog.getByText('DEMO-ERR-001', { exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await page.getByRole('row').filter({ hasText: 'DEMO-ERR-002' }).getByRole('button', { name: '详情', exact: true }).click()
  await expect(dialog.getByText('DEMO-ERR-002', { exact: true })).toBeVisible()
  await expect(dialog.getByText('DEMO-ERR-001', { exact: true })).toHaveCount(0)
  expect(attempt).toBe(2)
})

test('运行错误概览故障不阻塞列表，独立重试恢复指标', async ({ page }) => {
  let releaseOverview!: () => void
  const overviewGate = new Promise<void>((resolve) => { releaseOverview = resolve })
  let fail = true
  let listRequests = 0
  await page.route(/\/api\/v1\/system-errors(?:\?|$)/, async (route) => {
    listRequests++
    await route.fallback()
  })
  await page.route('**/api/v1/system-errors/overview', async (route) => {
    await overviewGate
    if (fail) {
      await route.fulfill({ status: 503, json: { code: 503, message: 'synthetic overview outage' } })
      return
    }
    await route.fallback()
  })
  await page.goto('/runtime-errors')
  const overview = page.getByRole('region', { name: '运行错误概览' })
  try {
    await expect(page.getByRole('row').filter({ hasText: 'DEMO-ERR-001' })).toBeVisible()
    await expect(overview.getByText('—', { exact: true })).toHaveCount(4)
  }
  finally {
    releaseOverview()
  }
  await expect(page.getByText('概览加载失败，不影响错误列表。')).toBeVisible()
  await expect(page.getByRole('row').filter({ hasText: 'DEMO-ERR-001' })).toBeVisible()
  const previousListRequests = listRequests
  fail = false
  await page.getByRole('button', { name: '重试概览' }).click()
  await expect(page.getByText('概览加载失败，不影响错误列表。')).toHaveCount(0)
  await expect(overview.getByText('—', { exact: true })).toHaveCount(0)
  expect(listRequests).toBe(previousListRequests)
})
