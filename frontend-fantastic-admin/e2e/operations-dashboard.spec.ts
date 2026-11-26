import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

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
  const taskInfo = dashboard.getByRole('button', { name: '查看活跃导出任务说明' })
  await expect(taskInfo).toBeVisible()
  await taskInfo.hover()
  await expect(page.getByText('等待 0 · 处理中 0', { exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
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
      phase: 'COPYING',
      partitionCount: 4,
      rowsCopied: 1200,
      copyLag: 42,
      copyEnabled: false,
      defaultPartitionHasRows: true,
      legacyTablePresent: false,
      updatedAt: snapshotTime,
    } } })
  })
  await page.goto('/operations-center')
  const card = page.locator('.delivery-status-card')
  await expect(card.getByText('受控网关', { exact: true })).toBeVisible()
  await expect(card.getByText('复制中', { exact: true })).toBeVisible()
  await expect(card.getByText('1,200', { exact: true })).toBeVisible()
  await expect(card.getByText('42', { exact: true })).toBeVisible()
  await expect(card.getByText('状态获取时间', { exact: true })).toBeVisible()
  await expect(card.getByText('迁移状态更新时间', { exact: true })).toBeVisible()
  await expect(card.getByText('有数据', { exact: true })).toBeVisible()
  await card.getByRole('button', { name: '刷新分区状态' }).click()
  await expect(card.getByText('刷新失败，以下为上次分区状态')).toBeVisible()
  await expect(card.getByText('1,200', { exact: true })).toBeVisible()
  await expect(card.getByRole('button', { name: /切换|回退|启动复制|放弃/ })).toHaveCount(0)
})

test('索引容量展示区分估算、真实容量与服务器剩余空间', async ({ page }) => {
  const data = snapshot('READ_WRITE')
  data.readiness.checks.push({ code: 'scan-code-key-indexes', name: '病案影像查询索引', passed: true, severity: 'WARNING', message: '符合就绪条件', ...{ details: { state: 'READY', estimatedRows: 30000000, totalBytes: 20015456256 } } })
  await page.route(overviewUrl, route => route.fulfill({ json: { code: 200, data } }))
  await page.goto('/operations-center')
  const dashboard = page.locator('.overview-dashboard')
  await expect(dashboard.getByText('编号索引与容量', { exact: true })).toBeVisible()
  await expect(dashboard.getByText('30,000,000', { exact: true })).toBeVisible()
  await expect(dashboard.getByText('未取得', { exact: true })).toBeVisible()
  await expect(dashboard.getByText('容量参考按三千万合成实测换算', { exact: false })).toBeVisible()
})

test('NAS 节点熔断和实际许可状态可见但不显示源地址', async ({ page }) => {
  const data = { ...snapshot('READ_WRITE'), nasRuntime: { inUse: 1, limit: 2, nodes: [{ ordinal: 1, state: 'OPEN', inUse: 1, limit: 1 }, { ordinal: 2, state: 'HALF_OPEN_ACTIVE', inUse: 0, limit: 1 }] } }
  await page.route(overviewUrl, route => route.fulfill({ json: { code: 200, data } }))
  await page.goto('/operations-center')
  const dashboard = page.locator('.overview-dashboard')
  await expect(dashboard.getByText('NAS 节点运行状态', { exact: true })).toBeVisible()
  await expect(dashboard.getByText('全局许可 1 / 2')).toBeVisible()
  await expect(dashboard.getByText('熔断冷却', { exact: true })).toBeVisible()
  await expect(dashboard.getByText('探针进行中')).toBeVisible()
})

test('网络影像来源异常优先并区分应用许可和SDK采样', async ({ page }) => {
  const data = { ...snapshot('READ_WRITE'), networkRuntime: {
    HTTP: { inUse: 0, limit: 8, waiting: 0, nodes: [{ ordinal: 1, state: 'CLOSED' }] },
    NGINX: { inUse: 1, limit: 8, waiting: 2, nodes: [{ ordinal: 1, state: 'OPEN' }] },
    OSS: { inUse: 0, limit: 8, waiting: 0, nodes: [], poolSample: {} },
  } }
  await page.route(overviewUrl, route => route.fulfill({ json: { code: 200, data } }))
  await page.goto('/operations-center')
  const rows = page.locator('.network-runtime-row')
  await expect(rows.first()).toContainText('NGINX')
  await expect(rows.first()).toContainText('熔断冷却')
  await expect(rows.first()).toContainText('等待约 2')
  await expect(page.getByText('SDK 池尚无有效采样')).toBeVisible()
  await expect(page.getByText('以上许可并非 TCP 连接数', { exact: false })).toBeVisible()
  data.networkRuntime.NGINX.nodes[0].state = 'CLOSED'
  await page.getByRole('button', { name: '刷新看板' }).click()
  await expect(page.getByText('较上次成功快照，熔断或半开数量变化：NGINX')).toBeVisible()
})

test('存储异常事件展示有界累计及恢复建议', async ({ page }) => {
  const data = { ...snapshot('READ_WRITE'), nasRuntime: { inUse: 1, limit: 2, waiting: 1, nodes: [{ ordinal: 1, state: 'OPEN', inUse: 1, limit: 1, history: { opened: 20, recovered: 19, probes: 19, events: [{ sequence: 59, type: 'OPENED', at: '2026-11-02T00:59:00Z' }] } }] } }
  await page.route(overviewUrl, route => route.fulfill({ json: { code: 200, data } }))
  await page.goto('/operations-center')
  const dashboard = page.locator('.overview-dashboard')
  await expect(dashboard.getByText('存储异常事件', { exact: true })).toBeVisible()
  await expect(dashboard.getByText('进入熔断次数', { exact: true })).toBeVisible()
  await expect(dashboard.getByText('最近 30 分钟已保留事件', { exact: false })).toBeVisible()
  await expect(dashboard.locator('.storage-trend-row').last()).toContainText('熔断 1')
  await expect(dashboard.getByText('NAS 节点 1 · 进入熔断')).toBeVisible()
  await expect(dashboard.getByText('不要强制释放未关闭资源', { exact: false })).toBeVisible()
  await expect(dashboard.getByText('重启清空', { exact: false })).toBeVisible()
})

test('单张正文校验使用明确ID并区分无基线与完整校验', async ({ page }) => {
  let requests = 0
  await page.route('**/api/v1/operations/image-content/verify', async (route) => {
    requests++
    expect(route.request().method()).toBe('POST')
    expect(route.request().postDataJSON()).toEqual({ imageId: 1 })
    await route.fulfill({ json: { code: 200, data: { imageId: 1, status: 'READABLE_NO_BASELINE', bytesRead: 1024, checkedAt: snapshotTime, scope: 'SINGLE_IMAGE_BOUNDED' } } })
  })
  await page.goto('/operations-center?tab=image')
  const button = page.getByRole('button', { name: '校验单张正文' })
  await expect(button).toBeDisabled()
  const id = page.getByRole('spinbutton')
  await id.fill('1')
  await button.click()
  await expect(page.getByText('可读，缺少有效校验基线')).toBeVisible()
  await expect(page.getByText('不代表整案完整性', { exact: false })).toBeVisible()
  await id.fill('2')
  await expect(page.getByText('可读，缺少有效校验基线')).toHaveCount(0)
  expect(requests).toBe(1)
})

test('持久运行历史展示相邻采样变化、缺口及七天范围', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    layout: { enableMobileAdaptation: true },
  })))
  await page.goto('/operations-center')
  await expect(page.getByRole('heading', { name: '运行历史与容量趋势' })).toBeVisible()
  await expect(page.getByRole('img', { name: 'JVM 堆内存历史' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '相邻采样状态变化' })).toBeVisible()
  await expect(page.getByText('正常读写 → 自动降级', { exact: false }).first()).toBeVisible()
  const response = page.waitForResponse(r => r.url().includes('/operations/history') && r.url().includes('hours=168'))
  await page.getByText('7 天', { exact: true }).click()
  const result = (await (await response).json()).data
  expect(result.hours).toBe(168)
  expect(result.samples.length).toBeLessThan(2017)
  await expect(page.getByRole('img', { name: '扫描表容量历史' })).toBeVisible()
  await page.setViewportSize({ width: 390, height: 844 })
  await expect.poll(() => page.locator('.overview-dashboard').evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
  await page.screenshot({ path: test.info().outputPath('operations-history.png'), fullPage: true })
})

test('历史未启用时不展示伪造的零值图表', async ({ page }) => {
  await page.route('**/api/v1/operations/history?*', route => route.fulfill({ json: { code: 200, data: {
    enabled: false,
    hours: 24,
    bucketSeconds: 300,
    from: '2026-11-01T00:00:00Z',
    to: '2026-11-02T00:00:00Z',
    samples: [],
  } } }))
  await page.goto('/operations-center')
  await expect(page.getByText('持久历史采集尚未启用')).toBeVisible()
  await expect(page.getByRole('img', { name: 'JVM 堆内存历史' })).toHaveCount(0)
})

test('Nginx 默认关闭时禁止校验，显式检查仅发送固定请求', async ({ page }) => {
  let calls = 0
  await page.route('**/api/v1/operations/nginx', route => route.fulfill({ json: { code: 200, data: {
    enabled: false,
    state: 'DISABLED',
    configBytes: null,
    configModifiedAt: null,
  } } }))
  await page.route('**/api/v1/operations/nginx/check', (route) => {
    calls++
    expect(route.request().method()).toBe('POST')
    expect(route.request().postData()).toBeNull()
    return route.fulfill({ json: { code: 200, data: { status: 'FAILED', checkedAt: snapshotTime, exitCode: 1 } } })
  })
  await page.goto('/operations-center')
  await expect(page.getByText('配置检查未启用', { exact: true })).toBeVisible()
  const check = page.getByRole('button', { name: '校验配置语法' })
  await expect(check).toBeDisabled()
  expect(calls).toBe(0)
  await page.unroute('**/api/v1/operations/nginx')
  await page.route('**/api/v1/operations/nginx', route => route.fulfill({ json: { code: 200, data: {
    enabled: true,
    state: 'AVAILABLE',
    configBytes: 1234,
    configModifiedAt: snapshotTime,
  } } }))
  await page.getByRole('button', { name: '刷新配置状态' }).click()
  await expect(check).toBeEnabled()
  await check.click()
  await expect(page.getByText('配置语法校验失败', { exact: true })).toBeVisible()
  await expect(page.getByText('退出码 1', { exact: false })).toBeVisible()
  expect(calls).toBe(1)
  await page.route('**/api/v1/operations/nginx/version', (route) => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().postData()).toBeNull()
    return route.fulfill({ json: { code: 200, data: { state: 'AVAILABLE', version: '1.28.0', observedAt: snapshotTime } } })
  })
  await page.route('**/api/v1/operations/nginx/listener-hints', route => route.fulfill({ json: { code: 200, data: { state: 'TEXT_HINTS', ports: [80], observedAt: snapshotTime } } }))
  await page.getByRole('button', { name: '读取安装版本' }).click()
  await expect(page.getByText('安装程序版本 Nginx 1.28.0', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: '核对监听配置文本' }).click()
  await expect(page.getByText('独立数值 listen 文本提示：80', { exact: false })).toBeVisible()
  await expect(page.getByText('不是生效监听配置或端口状态', { exact: false })).toBeVisible()
})

test('持久存储熔断事件区分真实状态机事件与采样变化', async ({ page }) => {
  await page.goto('/operations-center')
  await expect(page.getByRole('heading', { name: '持久存储熔断事件' })).toBeVisible()
  await expect(page.getByText('已采集 5 条事件。', { exact: false })).toBeVisible()
  await expect(page.getByRole('cell', { name: '仅元数据成功', exact: true })).toBeVisible()
  const response = page.waitForResponse(r => r.url().includes('/storage-circuit-history') && r.url().includes('hours=168'))
  await page.getByText('近 7 天', { exact: true }).click()
  expect((await (await response).json()).data.hours).toBe(168)
  await expect(page.getByRole('cell', { name: '节点恢复', exact: true })).toBeVisible()
})

test('Nginx 按需观察进程并仅展示投影日志，切换类别清除旧结果', async ({ page }) => {
  let reads = 0
  await page.route('**/api/v1/operations/nginx/runtime', route => route.fulfill({ json: { code: 200, data: { state: 'PROCESS_MATCHED', observedAt: snapshotTime } } }))
  await page.route('**/api/v1/operations/nginx/logs?*', (route) => {
    reads++
    const kind = new URL(route.request().url()).searchParams.get('kind')
    return route.fulfill({ json: { code: 200, data: { state: 'AVAILABLE', kind, observedAt: snapshotTime, truncated: true, omitted: 1, entries: [kind === 'access' ? { time: snapshotTime, method: 'GET', status: 502, requestSeconds: 0.25, severity: null } : { time: '2026-11-02T01:00:00', method: null, status: null, requestSeconds: null, severity: 'error' }] } } })
  })
  await page.goto('/operations-center')
  await expect(page.getByRole('heading', { name: 'Nginx 运行观察与脱敏日志' })).toBeVisible()
  expect(reads).toBe(0)
  await page.getByRole('button', { name: '核对本地进程' }).click()
  await expect(page.getByText('PID 对应本地安装程序正在运行', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '读取脱敏日志' }).click()
  await expect(page.getByRole('cell', { name: '502', exact: true })).toBeVisible()
  await page.getByText('错误级别', { exact: true }).click()
  await expect(page.getByRole('cell', { name: '502', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '读取脱敏日志' }).click()
  await expect(page.getByRole('cell', { name: 'error', exact: true })).toBeVisible()
  expect(reads).toBe(2)
  await page.route('**/api/v1/operations/nginx/windows-observation', (route) => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().postData()).toBeNull()
    return route.fulfill({ json: { code: 200, data: { state: 'OBSERVED', serviceState: 'RUNNING', listenerState: 'OBSERVED', ports: [80], observedAt: snapshotTime } } })
  })
  await page.getByRole('button', { name: '核对 Windows 服务与 TCP 监听' }).click()
  await expect(page.getByText('MRR-Gateway 服务：运行中', { exact: true })).toBeVisible()
  await expect(page.getByText('已核对 Nginx 进程的本地 TCP 监听：80', { exact: true })).toBeVisible()
  await expect(page.getByText('不证明两者属于同一启动链', { exact: false })).toBeVisible()
})
