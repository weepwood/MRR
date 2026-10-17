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
    // 页头图标原本一直由全局规则隐藏，属于死标记，已从模板移除。
    expect(await settingsHeader.locator('.header-icon').count()).toBe(0)
    // 英文标题此前由 ::before 伪元素硬编码注入，不可翻译、无法被读屏获取；
    // 现为真实 DOM 文本，因此断言从「伪元素内容」改为「真实可见文本」。
    await expect(settingsHeader.locator('.eyebrow')).toHaveText('System Settings')
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

    // 回归保护：这两个页面的标题被包裹在 `.title-line` 等额外层级中，
    // 旧实现依赖 `:has(> :first-child > h1/h2)` 反向识别页头，因此无法命中，
    // 标题字号长期偏离统一规范（实测 25px / 26px，字重 400）。
    // 改为显式类选择器后必须与装箱管理页保持一致。
    for (const path of ['/operations-center', '/nginx-browser']) {
      await page.goto(path, { waitUntil: 'domcontentloaded' })
      const title = page.locator('.page-header h1, .page-header h2').first()
      await expect(title).toBeVisible({ timeout: 20_000 })
      expect(await title.evaluate(node => window.getComputedStyle(node).fontSize)).toBe(archiveTitleSize)
    }

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
      { path: '/operations-center', selector: '.page-header' },
      { path: '/nginx-browser', selector: '.page-header' },
    ]) {
      await page.goto(target.path, { waitUntil: 'domcontentloaded' })
      await expect(page.locator('html')).toHaveAttribute('data-page-title-style', 'card')
      const header = page.locator(target.selector)
      await expect(header).toBeVisible({ timeout: 20_000 })
      expect(await header.evaluate(node => window.getComputedStyle(node).backgroundImage)).not.toBe('none')
    }
  })
})
