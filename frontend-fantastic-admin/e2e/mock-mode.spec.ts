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

  test('页面标题可在页面同级与卡片风格之间切换', async ({ page }) => {
    test.setTimeout(90_000)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.addInitScript(() => {
      const storageKey = 'MRR-ADMIN:app-settings'
      if (!localStorage.getItem(storageKey)) {
        localStorage.setItem(storageKey, JSON.stringify({
          app: {
            enablePermission: false,
            pageTitleStyle: 'plain',
            radius: 0.75,
          },
        }))
      }
    })

    await page.goto('/records', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: '记录管理' })).toBeVisible({ timeout: 20_000 })
    await expect(page.locator('html')).toHaveAttribute('data-page-title-style', 'plain')

    const recordsHeader = page.locator('.page-header').first()
    const plainStyle = await recordsHeader.evaluate((node) => {
      const style = window.getComputedStyle(node)
      return {
        backgroundImage: style.backgroundImage,
        borderTopWidth: style.borderTopWidth,
        boxShadow: style.boxShadow,
      }
    })
    expect(plainStyle.backgroundImage).toBe('none')
    expect(plainStyle.borderTopWidth).toBe('0px')
    expect(plainStyle.boxShadow).toBe('none')

    const plainLineTops = await recordsHeader.locator('.eyebrow, h2, .subtitle').evaluateAll((nodes) => {
      return nodes.map(node => node.getBoundingClientRect().top)
    })
    expect(plainLineTops).toHaveLength(3)
    expect(plainLineTops[0]).toBeLessThan(plainLineTops[1])
    expect(plainLineTops[1]).toBeLessThan(plainLineTops[2])

    await page.goto('/statistics', { waitUntil: 'domcontentloaded' })
    const statisticsHero = page.locator('.hero-panel')
    await expect(statisticsHero).toBeVisible({ timeout: 20_000 })
    const plainHeroStyle = await statisticsHero.evaluate((node) => {
      const style = window.getComputedStyle(node)
      return {
        backgroundImage: style.backgroundImage,
        borderTopWidth: style.borderTopWidth,
        boxShadow: style.boxShadow,
      }
    })
    expect(plainHeroStyle.backgroundImage).toBe('none')
    expect(plainHeroStyle.borderTopWidth).toBe('0px')
    expect(plainHeroStyle.boxShadow).toBe('none')

    await page.evaluate(() => {
      const rawSettings = localStorage.getItem('MRR-ADMIN:app-settings')
      const savedSettings = rawSettings ? JSON.parse(rawSettings) : { app: {} }
      savedSettings.app = { ...savedSettings.app, pageTitleStyle: 'card' }
      localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify(savedSettings))
    })

    await page.goto('/records', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('html')).toHaveAttribute('data-page-title-style', 'card')
    await expect(page.getByRole('heading', { name: '记录管理' })).toBeVisible({ timeout: 20_000 })

    const cardHeader = page.locator('.page-header').first()
    const cardStyle = await cardHeader.evaluate((node) => {
      const style = window.getComputedStyle(node)
      return {
        backgroundImage: style.backgroundImage,
        borderRadius: Number.parseFloat(style.borderRadius),
        boxShadow: style.boxShadow,
      }
    })
    expect(cardStyle.backgroundImage).not.toBe('none')
    expect(cardStyle.borderRadius).toBeGreaterThan(0)
    expect(cardStyle.boxShadow).not.toBe('none')

    const cardLineTops = await cardHeader.locator('.eyebrow, h2, .subtitle').evaluateAll((nodes) => {
      return nodes.map(node => node.getBoundingClientRect().top)
    })
    expect(cardLineTops).toHaveLength(3)
    expect(cardLineTops[0]).toBeLessThan(cardLineTops[1])
    expect(cardLineTops[1]).toBeLessThan(cardLineTops[2])
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
