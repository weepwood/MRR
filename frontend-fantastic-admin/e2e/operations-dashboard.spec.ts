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

test('维护模式确认保留非空校验，按 Esc 退出确认不发送写请求', async ({ page }) => {
  let enabled = false
  let enables = 0
  let disables = 0
  await page.route(overviewUrl, route => route.fulfill({
    json: { code: 200, data: snapshot(enabled ? 'READ_ONLY_MAINTENANCE' : 'READ_WRITE') },
  }))
  await page.route('**/api/v1/operations/readiness', route => route.fulfill({
    json: { code: 200, data: snapshot(enabled ? 'READ_ONLY_MAINTENANCE' : 'READ_WRITE').readiness },
  }))
  await page.route('**/api/v1/operations/maintenance', route => route.fulfill({ json: { code: 200, data: { enabled } } }))
  await page.route('**/api/v1/operations/maintenance/enable', async (route) => {
    enables++
    expect(route.request().postDataJSON()).toEqual({ reason: '演练维护' })
    enabled = true
    await route.fulfill({ json: { code: 200, data: { enabled, reason: '演练维护' } } })
  })
  await page.route('**/api/v1/operations/maintenance/disable', async (route) => {
    disables++
    enabled = false
    await route.fulfill({ json: { code: 200, data: { enabled } } })
  })

  await page.goto('/operations-center?tab=maintenance')
  await expect(page.getByText('主动维护模式未启用')).toBeVisible()
  await page.getByRole('button', { name: '进入维护模式', exact: true }).click()
  const enableDialog = page.getByRole('dialog', { name: '进入主动维护模式' })
  const reason = enableDialog.getByRole('textbox', { name: '请输入本次维护原因' })
  await expect(enableDialog).toHaveClass(/\bel-overlay-dialog\b/)
  await expect(reason).toBeFocused()
  await reason.press('Enter')
  await expect(enableDialog.getByText('维护原因不能为空')).toBeVisible()
  expect(enables).toBe(0)
  await reason.fill(' 演练维护 ')
  await reason.press('Enter')
  await expect(page.getByText('主动维护模式已启用')).toBeVisible()
  expect(enables).toBe(1)

  const exit = page.getByRole('button', { name: '退出主动维护', exact: true })
  const disableDialog = page.getByRole('dialog', { name: '退出主动维护模式' })
  await exit.click()
  await expect(disableDialog).toHaveClass(/\bel-overlay-dialog\b/)
  await page.keyboard.press('Escape')
  await expect(disableDialog).toBeHidden()
  await expect(exit).toBeFocused()
  expect(disables).toBe(0)

  await exit.click()
  await disableDialog.getByRole('button', { name: '确认退出' }).click()
  await expect(page.getByText('主动维护模式未启用')).toBeVisible()
  expect(disables).toBe(1)
})


test('shows configured delivery mode and read-only partition status, labels stale on failure', async ({ page }) => {
  await page.route(overviewUrl, route => route.fulfill({
    json: { code: 200, data: { ...snapshot('READ_WRITE'), imageDeliveryMode: 'gateway' } },
  }))
  let calls = 0
  await page.route('**/api/v1/operations/access-log-partition/status', async (route) => {
    expect(route.request().method()).toBe('GET')
    calls++
    if (calls > 1) {
      await route.fulfill({ status: 503, json: { code: 503, message: '模拟状态失败' } })
      return
    }
    await route.fulfill({ json: { code: 200, data: {
      phase: 'COPYING', partitionCount: 4, rowsCopied: 1200, copyLag: 42,
      copyEnabled: false, defaultPartitionHasRows: true, legacyTablePresent: false,
      updatedAt: snapshotTime,
    } } })
  })
  await page.goto('/operations-center')
  const card = page.locator('.delivery-status-card')
  await expect(card.getByText('受控网关', { exact: true })).toBeVisible()
  await expect(card.getByText('复制中', { exact: true })).toBeVisible()
  await expect(card.getByText('1,200', { exact: true })).toBeVisible()
  await expect(card.getByText('42', { exact: true })).toBeVisible()
  await expect(card.getByText('有数据', { exact: true })).toBeVisible()
  await card.getByRole('button', { name: '刷新分区状态' }).click()
  await expect(card.getByText('刷新失败，以下为上次分区状态')).toBeVisible()
  await expect(card.getByText('1,200', { exact: true })).toBeVisible()
  await expect(card.getByRole('button', { name: /切换|回退|启动复制|放弃/ })).toHaveCount(0)
})
