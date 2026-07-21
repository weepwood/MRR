import { expect, test } from '@playwright/test'

test.describe('页面标题统一风格', () => {
  test('风格一字号对齐装箱管理并覆盖设置、帮助和用户页面', async ({ page }) => {
    test.setTimeout(120_000)
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

    await page.goto('/archive-boxes', { waitUntil: 'domcontentloaded' })
    const archiveTitle = page.getByRole('heading', { name: '档案装箱管理' })
    await expect(archiveTitle).toBeVisible({ timeout: 20_000 })
    const archiveTitleSize = await archiveTitle.evaluate(node => window.getComputedStyle(node).fontSize)
    expect(archiveTitleSize).toBe('28px')

    await page.goto('/records', { waitUntil: 'domcontentloaded' })
    const recordsTitle = page.getByRole('heading', { name: '记录管理' })
    await expect(recordsTitle).toBeVisible({ timeout: 20_000 })
    expect(await recordsTitle.evaluate(node => window.getComputedStyle(node).fontSize)).toBe(archiveTitleSize)

    await page.goto('/settings', { waitUntil: 'domcontentloaded' })
    const settingsHeader = page.locator('.settings-page > .page-header')
    const settingsTitle = page.getByRole('heading', { name: '系统设置' })
    await expect(settingsHeader).toBeVisible({ timeout: 20_000 })
    await expect(settingsTitle).toBeVisible()
    expect(await settingsTitle.evaluate(node => window.getComputedStyle(node).fontSize)).toBe(archiveTitleSize)
    expect(await settingsHeader.locator('.header-icon').evaluate(node => window.getComputedStyle(node).display)).toBe('none')
    const settingsEyebrow = await settingsHeader.locator('.header-title').evaluate((node) => {
      return window.getComputedStyle(node, '::before').content
    })
    expect(settingsEyebrow).toContain('System Settings')
    expect(await settingsHeader.evaluate(node => window.getComputedStyle(node).backgroundImage)).toBe('none')

    await page.goto('/help', { waitUntil: 'domcontentloaded' })
    const helpHeader = page.locator('.help-center > .help-header')
    const helpTitle = page.getByRole('heading', { name: '帮助与文档' })
    await expect(helpHeader).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText('MRR DOCUMENTATION', { exact: true })).toBeVisible()
    expect(await helpTitle.evaluate(node => window.getComputedStyle(node).fontSize)).toBe(archiveTitleSize)
    expect(await helpHeader.evaluate(node => window.getComputedStyle(node).backgroundImage)).toBe('none')

    await page.goto('/users', { waitUntil: 'domcontentloaded' })
    const usersTitle = page.getByRole('heading', { name: '用户管理' })
    await expect(usersTitle).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText('User Management', { exact: true })).toBeVisible()
    await expect(page.locator('.mrr-page-header__icon')).toHaveCount(0)
    expect(await usersTitle.evaluate(node => window.getComputedStyle(node).fontSize)).toBe(archiveTitleSize)

    await page.evaluate(() => {
      const storageKey = 'MRR-ADMIN:app-settings'
      const rawSettings = localStorage.getItem(storageKey)
      const savedSettings = rawSettings ? JSON.parse(rawSettings) : { app: {} }
      savedSettings.app = { ...savedSettings.app, pageTitleStyle: 'card' }
      localStorage.setItem(storageKey, JSON.stringify(savedSettings))
    })

    for (const target of [
      { path: '/settings', selector: '.settings-page > .page-header' },
      { path: '/help', selector: '.help-center > .help-header' },
    ]) {
      await page.goto(target.path, { waitUntil: 'domcontentloaded' })
      await expect(page.locator('html')).toHaveAttribute('data-page-title-style', 'card')
      const header = page.locator(target.selector)
      await expect(header).toBeVisible({ timeout: 20_000 })
      expect(await header.evaluate(node => window.getComputedStyle(node).backgroundImage)).not.toBe('none')
    }
  })
})
