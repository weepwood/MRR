import { expect, test } from '@playwright/test'

test('OSS标签按需执行有限只读检查并记录摘要', async ({ page }) => {
  const requests: string[] = []
  await page.route(/\/api\/v1\/oss\//, async (route) => {
    requests.push(route.request().url())
    expect(route.request().method()).toBe('GET')
    const path = new URL(route.request().url()).pathname
    const data = path.endsWith('/browser')
      ? { configured: true, loadedDirectories: 2, loadedFiles: 3, truncated: true, endpoint: 'sensitive.invalid', entries: [{ name: 'private-patient.png' }] }
      : path.endsWith('/readiness')
        ? { ready: false, ossConfigured: true, sourcePathReadable: false, sampleSize: 10, sampleReadableCount: 0, warnings: ['private-path'] }
        : path.endsWith('/statistics')
          ? { totalCount: 8, migratedCount: 3, failedCount: 1 }
          : { list: [{ status: 'completed', createdBy: 'private-operator', scopeValue: 'private-record' }] }
    await route.fulfill({ json: { code: 200, data } })
  })
  await page.goto('/auth-test')
  await page.getByRole('tab', { name: 'OSS 检查', exact: true }).click()
  expect(requests).toHaveLength(0)
  await page.getByRole('button', { name: '运行全部 OSS 检查' }).click()
  await expect(page.getByTestId('oss-check-browser')).toContainText('通过')
  await expect(page.getByTestId('oss-check-readiness')).toContainText('需关注')
  await expect(page.getByTestId('oss-check-jobs')).toContainText('通过')
  expect(requests).toHaveLength(4)
  expect(requests.some(url => url.includes('maxKeys=20'))).toBe(true)
  expect(requests.some(url => url.includes('sampleSize=10'))).toBe(true)
  expect(requests.some(url => url.includes('size=5'))).toBe(true)
  for (const secret of ['sensitive.invalid', 'private-patient', 'private-path', 'private-operator', 'private-record']) {
    await expect(page.locator('body')).not.toContainText(secret)
  }
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('tab', { name: '请求记录', exact: true }).click()
  await expect(page.locator('.el-table__body-wrapper')).toContainText('OSS 目录可读性')
  await expect(page.locator('.el-table__body-wrapper')).toContainText('需关注')
})

test('切换标签页使在途结果失效，重新进入仍为未执行', async ({ page }) => {
  let release!: () => void
  let started = false
  await page.route(/\/api\/v1\/oss\/browser(?:\?|$)/, async (route) => {
    started = true
    await new Promise<void>(resolve => release = resolve)
    await route.fulfill({ json: { code: 200, data: { configured: false } } })
  })
  await page.goto('/auth-test')
  await page.getByRole('tab', { name: 'OSS 检查', exact: true }).click()
  await page.getByTestId('oss-check-browser').getByRole('button', { name: '执行检查' }).click()
  await expect.poll(() => started).toBe(true)
  await page.getByRole('tab', { name: '请求记录', exact: true }).click()
  const finished = page.waitForEvent('requestfinished', request => request.url().includes('/oss/browser'))
  release()
  await finished
  await page.evaluate(() => new Promise(requestAnimationFrame))
  await expect(page.locator('.el-table__body-wrapper')).not.toContainText('OSS 目录可读性')
  await page.getByRole('tab', { name: 'OSS 检查', exact: true }).click()
  await expect(page.getByTestId('oss-check-browser')).toContainText('尚未执行')
})

test('明暗主题和窄屏可阅读，未配置提示可清空', async ({ page }) => {
  await page.addInitScript(() => {
    const key = 'MRR-ADMIN:app-settings'
    const settings = JSON.parse(localStorage.getItem(key) || '{}')
    localStorage.setItem(key, JSON.stringify({ ...settings, layout: { ...settings.layout, enableMobileAdaptation: true } }))
  })
  await page.route(/\/api\/v1\/oss\/browser(?:\?|$)/, route => route.fulfill({ json: { code: 200, data: { configured: false } } }))
  await page.goto('/auth-test')
  await page.getByRole('tab', { name: 'OSS 检查', exact: true }).click()
  const row = page.getByTestId('oss-check-browser')
  await row.getByRole('button', { name: '执行检查' }).click()
  await expect(row).toContainText('OSS 未配置')
  for (const dark of [false, true]) {
    await page.evaluate(dark => document.documentElement.classList.toggle('dark', dark), dark)
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 1000 })
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      await expect(row.getByRole('button', { name: '执行检查' })).toBeVisible()
      await page.locator('.oss-check-panel').screenshot({ path: test.info().outputPath(`oss-${dark ? 'dark' : 'light'}-${width}.png`) })
    }
  }
  await page.getByRole('button', { name: '清空检查结果' }).click()
  await expect(row).toContainText('尚未执行')
})

test('权限拒绝、业务失败和网络失败不误报通过', async ({ page }) => {
  let attempt = 0
  await page.route(/\/api\/v1\/oss\/browser(?:\?|$)/, async (route) => {
    attempt++
    if (attempt === 1) { await route.fulfill({ status: 403, json: { message: 'private-denial' } }) }
    else if (attempt === 2) { await route.fulfill({ json: { code: 500, message: 'private-business-failure' } }) }
    else { await route.abort('failed') }
  })
  await page.goto('/auth-test')
  await page.getByRole('tab', { name: 'OSS 检查', exact: true }).click()
  const row = page.getByTestId('oss-check-browser')
  await row.getByRole('button', { name: '执行检查' }).click()
  await expect(row).toContainText('无权读取')
  await expect(page).toHaveURL(/auth-test/)
  await row.getByRole('button', { name: '执行检查' }).click()
  await expect(row).toContainText('业务接口未成功')
  await row.getByRole('button', { name: '执行检查' }).click()
  await expect(row).toContainText('网络请求未完成')
  await expect(page.locator('body')).not.toContainText('private-denial')
  await expect(page.locator('body')).not.toContainText('private-business-failure')
})
