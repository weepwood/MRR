import { expect, test } from '@playwright/test'

test('完整性报告与异常分页独立，筛选已加载数据且窄屏布局不溢出', async ({ page }) => {
  const base = '/api/v1/operations/archive-manifests'
  const latest = { version: 1001, archiveId: 1, status: 'COMPLETED', snapshotCount: 150, processed: 150, verified: 49, anomalies: 101, completeDigestAvailable: false, createdAt: '2026-11-25T00:00:00Z', updatedAt: '2026-11-25T00:00:00Z' }
  let fail = true
  let latestRequests = 0
  await page.route(`**${base}?*`, (route) => {
    latestRequests++
    return route.fulfill({ json: { code: 200, data: { enabled: false, latest } } })
  })
  await page.route(`**${base}/1001/report?*`, route => route.fulfill({ json: { code: 200, data: { ...latest, segments: [], anomalyCounts: { TIMEOUT: 100, MISSING_OR_UNREADABLE: 1 } } } }))
  await page.route(`**${base}/1001/anomalies?*`, (route) => {
    const after = Number(new URL(route.request().url()).searchParams.get('after') ?? 0)
    if (after && fail) {
      return route.fulfill({ status: 400, json: { code: 400, message: 'synthetic pagination failure' } })
    }
    const row = (cursor: number) => ({ cursor, scanId: 1000 + cursor, status: cursor === 101 ? 'MISSING_OR_UNREADABLE' : 'TIMEOUT', bytesRead: 0, digestAvailable: false, checkedAt: latest.updatedAt })
    return route.fulfill({ json: { code: 200, data: after ? [row(101)] : Array.from({ length: 100 }, (_, i) => row(i + 1)) } })
  })
  await page.goto('/archive-workbench')
  const search = page.getByRole('textbox', { name: '病案查询值' })
  await search.fill('26070000')
  await search.press('Enter')
  const report = page.getByRole('region', { name: '完整性报告', exact: true })
  const anomalies = page.getByRole('region', { name: '已加载异常', exact: true })
  await report.getByRole('button', { name: '查看完整性报告' }).click()
  await expect(report.getByRole('button', { name: '下载完整性报告 JSON' })).toBeVisible()
  await expect(anomalies.getByText('已加载 100 / 101')).toBeVisible()
  const initialLatestRequests = latestRequests
  await anomalies.getByRole('button', { name: '加载更多异常', exact: true }).click()
  await expect(anomalies.getByText('更多异常读取失败，已加载结果保留。')).toBeVisible()
  await expect(report.getByRole('button', { name: '下载完整性报告 JSON' })).toBeVisible()
  fail = false
  await anomalies.getByRole('button', { name: '重试加载更多' }).click()
  await expect(anomalies.getByText('已加载 101 / 101')).toBeVisible()
  expect(latestRequests).toBe(initialLatestRequests)
  await anomalies.locator('.manifest-base-select .el-select__wrapper').click()
  await page.getByRole('option', { name: 'MISSING_OR_UNREADABLE', exact: true }).click()
  await expect(anomalies.getByText('仅筛选已加载的 101 条异常，当前 1 条；不是全量筛选。')).toBeVisible()
  await expect(anomalies.getByRole('row').filter({ hasText: '1101' })).toBeVisible()
  await expect(anomalies.getByRole('row').filter({ hasText: '1001' })).toHaveCount(0)
  await page.screenshot({ path: test.info().outputPath('manifest-layout-desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect.poll(() => page.locator('.archive-stack--workspace').evaluate(el => el.scrollWidth <= el.clientWidth + 2)).toBe(true)
  await expect(report.getByRole('button', { name: '下载完整性报告 JSON' })).toBeVisible()
  await page.screenshot({ path: test.info().outputPath('manifest-layout-mobile.png'), fullPage: true })
})
