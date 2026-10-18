import { expect, test } from '@playwright/test'

test('日志页面显示缺口并按文本渲染日志，补录必须显式触发', async ({ page }) => {
  let imports = 0
  let dialogs = 0
  page.on('dialog', async dialog => { dialogs++; await dialog.dismiss() })
  await page.route('**/api/v1/runtime-logs**', async route => {
    const path = new URL(route.request().url()).pathname
    let data: unknown
    if (path.endsWith('/health')) data = { unknownGap: true, invalidLines: 1, auditHmacConfigured: true, pollAgeSeconds: 1, backlog: { pending_files: 2, pending_raw_bytes: 128 }, sources: [] }
    else if (path.endsWith('/files')) data = [{ fileName: 'img-api.log.2026-10-17.0.gz', bytes: 120 }]
    else if (path.endsWith('/imports')) { imports++; data = null }
    else data = { list: [{ occurred_at: '2026-10-17T00:00:00Z', event_id: '00000000-0000-4000-8000-000000000001', level: 'WARN', logger_name: 'synthetic', request_id: 'synthetic-request', message_summary: '<script>alert("synthetic")</script>', stack_trace: 'com.synthetic.Failure' }], hasMore: false }
    await route.fulfill({ json: { code: 200, message: 'success', data } })
  })
  await page.goto('/runtime-logs')
  await expect(page.getByRole('heading', { name: '运行日志' })).toBeVisible()
  await expect(page.getByText('日志存在缺口或无法确认完整性，请检查采集状态')).toBeVisible()
  await expect(page.getByRole('cell', { name: '<script>alert("synthetic")</script>' })).toBeVisible()
  expect(dialogs).toBe(0)
  expect(imports).toBe(0)
  await expect(page.locator('.runtime-log-page')).toHaveCSS('opacity', '1')
  await page.screenshot({ path: 'test-results/runtime-logs-light.png', fullPage: true })
  await page.emulateMedia({ reducedMotion: 'reduce', colorScheme: 'dark' })
  await page.evaluate(() => document.documentElement.classList.add('dark'))
  await expect.poll(async () => page.locator('.runtime-log-page > .el-card').first().evaluate(element => Math.max(...getComputedStyle(element).backgroundColor.match(/\d+/g)!.slice(0, 3).map(Number)))).toBeLessThan(65)
  await page.screenshot({ path: 'test-results/runtime-logs-dark-reduced.png', fullPage: true })
  await page.getByRole('button', { name: '详情', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '日志详情' }).locator('pre')).toHaveJSProperty('textContent', '<script>alert("synthetic")</script>\ncom.synthetic.Failure')
})

