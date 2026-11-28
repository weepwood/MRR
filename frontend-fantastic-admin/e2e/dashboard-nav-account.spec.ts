import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const [palette, theme] of [['mrr', 'light'], ['mrr', 'dark'], ['gr8r', 'light'], ['gr8r', 'dark']] as const) {
  test(`${palette}/${theme} 账户入口、等高导航卡片与原有菜单`, async ({ page }, info) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { themePalette: palette, colorScheme: theme, enableMotion: false },
      menu: { mode: 'single', enableSubMenuCollapseButton: true },
      layout: { enableMobileAdaptation: true },
    })), { palette, theme })
    const requested: string[] = []
    page.on('request', req => requested.push(new URL(req.url()).pathname))
    await page.goto('/')
    const card = (id: string) => page.locator(`[data-widget-id="${id}"]`)
    await expect(card('archive').locator('.home-lookup')).toBeVisible()
    const account = page.locator('.account-area .account-trigger')
    await expect(account).toHaveAttribute('data-variant', 'sidebar')
    await expect(account).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')
    const geometry = await account.evaluate((el) => {
      const avatar = el.querySelector('.mrr-account-avatar')!.getBoundingClientRect()
      const rect = el.getBoundingClientRect()
      const parent = el.closest('.account-area')!.getBoundingClientRect()
      return { height: rect.height, avatar: avatar.width, left: rect.left - parent.left, right: parent.right - rect.right }
    })
    expect(geometry.height).toBe(40)
    expect(geometry.avatar).toBe(28)
    expect(geometry.left).toBeLessThanOrEqual(12)
    expect(geometry.right).toBeLessThanOrEqual(12)
    for (const width of [1920, 1440, 1024, 760, 375]) {
      await page.setViewportSize({ width, height: 900 })
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width + 1)
      for (const id of ['statistics', 'monitoring']) {
        await expect(card(id)).toHaveAttribute('role', 'link')
        await expect(card(id).locator('.dashboard-widget__content')).toHaveCount(0)
      }
      if (width >= 1024) {
        const rows = await page.locator('.dashboard-grid').evaluate(el => getComputedStyle(el).gridTemplateRows.split(' ').map(Number.parseFloat))
        expect(Math.max(...rows) - Math.min(...rows)).toBeLessThanOrEqual(1)
        const heights = await page.locator('.dashboard-widget:not(.is-action)').evaluateAll(els => els.map(el => (el as HTMLElement).offsetHeight))
        expect(Math.max(...heights) - Math.min(...heights)).toBeLessThanOrEqual(1)
      }
      await page.screenshot({ path: info.outputPath(`home-${width}.png`), fullPage: true })
    }
    expect(requested.some(p => /\/api\/v1\/(?:statistics\/summary|system\/health)$/.test(p))).toBe(false)
    await page.setViewportSize({ width: 1440, height: 900 })
    await account.focus()
    await account.press('Enter')
    const menu = page.locator('.mrr-account-menu:visible')
    await expect(menu.getByRole('menuitem', { name: '个人设置' })).toBeVisible()
    await page.screenshot({ path: info.outputPath('account-menu.png') })
    await page.keyboard.press('Escape')
    await expect(menu).toBeHidden()
    await expect(account).toBeFocused()
    await card('statistics').focus()
    await card('statistics').press('Enter')
    await expect(page).toHaveURL(/\/statistics$/)
    await page.goto('/')
    await card('monitoring').click()
    await expect(page).toHaveURL(/\/monitoring$/)
  })
}

test('旧指标配置不再撑高两张导航卡，其他偏好保留', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.goto('/')
  await page.locator('.dashboard-header').hover()
  await page.getByRole('button', { name: '管理模块' }).click()
  const manager = page.getByRole('dialog', { name: '管理小组件' })
  await manager.locator('.widget-manager-item').filter({ hasText: '病案扫描统计' }).getByRole('button', { name: '编辑' }).click()
  await page.getByRole('dialog', { name: '编辑小组件' }).getByRole('button', { name: '保存', exact: true }).click()
  await manager.getByRole('button', { name: '完成', exact: true }).click()
  await page.evaluate(() => {
    const key = Object.keys(localStorage).find(k => k.startsWith('MRR-ADMIN:dashboard-widgets:') && !k.endsWith(':appearance'))!
    const items = JSON.parse(localStorage.getItem(key)!)
    for (const item of items) {
      if (item.id === 'statistics') { Object.assign(item, { title: '自定义扫描统计', metricKeys: ['records'], color: 'pink' }) }
      if (item.id === 'monitoring') { Object.assign(item, { metricKeys: ['database'] }) }
    }
    localStorage.setItem(key, JSON.stringify(items))
  })
  await page.reload()
  const stats = page.locator('[data-widget-id="statistics"]')
  await expect(stats).toContainText('自定义扫描统计')
  await expect(stats).toHaveAttribute('role', 'link')
  await expect(stats.locator('.dashboard-widget__content')).toHaveCount(0)
  await expect(page.locator('[data-widget-id="monitoring"] .dashboard-widget__content')).toHaveCount(0)
})
