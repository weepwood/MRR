import type { Page, Route } from '@playwright/test'
import { expect, test } from '@playwright/test'

const fileNames = ['img-api.log.2026-11-01.0.gz', 'img-api.log.2026-11-02.0.gz']
const files = fileNames.map(fileName => ({ fileName, bytes: 120 }))
const health = { pollAgeSeconds: 1, auditHmacConfigured: true, sources: [], backlog: { pending_files: 2, pending_raw_bytes: 128 } }
const first = { occurred_at: '2026-11-01T00:00:00Z', event_id: '00000000-0000-4000-8000-000000000001', level: 'WARN', logger_name: 'synthetic', request_id: 'synthetic-request', error_id: '', message_summary: 'synthetic first', stack_trace: '' }
const second = { ...first, event_id: '00000000-0000-4000-8000-000000000002', message_summary: 'synthetic second' }
const success = (route: Route, data: unknown) => route.fulfill({ json: { code: 200, message: 'success', data } })
const failure = (route: Route) => route.fulfill({ status: 503, json: { code: 503, message: 'synthetic unavailable' } })

async function openPage(page: Page, colorScheme = 'light') {
  await page.addInitScript((colorScheme) => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme }, layout: { enableMobileAdaptation: true } }))
  }, colorScheme)
  await page.goto('/runtime-logs')
  await expect(page.getByRole('cell', { name: first.message_summary, exact: true })).toBeVisible()
}

test('暗色窄屏文件清单失败不隐藏日志或健康信息，单独刷新可恢复', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 900 })
  let unavailable = true
  await page.route('**/api/v1/runtime-logs**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/health')) { return success(route, { ...health, unknownGap: true }) }
    if (path.endsWith('/files')) { return unavailable ? failure(route) : success(route, files) }
    return success(route, { list: [first], hasMore: false })
  })
  await openPage(page, 'dark')
  await expect(page.getByRole('alert').filter({ hasText: '日志文件清单读取失败' })).toBeVisible()
  await expect(page.getByText('日志存在缺口或无法确认完整性，请检查采集状态')).toBeVisible()
  await expect(page.getByRole('combobox', { name: '历史文件', exact: true })).toBeDisabled()
  await expect(page.getByRole('cell', { name: first.message_summary, exact: true })).toBeVisible()
  await page.screenshot({ path: test.info().outputPath('collector-failure-dark-375.png'), fullPage: true })
  unavailable = false
  await page.getByRole('button', { name: '刷新状态', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: '日志文件清单读取失败' })).toHaveCount(0)
  await expect(page.getByRole('combobox', { name: '历史文件', exact: true })).toBeEnabled()
  await expect.poll(() => page.locator('.runtime-log-page').evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
})

test('补录部分成功保留未确认文件，重试不重复登记成功文件', async ({ page }) => {
  const registered: string[] = []
  let rejectSecond = true
  let firstHealth = true
  await page.route('**/api/v1/runtime-logs**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/health')) {
      if (firstHealth) { firstHealth = false; return failure(route) }
      return success(route, health)
    }
    if (path.endsWith('/files')) { return success(route, files) }
    if (path.endsWith('/imports')) {
      const name = route.request().postDataJSON().fileName as string
      registered.push(name)
      return name === fileNames[1] && rejectSecond ? failure(route) : success(route, null)
    }
    return success(route, { list: [first], hasMore: false })
  })
  await openPage(page)
  await expect(page.getByRole('alert').filter({ hasText: '采集状态读取失败' })).toBeVisible()
  await page.screenshot({ path: test.info().outputPath('health-failure-light.png'), fullPage: true })
  await page.locator('.runtime-logs-files .el-select__wrapper').click()
  for (const name of fileNames) {
    await page.getByRole('option', { name, exact: true }).click()
  }
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '登记补录', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: '已登记 1 个文件，剩余 1 个未确认登记' })).toBeVisible()
  expect(registered).toEqual(fileNames)
  await expect(page.locator('.runtime-logs-files .el-tag')).toHaveCount(1)
  await expect(page.locator('.runtime-logs-files .el-tag')).toContainText(fileNames[1]!)
  await page.screenshot({ path: test.info().outputPath('partial-registration-light.png'), fullPage: true })
  rejectSecond = false
  await page.getByRole('button', { name: '登记补录', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: '已登记 1 个文件，后台将按批次处理' })).toBeVisible()
  expect(registered).toEqual([fileNames[0], fileNames[1], fileNames[1]])
  await expect(page.locator('.runtime-logs-files .el-tag')).toHaveCount(0)
})

test('翻批重试沿用原时间和游标，编辑中的筛选由显式查询应用', async ({ page }) => {
  const requests: Record<string, string>[] = []
  let failed = false
  await page.route('**/api/v1/runtime-logs**', (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/health')) { return success(route, health) }
    if (url.pathname.endsWith('/files')) { return success(route, files) }
    const params = Object.fromEntries(url.searchParams)
    requests.push(params)
    if (params.cursorId && !failed) { failed = true; return failure(route) }
    return success(route, { list: [params.cursorId ? second : first], hasMore: !params.cursorId })
  })
  await openPage(page)
  await page.getByRole('textbox', { name: '追踪编号', exact: true }).fill('synthetic-draft')
  await page.getByRole('button', { name: '下一批', exact: true }).click()
  await expect(page.locator('.app-error')).toBeVisible()
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.getByRole('cell', { name: second.message_summary, exact: true })).toBeVisible()
  expect(requests[2]).toEqual(requests[1])
  expect(requests[2]).toMatchObject({ from: requests[0]!.from, to: requests[0]!.to, cursorId: first.event_id, cursorTime: first.occurred_at })
  expect(requests[2]!.requestId).toBeUndefined()
  await page.getByRole('button', { name: '回到首批', exact: true }).click()
  await expect(page.getByRole('cell', { name: first.message_summary, exact: true })).toBeVisible()
  expect(requests[3]).toEqual(requests[0])
  await page.getByRole('button', { name: '刷新日志', exact: true }).click()
  await expect.poll(() => requests.length).toBe(5)
  expect(requests[4]).toEqual(requests[0])
  await page.getByRole('button', { name: '查询日志', exact: true }).click()
  await expect.poll(() => requests.at(-1)?.requestId).toBe('synthetic-draft')
})

test('清空时间后查询显示固定的最近24小时，下一批继续使用该范围', async ({ page }) => {
  const requests: Record<string, string>[] = []
  await page.route('**/api/v1/runtime-logs**', (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/health')) { return success(route, health) }
    if (url.pathname.endsWith('/files')) { return success(route, files) }
    const params = Object.fromEntries(url.searchParams)
    requests.push(params)
    return success(route, { list: [params.cursorId ? second : first], hasMore: !params.cursorId })
  })
  await openPage(page)
  const editor = page.locator('.filter-date .el-date-editor')
  await editor.hover()
  await editor.locator('.el-range__close-icon').click()
  await expect(editor.locator('.el-range-input').first()).toHaveValue('')
  await page.getByRole('button', { name: '查询日志', exact: true }).click()
  await expect.poll(() => requests.length).toBe(2)
  const applied = requests[1]!
  expect(Date.parse(applied.to!) - Date.parse(applied.from!)).toBe(86400000)
  for (const input of await editor.locator('.el-range-input').all()) {
    await expect(input).not.toHaveValue('')
  }
  await page.getByRole('button', { name: '下一批', exact: true }).click()
  await expect(page.getByRole('cell', { name: second.message_summary, exact: true })).toBeVisible()
  expect(requests[2]).toMatchObject({ from: applied.from, to: applied.to, cursorId: first.event_id })
})
