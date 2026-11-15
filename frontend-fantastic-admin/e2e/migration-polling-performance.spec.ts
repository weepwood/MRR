import { expect, test } from '@playwright/test'

test('缓存迁移页离开后60秒无轮询，返回恢复且保留手动刷新选项', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { enableMotion: false },
    menu: { mode: 'single' },
  })))
  const job = { id: 91001, status: 'running', mode: 'pilot', processedCount: 10, totalCount: 100, rate: 10 }
  let reads = 0
  let taskReads = 0
  let overviewReads = 0
  await page.route('**/api/v1/oss/migration/**', async (route) => {
    expect(route.request().method()).toBe('GET')
    reads++
    const path = new URL(route.request().url()).pathname
    let data: unknown = { list: [], total: 0 }
    if (path.endsWith('/statistics')) {
      overviewReads++
      data = { totalCount: 100, pendingCount: 90, migratedCount: 10 }
    }
    else if (path.endsWith('/readiness')) {
      data = { ready: true, ossConfigured: true, sourcePathReadable: true, noActiveJob: false, activeJob: job, sampleSize: 1, sampleReadableCount: 1 }
    }
    else if (path.endsWith('/jobs/91001')) {
      taskReads++
      data = job
    }
    else if (path.endsWith('/jobs')) {
      data = { list: [job], total: 1 }
    }
    else if (path.endsWith('/pending-folders')) {
      data = []
    }
    await route.fulfill({ json: { code: 200, data } })
  })
  await page.clock.install()
  await page.goto('/oss-migration')
  await expect(page.getByRole('heading', { name: 'OSS 迁移管理', exact: true })).toBeVisible()
  await expect(page.locator('.refresh-state > span')).toContainText(/\d{4}/)
  await page.clock.pauseAt(new Date(await page.evaluate(() => Date.now()) + 1000))
  await page.clock.runFor(2000)
  await expect.poll(() => taskReads).toBeGreaterThan(0)
  await page.getByText('限定批次', { exact: true }).click()
  await page.locator('.refresh-state .el-switch').click()
  await expect(page.getByRole('switch')).not.toBeChecked()

  await page.locator('.sub-sidebar-container .menu-item-container[title="系统"]').click()
  await page.clock.runFor(500)
  await page.getByRole('link', { name: '用户管理', exact: true }).click()
  await expect(page.getByRole('heading', { name: '用户管理', exact: true })).toBeVisible()
  const awayReads = reads
  await page.clock.runFor(60_000)
  expect(reads).toBe(awayReads)

  const beforeReturn = taskReads
  const beforeOverview = overviewReads
  await page.goBack()
  await expect(page.getByRole('heading', { name: 'OSS 迁移管理', exact: true })).toBeVisible()
  await expect(page.getByRole('radio', { name: '限定批次', exact: true })).toBeChecked()
  await expect(page.getByRole('switch')).not.toBeChecked()
  await expect.poll(() => taskReads).toBeGreaterThan(beforeReturn)
  expect(overviewReads).toBe(beforeOverview)
})
