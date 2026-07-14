import { expect, test } from '@playwright/test'

test.describe('无后端 Mock 模式', () => {
  test('提供统计、病案和响应分析数据', async ({ request }) => {
    const summaryResponse = await request.get('/proxy/api/v1/statistics/summary')
    expect(summaryResponse.ok()).toBe(true)
    const summary = await summaryResponse.json()
    expect(summary.code).toBe(200)
    expect(summary.data.total.totalRecords).toBeGreaterThan(0)
    expect(summary.data.uniqueBAHCount).toBeGreaterThan(0)

    const recordsResponse = await request.get('/proxy/api/v1/scan/page?page=1&size=10')
    expect(recordsResponse.ok()).toBe(true)
    const records = await recordsResponse.json()
    expect(records.code).toBe(200)
    expect(records.data.list).toHaveLength(10)
    expect(records.data.total).toBeGreaterThan(10)

    const analysisResponse = await request.get('/proxy/api/v1/response-metrics/analysis?days=7')
    expect(analysisResponse.ok()).toBe(true)
    const analysis = await analysisResponse.json()
    expect(analysis.code).toBe(200)
    expect(analysis.data.trend).toHaveLength(7)
    expect(analysis.data.slowEndpoints.length).toBeGreaterThan(0)
  })

  test('支持条件查询和内存写入', async ({ request }) => {
    const filteredResponse = await request.post('/proxy/api/v1/scan/page/condition?page=1&size=20', {
      data: { btype: 1 },
    })
    expect(filteredResponse.ok()).toBe(true)
    const filtered = await filteredResponse.json()
    expect(filtered.code).toBe(200)
    expect(filtered.data.list.length).toBeGreaterThan(0)
    expect(filtered.data.list.every((item: { btype: number }) => item.btype === 1)).toBe(true)

    const settingsResponse = await request.put('/proxy/api/v1/settings/mock.test', {
      data: { value: 'enabled' },
    })
    expect(settingsResponse.ok()).toBe(true)

    const settingResponse = await request.get('/proxy/api/v1/settings/mock.test')
    expect(settingResponse.ok()).toBe(true)
    const setting = await settingResponse.json()
    expect(setting.data).toBe('enabled')
  })

  test('影像档案袋保持可见并应用全局圆角', async ({ page }) => {
    await page.goto('/archive')

    await expect(page.getByRole('heading', { name: '影像档案袋' })).toBeVisible()

    const archivePage = page.locator('.archive-page')
    await expect(archivePage).toBeVisible()
    const archiveBox = await archivePage.boundingBox()
    expect(archiveBox?.height ?? 0).toBeGreaterThan(300)

    const searchCard = page.locator('.search-card')
    await expect(searchCard).toBeVisible()
    const borderRadius = await searchCard.evaluate((element) => {
      return Number.parseFloat(window.getComputedStyle(element).borderRadius)
    })
    expect(borderRadius).toBeGreaterThan(0)
  })
})
