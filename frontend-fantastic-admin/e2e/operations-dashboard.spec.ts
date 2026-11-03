import { expect, test } from '@playwright/test'

const overviewUrl = '**/api/v1/operations/overview'
const snapshotTime = '2026-11-02T01:00:00Z'

function snapshot(mode: 'READ_WRITE' | 'READ_ONLY_MAINTENANCE' | 'READ_ONLY_DEGRADED') {
  const degraded = mode === 'READ_ONLY_DEGRADED'
  return {
    generatedAt: snapshotTime,
    readiness: {
      mode,
      readOnly: mode !== 'READ_WRITE',
      automaticReadOnly: degraded,
      maintenanceReadOnly: mode !== 'READ_WRITE',
      checkedAt: snapshotTime,
      checks: [
        { code: 'backup', name: '最近备份', passed: !degraded, severity: 'WARNING', message: '未通过就绪检查' },
        { code: 'database', name: '数据库连接', passed: !degraded, severity: 'CRITICAL', message: '未通过就绪检查' },
      ],
    },
    runtime: { applicationVersion: 'test-version', startedAt: snapshotTime, uptimeMs: 60000, heapUsedBytes: 128, heapMaxBytes: 512 },
    taskSummary: { available: !degraded, active: 0, counts: { PENDING: 0, PROCESSING: 0, SUCCESS: 0, FAILED: 0 } },
    recentServerErrors: 0,
    recentServerErrorsAvailable: !degraded,
  }
}

test('shows the first load failure and distinguishes current, maintenance and stale data', async ({ page }) => {
  let requests = 0
  await page.route(overviewUrl, async (route) => {
    requests++
    if (requests === 1 || requests === 4) {
      await route.fulfill({ status: 503, json: { code: 503, message: '测试服务不可用' } })
      return
    }
    await route.fulfill({ json: { code: 200, data: snapshot(requests === 2 ? 'READ_WRITE' : 'READ_ONLY_MAINTENANCE') } })
  })
  await page.goto('/operations-center')
  const dashboard = page.locator('.overview-dashboard')
  const refresh = page.getByRole('button', { name: '刷新看板' })
  await expect(dashboard.getByText('无法获取状态')).toBeVisible()
  await expect(dashboard.getByText('总览快照')).toHaveCount(0)
  await refresh.click()
  await expect(dashboard.getByText('当前允许业务写入。')).toBeVisible()
  await expect(dashboard.getByText('等待 0 · 处理中 0')).toBeVisible()
  await refresh.click()
  await expect(dashboard.getByText('主动维护已开启，业务写入暂停。')).toBeVisible()
  await refresh.click()
  await expect(dashboard.getByText('刷新失败，当前为上次成功数据')).toBeVisible()
  await expect(dashboard.getByText('上次记录：主动维护只读')).toBeVisible()
  await expect(dashboard.getByText('主动维护已开启，业务写入暂停。')).toHaveCount(0)
  await expect(dashboard.getByText('总览快照', { exact: false })).toBeVisible()
  expect(requests).toBe(4)
})

test('prioritizes serious issues, shows both read-only reasons and fits narrow screens', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route(overviewUrl, route => route.fulfill({ json: { code: 200, data: snapshot('READ_ONLY_DEGRADED') } }))
  await page.goto('/operations-center')
  const dashboard = page.locator('.overview-dashboard')
  await expect(dashboard.getByText('自动只读降级：数据库连接检查未通过。')).toBeVisible()
  await expect(dashboard.getByText('主动维护已开启，业务写入暂停。')).toBeVisible()
  await expect(dashboard.getByText('不可用', { exact: true })).toHaveCount(2)
  const issues = dashboard.locator('.issue-list .issue-row')
  await expect(issues.first()).toContainText('数据库连接')
  await expect(issues.nth(1)).toContainText('最近备份')
  await expect.poll(() => dashboard.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
  await issues.first().getByRole('button', { name: '查看体检' }).click()
  await expect(page).toHaveURL(/tab=diagnostics/)
  await expect(page.getByRole('button', { name: '开始全面体检' })).toBeVisible()
})
