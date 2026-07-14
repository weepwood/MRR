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
    test.setTimeout(60_000)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.addInitScript(() => {
      localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: {
          enablePermission: false,
          radius: 0.75,
        },
      }))
    })
    await page.goto('/archive', { waitUntil: 'domcontentloaded' })

    await page.waitForFunction(
      () => document.body.classList.contains('archive-immersive'),
      undefined,
      { timeout: 20_000 },
    )

    await expect(page.getByRole('heading', { name: '影像档案袋' })).toBeVisible({ timeout: 20_000 })

    const archivePage = page.locator('.archive-page')
    await expect(archivePage).toBeVisible()
    await expect.poll(async () => {
      return (await archivePage.boundingBox())?.height ?? 0
    }, { timeout: 10_000 }).toBeGreaterThan(300)

    const searchCard = page.locator('.search-card')
    await expect(searchCard).toBeVisible()
    const borderRadius = await searchCard.evaluate((element) => {
      return Number.parseFloat(window.getComputedStyle(element).borderRadius)
    })
    expect(borderRadius).toBeGreaterThan(0)
  })

  test('主要业务页面卡片跟随统一圆角设置', async ({ page }) => {
    test.setTimeout(90_000)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.addInitScript(() => {
      localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: {
          enablePermission: false,
          radius: 1,
        },
      }))
    })

    const surfaces = [
      { path: '/', selector: '.stat-card' },
      { path: '/statistics', selector: '.hero-panel' },
      { path: '/monitoring', selector: '.monitor-card' },
      { path: '/settings', selector: '.setting-section' },
      { path: '/response-analysis', selector: '.analysis-card' },
    ]

    for (const surface of surfaces) {
      await page.goto(surface.path, { waitUntil: 'domcontentloaded' })
      const element = page.locator(surface.selector).first()
      await expect(element).toBeVisible({ timeout: 20_000 })
      const radius = await element.evaluate((node) => {
        return Number.parseFloat(window.getComputedStyle(node).borderRadius)
      })
      expect(radius).toBe(16)
    }
  })
})
