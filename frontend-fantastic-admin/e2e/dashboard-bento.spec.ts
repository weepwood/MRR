import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const [palette, theme] of [['mrr', 'light'], ['mrr', 'dark'], ['gr8r', 'light'], ['gr8r', 'dark']] as const) {
  test(`${palette}/${theme} 工作台标准导航卡片与布局`, async ({ page }, info) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { themePalette: palette, colorScheme: theme, enableMotion: false },
      menu: { mode: 'single' },
      layout: { enableMobileAdaptation: true },
    })), { palette, theme })
    const requests = { stats: 0, health: 0, scan: 0 }
    page.on('request', (req) => {
      const path = new URL(req.url()).pathname
      if (path.endsWith('/api/v1/statistics/summary')) { requests.stats++ }
      if (path.endsWith('/api/v1/system/health')) { requests.health++ }
      if (path.includes('/api/v1/scan/')) { requests.scan++ }
    })
    await page.goto('/')
    const archive = page.locator('[data-widget-id="archive"]')
    const stats = page.locator('[data-widget-id="statistics"]')
    const health = page.locator('[data-widget-id="monitoring"]')
    await expect(archive.getByRole('textbox', { name: '病案号', exact: true })).toBeVisible()
    await expect(health).toHaveAttribute('role', 'link')
    await expect(stats).toHaveAttribute('role', 'link')
    await expect(page.locator('.dashboard-data')).toHaveCount(0)
    expect(requests.health).toBe(0)
    expect(requests.stats).toBe(0)
    expect(requests.scan).toBe(0)
    for (const width of [1920, 1440, 1024, 760, 375]) {
      await page.setViewportSize({ width, height: 900 })
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width + 1)
      await page.screenshot({ path: info.outputPath(`dashboard-${width}.png`), fullPage: true })
    }
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.locator('.dashboard-header').hover()
    await page.getByRole('button', { name: '管理模块' }).click()
    const manager = page.getByRole('dialog', { name: '管理小组件' })
    const row = manager.locator('.widget-manager-item').filter({ hasText: '病案扫描统计' })
    await row.getByRole('button', { name: '编辑' }).click()
    const editor = page.getByRole('dialog', { name: '编辑小组件' })
    await editor.getByText('大', { exact: true }).click()
    await editor.getByText('模块说明', { exact: true }).click()
    await editor.getByRole('button', { name: '保存', exact: true }).click()
    await expect(stats).toHaveClass(/dashboard-widget--large/)
    await expect(stats.locator('.dashboard-widget__content')).toHaveCount(0)
    await expect(stats.locator('.dashboard-widget__description')).toHaveCount(0)
    await manager.getByRole('button', { name: '完成', exact: true }).click()
    await page.screenshot({ path: info.outputPath('large-navigation.png'), fullPage: true })
    expect(requests.stats).toBe(0)
    expect(requests.health).toBe(0)
  })
}

test('受限会话不加载不可访问的统计和系统数据', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { code: 200, data: { id: 20, username: 'synthetic-reader', roleCode: 'OPERATOR', status: 'active', permissions: ['record:read'], mustChangePassword: false } } }))
  const requests: string[] = []
  page.on('request', req => requests.push(new URL(req.url()).pathname.replace(/^\/proxy/, '')))
  await page.goto('/')
  await expect(page.locator('[data-widget-id="archive"] .home-lookup')).toBeVisible()
  await expect(page.locator('[data-widget-id="monitoring"], [data-widget-id="statistics"]')).toHaveCount(0)
  expect(requests).not.toContain('/api/v1/statistics/summary')
  expect(requests).not.toContain('/api/v1/system/health')
})

test('可选统计明细摘要、编辑持久化与过期失败状态', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { enableMotion: false }, layout: { enableMobileAdaptation: true } })))
  let reads = 0
  let fail = false
  await page.route('**/api/v1/statistics/summary', async (route) => {
    reads++
    await route.fulfill({ json: fail ? { code: 500, message: '测试失败' } : { code: 200, data: { total: { totalRecords: 0, totalPages: 12 }, uniqueBAHCount: 3, byType: [{ type: '合成分类', recordCount: 0 }] } } })
  })
  await page.clock.install()
  await page.goto('/')
  const stats = page.locator('[data-widget-id="statistics"]')
  const detail = page.locator('[data-widget-id="statistics-detail"]')
  await page.locator('.dashboard-header').hover()
  await page.getByRole('button', { name: '管理模块' }).click()
  const manager = page.getByRole('dialog', { name: '管理小组件' })
  await manager.locator('.widget-manager-item').filter({ hasText: '统计明细' }).getByRole('button', { name: '编辑' }).click()
  const editor = page.getByRole('dialog', { name: '编辑小组件' })
  await editor.getByText('统计总页数', { exact: true }).click()
  await editor.getByRole('button', { name: '保存', exact: true }).click()
  await manager.getByRole('button', { name: '完成', exact: true }).click()
  await detail.getByRole('button', { name: '读取', exact: true }).click()
  await expect(detail.getByText('12', { exact: true })).toBeVisible()
  await expect(stats.locator('.dashboard-widget__content')).toHaveCount(0)
  expect(reads).toBe(1)
  await expect(detail.getByRole('button', { name: '刷新', exact: true })).toBeDisabled()
  await page.clock.fastForward(11 * 60_000)
  await expect(detail.getByText('已过期 · 上次快照')).toBeVisible()
  fail = true
  await detail.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(detail.getByText('刷新失败 · 上次快照')).toBeVisible()
  await expect(detail.getByText('12', { exact: true })).toBeVisible()
  await page.reload()
  await expect(detail.locator('.dashboard-data')).toBeVisible()
  await expect(detail.getByText('按需读取统计摘要')).toBeVisible()
  await expect(detail.getByText('12', { exact: true })).toHaveCount(0)
})

test('查询历史可连续反向与键盘退出，查询卡支持原有键盘排序', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { enableMotion: true }, layout: { enableMobileAdaptation: true } })))
  await page.route('**/api/v1/archive-search-history**', route => route.fulfill({ json: { code: 200, data: [{ id: 1, bah: '00094001', sjh: '', success: true, imageCount: 2, queryCount: 1, favorite: false, searchedAt: new Date().toISOString() }] } }))
  await page.goto('/')
  const archive = page.locator('[data-widget-id="archive"]')
  const trigger = archive.getByRole('button', { name: '最近查询' })
  const panel = page.getByRole('dialog', { name: '最近查询' })
  const geometry = () => page.locator('.dashboard-grid > article').evaluateAll(els => els.map((el) => { const card = el as HTMLElement; return [card.offsetLeft, card.offsetTop, card.offsetWidth, card.offsetHeight] }))
  await expect(page.locator('[data-widget-id="monitoring"]')).toHaveAttribute('role', 'link')
  // The former health request implicitly waited for initial page-entry motion.
  // Measure only after the grid and its ancestors finish that motion.
  await expect.poll(() => page.locator('.dashboard-grid').evaluate((el) => {
    for (let node: Element | null = el; node; node = node.parentElement) {
      if (node.getAnimations().some(animation => animation.pending || animation.playState === 'running')) { return false }
    }
    return true
  })).toBe(true)
  const before = await geometry()
  await trigger.click()
  await expect(panel.locator('.recent-lookups__item')).toHaveCount(1)
  expect(await geometry()).toEqual(before)
  await expect(panel.locator('.recent-lookups__item').first()).toBeVisible()
  await trigger.evaluate(async (node) => {
    for (let i = 0; i < 10; i++) {
      ;(node as HTMLElement).click()
      await new Promise(resolve => setTimeout(resolve, 37))
    }
  })
  await expect(trigger).toHaveAttribute('aria-expanded', 'true')
  await panel.locator('.recent-lookups__item').first().focus()
  await page.keyboard.press('Escape')
  await expect(trigger).toBeFocused()
  await expect(trigger).toHaveAttribute('aria-expanded', 'false')
  await expect(panel).toBeHidden()
  expect(await geometry()).toEqual(before)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await trigger.click()
  await expect(panel).toBeVisible()
  expect(await geometry()).toEqual(before)
  await trigger.click()
  await expect(panel).toBeHidden()
  await trigger.click()
  await expect(panel).toBeVisible()
  await page.locator('.dashboard-header h1').click()
  await expect(panel).toBeHidden()
  expect(await geometry()).toEqual(before)
  await page.locator('.dashboard-header').hover()
  await page.getByRole('button', { name: '调整布局' }).click()
  await expect(archive.getByRole('textbox', { name: '病案号', exact: true })).toBeDisabled()
  await archive.focus()
  await expect(archive).toBeFocused()
  // The original pin-group boundary remains intact.
  await archive.press('Alt+ArrowRight')
  await expect(page.locator('.dashboard-grid > article').first()).toHaveAttribute('data-widget-id', 'archive')
  await page.getByRole('button', { name: '完成调整' }).click()
  await expect(archive.getByRole('textbox', { name: '病案号', exact: true })).toBeEnabled()
})

test('窄屏最近查询浮层不改变卡片布局', async ({ page }, info) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { enableMotion: false }, layout: { enableMobileAdaptation: true } })))
  await page.setViewportSize({ width: 375, height: 900 })
  await page.goto('/')
  await expect(page.locator('[data-widget-id="monitoring"]')).toHaveAttribute('role', 'link')
  const archive = page.locator('[data-widget-id="archive"]')
  const trigger = archive.getByRole('button', { name: '最近查询' })
  const geometry = () => page.locator('.dashboard-grid > article').evaluateAll(els => els.map((el) => { const card = el as HTMLElement; return [card.offsetLeft, card.offsetTop, card.offsetWidth, card.offsetHeight] }))
  const before = await geometry()
  await trigger.click()
  const panel = page.getByRole('dialog', { name: '最近查询' })
  await expect(panel).toBeVisible()
  const bounds = (await panel.boundingBox())!
  expect(bounds.x).toBeGreaterThanOrEqual(0)
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(375)
  expect(await geometry()).toEqual(before)
  await page.screenshot({ path: info.outputPath('mobile-history.png') })
  await page.keyboard.press('Escape')
  await expect(panel).toBeHidden()
  await expect(trigger).toBeFocused()
  expect(await geometry()).toEqual(before)
})
