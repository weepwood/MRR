import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { colorScheme: 'light', pageTitleStyle: 'card', enableMotion: false },
    menu: { mode: 'single' },
    layout: { enableMobileAdaptation: true },
  })))
})

test('长设置页面目录固定，分区标题自然滚动且不遮挡内容，键盘切换返回新分区', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/settings?section=archive')
  const header = page.locator('.settings-panel-header')
  const sidebar = page.locator('.settings-sidebar')
  await expect(page.getByRole('button', { name: '保存设置', exact: true })).toBeDisabled()
  await expect(page.locator('.settings-content')).toHaveAttribute('aria-busy', 'false')
  await expect(page.locator('.settings-content .el-form')).toBeVisible()
  await expect(header).toHaveCSS('position', 'static')
  await expect(sidebar).toHaveCSS('position', 'sticky')
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollHeight - document.documentElement.clientHeight)).toBeGreaterThan(100)
  const initialTop = (await header.boundingBox())!.y
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(100)
  await expect.poll(async () => {
    const top = (await header.boundingBox())!.y
    const scroll = await page.evaluate(() => window.scrollY)
    return Math.abs(initialTop - top - scroll)
  }).toBeLessThanOrEqual(1)
  await expect.poll(() => header.evaluate((el) => {
    const content = document.querySelector('.settings-content')!
    return el.getBoundingClientRect().bottom <= content.getBoundingClientRect().top
  })).toBe(true)
  await expect(sidebar.getByRole('button', { name: '访问安全', exact: true })).toBeInViewport()
  const archive = sidebar.getByRole('button', { name: '档案浏览', exact: true })
  await archive.focus()
  await archive.press('Home')
  const system = sidebar.getByRole('button', { name: '系统信息', exact: true })
  await expect(system).toBeFocused()
  await system.press('Enter')
  await expect(page.getByRole('heading', { name: '系统信息', exact: true })).toBeVisible()
  await expect(page.getByRole('textbox', { name: '系统名称', exact: true })).toBeInViewport()
})

test('跨分类保留服务器草稿，窄屏不固定头部，返回后可撤回', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/settings?section=system')
  const input = page.getByRole('textbox', { name: '系统名称', exact: true })
  await expect(input).toBeVisible()
  const original = await input.inputValue()
  await input.fill('合成设置草稿')
  await page.locator('.settings-nav-item').filter({ hasText: '界面外观' }).click()
  await expect(page.locator('.settings-pending')).toContainText('另有 1 项系统配置尚未保存')
  await page.getByRole('button', { name: '返回系统信息' }).click()
  await expect(input).toHaveValue('合成设置草稿')
  await page.setViewportSize({ width: 375, height: 812 })
  await expect(page.getByRole('combobox', { name: '设置分类' })).toBeVisible()
  await expect(page.locator('.settings-panel-header')).toHaveCSS('position', 'static')
  await expect.poll(() => page.locator('.settings-layout').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await input.fill(original)
  await expect(page.getByRole('button', { name: '保存设置', exact: true })).toBeDisabled()
})
