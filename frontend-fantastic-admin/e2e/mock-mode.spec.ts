import { expect, test } from '@playwright/test'

async function readRadius(locator: import('@playwright/test').Locator) {
  return locator.evaluate(element => Number.parseFloat(window.getComputedStyle(element).borderRadius))
}

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
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.addInitScript(() => {
      localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { enablePermission: false, radius: 0.75 },
      }))
    })
    await page.goto('/archive', { waitUntil: 'domcontentloaded' })

    await page.waitForFunction(
      () => document.body.classList.contains('archive-immersive'),
      undefined,
      { timeout: 20_000 },
    )
    await expect(page.getByRole('heading', { name: '住院病案' })).toBeVisible({ timeout: 20_000 })

    const archivePage = page.locator('.archive-page')
    await expect(archivePage).toBeVisible()
    await expect.poll(async () => (await archivePage.boundingBox())?.height ?? 0).toBeGreaterThan(300)

    const searchCard = page.locator('.search-card')
    await expect(searchCard).toBeVisible()
    expect(await readRadius(searchCard)).toBeGreaterThan(0)
  })

  test('页面标题可在页面同级与卡片风格之间切换', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.addInitScript(() => {
      const storageKey = 'MRR-ADMIN:app-settings'
      if (!localStorage.getItem(storageKey)) {
        localStorage.setItem(storageKey, JSON.stringify({
          app: { enablePermission: false, pageTitleStyle: 'plain', radius: 0.75 },
        }))
      }
    })

    await page.goto('/records', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: '记录管理' })).toBeVisible({ timeout: 20_000 })
    await expect(page.locator('html')).toHaveAttribute('data-page-title-style', 'plain')

    // 记录管理已迁到共享组件 MrrPageHeader（`.mrr-page-header`），
    // page-header.css 对 `.page-header` 与 `.mrr-page-header` 是并行选择器，样式口径相同。
    const header = page.locator('.mrr-page-header').first()
    await expect(header).toBeVisible()
    const plainStyle = await header.evaluate((node) => {
      const style = window.getComputedStyle(node)
      return { backgroundImage: style.backgroundImage, backgroundColor: style.backgroundColor, borderRadius: style.borderRadius, boxShadow: style.boxShadow, borderWidth: style.borderTopWidth }
    })
    expect(plainStyle.backgroundImage).toBe('none')
    expect(Number.parseFloat(plainStyle.borderRadius) || 0).toBe(0)
    expect(plainStyle.boxShadow).toBe('none')
    expect(plainStyle.borderWidth).toBe('0px')

    await page.evaluate(() => {
      const storageKey = 'MRR-ADMIN:app-settings'
      const savedSettings = JSON.parse(localStorage.getItem(storageKey) || '{"app":{}}')
      savedSettings.app = { ...savedSettings.app, pageTitleStyle: 'card' }
      localStorage.setItem(storageKey, JSON.stringify(savedSettings))
    })
    await page.reload({ waitUntil: 'domcontentloaded' })

    await expect(page.locator('html')).toHaveAttribute('data-page-title-style', 'card')
    await expect(page.getByRole('heading', { name: '记录管理' })).toBeVisible({ timeout: 20_000 })
    const cardStyle = await page.locator('.mrr-page-header').first().evaluate((node) => {
      const style = window.getComputedStyle(node)
      return { backgroundImage: style.backgroundImage, backgroundColor: style.backgroundColor, borderRadius: Number.parseFloat(style.borderRadius), boxShadow: style.boxShadow, borderWidth: style.borderTopWidth, borderStyle: style.borderTopStyle }
    })
    expect(cardStyle.backgroundImage).toBe('none')
    expect(cardStyle.backgroundColor).not.toBe(plainStyle.backgroundColor)
    expect(cardStyle.backgroundColor).not.toBe('rgba(0, 0, 0, 0)')
    expect(cardStyle.borderWidth).toBe('1px')
    expect(cardStyle.borderStyle).toBe('solid')
    expect(cardStyle.borderRadius).toBeGreaterThan(0)
    expect(cardStyle.boxShadow).toBe('none')
  })

  test('主要页面卡片跟随统一圆角设置', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.addInitScript(() => {
      localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { enablePermission: false, radius: 1 },
      }))
    })

    // 必须是真正带背景/边框的卡片表面。settings 页的 .setting-section 只是
    // 布局网格（无背景无边框），圆角不可见，卡片是它内部的 .setting-group。
    const surfaces = [
      { path: '/', selector: '.dashboard-widget' },
      { path: '/archive', selector: '.search-card' },
      { path: '/settings', selector: '.setting-group' },
    ]

    for (const surface of surfaces) {
      await page.goto(surface.path, { waitUntil: 'domcontentloaded' })
      const element = page.locator(surface.selector).first()
      await expect(element).toBeVisible({ timeout: 20_000 })
      expect(await readRadius(element)).toBeGreaterThan(0)
    }
  })
})
