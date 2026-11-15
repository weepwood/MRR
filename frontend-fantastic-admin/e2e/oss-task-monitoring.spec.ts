import { expect, test } from '@playwright/test'

const active = { id: 99, status: 'running', mode: 'pilot', totalCount: 10, processedCount: 1, failedCount: 0, rate: 10 }
test.beforeEach(async ({ page }) => {
  await page.route(/\/api\/v1\/oss\/migration\/readiness(?:\?|$)/, route => route.fulfill({ json: { code: 200, data: { ready: false, ossConfigured: true, sourcePathConfigured: true, sourcePathReadable: true, noActiveJob: false, pendingCount: 0, sampleSize: 0, sampleReadableCount: 0, sampleMissingCount: 0, sampleInvalidCount: 0, activeJob: active, warnings: [] } } }))
  await page.route(/\/api\/v1\/oss\/migration\/jobs\/99$/, route => route.fulfill({ json: { code: 200, data: active } }))
})

test('查看历史页不清除活动任务，历史失败可重试', async ({ page }) => {
  let fail = true
  await page.route(/\/api\/v1\/oss\/migration\/jobs(?:\?|$)/, async (route) => {
    const current = Number(new URL(route.request().url()).searchParams.get('page') || 1)
    if (current === 2 && fail) {
      fail = false
      await route.fulfill({ status: 503, json: { code: 503, message: '合成故障' } })
      return
    }
    await route.fulfill({ json: { code: 200, data: { list: [{ id: current, mode: 'pilot', status: 'completed' }], total: 20 } } })
  })
  await page.goto('/oss-migration')
  const history = page.locator('.el-card').filter({ has: page.getByText('迁移任务历史', { exact: true }) })
  await expect(page.locator('.current-job-card')).toContainText('当前任务 #99')
  await history.locator('.btn-next').click()
  await expect(history).toContainText('任务历史读取失败，当前仍显示上次成功读取的第 1 页')
  await expect(page.locator('.current-job-card')).toContainText('当前任务 #99')
  await history.getByRole('button', { name: '刷新任务' }).click()
  await expect(history.locator('.el-table__body .el-table__row td').first()).toHaveText('2')
  await expect(history).not.toContainText('任务历史读取失败')
  await expect(page.locator('.current-job-card')).toContainText('当前任务 #99')
})

test('详情失败不冒充完整信息，可重新加载', async ({ page }) => {
  let fail = true
  await page.route(/\/api\/v1\/oss\/migration\/jobs(?:\?|$)/, route => route.fulfill({ json: { code: 200, data: { list: [{ id: 1, status: 'completed', mode: 'pilot' }], total: 1 } } }))
  await page.route(/\/api\/v1\/oss\/migration\/jobs\/1$/, async (route) => {
    if (fail) { fail = false; await route.fulfill({ status: 503, json: { code: 503 } }) }
    else { await route.fulfill({ json: { code: 200, data: { id: 1, status: 'completed', totalCount: 123, processedCount: 100 } } }) }
  })
  await page.goto('/oss-migration')
  const history = page.locator('.el-card').filter({ has: page.getByText('迁移任务历史', { exact: true }) })
  await history.getByRole('button', { name: '查看任务详情', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '迁移任务详情' })
  await expect(drawer).toContainText('任务详情暂不可用')
  await expect(drawer.locator('.el-descriptions')).toHaveCount(0)
  await drawer.getByRole('button', { name: '重新加载详情' }).click()
  await expect(drawer).toContainText('100 / 123')
  await expect(drawer).not.toContainText('任务详情暂不可用')
})
