import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => { await isolateLocalVisualSettings(page) })

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
    expect(archiveTitleSize).toBe('22px')

    await page.goto('/records', { waitUntil: 'domcontentloaded' })
    const recordsTitle = page.getByRole('heading', { name: '记录管理' })
    await expect(recordsTitle).toBeVisible({ timeout: 20_000 })
    expect(await recordsTitle.evaluate(node => window.getComputedStyle(node).fontSize)).toBe(archiveTitleSize)

    await page.goto('/settings', { waitUntil: 'domcontentloaded' })
    const settingsHeader = page.locator('.settings-page > .mrr-page-header')
    const settingsTitle = page.getByRole('heading', { name: '系统设置' })
    await expect(settingsHeader).toBeVisible({ timeout: 20_000 })
    await expect(settingsTitle).toBeVisible()
    expect(await settingsTitle.evaluate(node => window.getComputedStyle(node).fontSize)).toBe(archiveTitleSize)
    // 页头图标原本一直由全局规则隐藏，属于死标记，已从模板移除。
    expect(await settingsHeader.locator('.header-icon').count()).toBe(0)
    // 装饰性英文眉标题已从业务页移除，保留中文主标题。
    await expect(settingsHeader.locator('.mrr-page-header__eyebrow')).toHaveCount(0)
    expect(await settingsHeader.evaluate(node => window.getComputedStyle(node).backgroundImage)).toBe('none')

    await page.goto('/help', { waitUntil: 'domcontentloaded' })
    const helpHeader = page.locator('.help-center > .mrr-page-header')
    const helpTitle = page.getByRole('heading', { name: '帮助与文档' })
    await expect(helpHeader).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText('MRR DOCUMENTATION', { exact: true })).toHaveCount(0)
    expect(await helpTitle.evaluate(node => window.getComputedStyle(node).fontSize)).toBe(archiveTitleSize)
    expect(await helpHeader.evaluate(node => window.getComputedStyle(node).backgroundImage)).toBe('none')

    await page.goto('/users', { waitUntil: 'domcontentloaded' })
    const usersTitle = page.getByRole('heading', { name: '用户管理' })
    await expect(usersTitle).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText('User Management', { exact: true })).toHaveCount(0)
    await expect(page.locator('.mrr-page-header__icon')).toHaveCount(0)
    expect(await usersTitle.evaluate(node => window.getComputedStyle(node).fontSize)).toBe(archiveTitleSize)

    // 回归保护：这两个页面的标题被包裹在 `.title-line` 等额外层级中，
    // 旧实现依赖 `:has(> :first-child > h1/h2)` 反向识别页头，因此无法命中，
    // 标题字号长期偏离统一规范（实测 25px / 26px，字重 400）。
    // 改为显式类选择器后必须与装箱管理页保持一致。
    for (const path of ['/operations-center', '/nginx-browser']) {
      await page.goto(path, { waitUntil: 'domcontentloaded' })
      const title = page.locator('.mrr-page-header h1').first()
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
      { path: '/settings', selector: '.settings-page > .mrr-page-header' },
      { path: '/help', selector: '.help-center > .mrr-page-header' },
      { path: '/operations-center', selector: '.mrr-page-header' },
      { path: '/nginx-browser', selector: '.mrr-page-header' },
    ]) {
      await page.goto(target.path, { waitUntil: 'domcontentloaded' })
      await expect(page.locator('html')).toHaveAttribute('data-page-title-style', 'card')
      const header = page.locator(target.selector)
      await expect(header).toBeVisible({ timeout: 20_000 })
      const style = await header.evaluate((node) => {
        const style = window.getComputedStyle(node)
        return { backgroundImage: style.backgroundImage, backgroundColor: style.backgroundColor, borderWidth: style.borderTopWidth, borderStyle: style.borderTopStyle, borderRadius: Number.parseFloat(style.borderRadius), boxShadow: style.boxShadow }
      })
      expect(style.backgroundImage).toBe('none')
      expect(style.backgroundColor).not.toBe('rgba(0, 0, 0, 0)')
      expect(style.borderWidth).toBe('1px')
      expect(style.borderStyle).toBe('solid')
      expect(style.borderRadius).toBeGreaterThan(0)
      expect(style.boxShadow).toBe('none')
    }
  })
})

const businessRoutes = [
  '/',
  '/users',
  '/permissions',
  '/settings',
  '/help',
  '/records',
  '/patients',
  '/ocr-batches',
  '/statistics',
  '/statistics-detail',
  '/archive-boxes',
  '/oss-migration',
  '/oss-browser',
  '/data-exchange',
  '/data-relations',
  '/archive-workbench',
  '/operations-center',
  '/database-browse',
  '/logs',
  '/runtime-errors',
  '/audit-images',
  '/monitoring',
  '/response-analysis',
  '/auth-test',
  '/nginx-browser',
  '/ui-components',
]

test('所有后台页头采用相同尺寸和左右边界', async ({ page }, testInfo) => {
  test.setTimeout(240_000)
  await page.setViewportSize({ width: 2400, height: 1000 })
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { pageTitleStyle: 'card', colorScheme: 'light', enableMotion: false },
    menu: { mode: 'single' },
    layout: { enableMobileAdaptation: true },
  })))
  const measure = async () => {
    const header = page.locator('.mrr-page-header').first()
    await expect(header).toBeVisible()
    await expect(header.locator('h1')).toBeVisible()
    return header.evaluate((el) => {
      const css = getComputedStyle(el)
      const rect = el.getBoundingClientRect()
      const title = el.querySelector('h1')!
      const description = el.querySelector('.mrr-page-header__description')!
      return {
        x: rect.x,
        width: rect.width,
        height: rect.height,
        titleSize: getComputedStyle(title).fontSize,
        descriptionSize: getComputedStyle(description).fontSize,
        paddingTop: css.paddingTop,
        paddingBottom: css.paddingBottom,
        paddingLeft: css.paddingLeft,
        paddingRight: css.paddingRight,
        radius: css.borderRadius,
        shellWidth: el.closest('.mrr-page-shell')?.getBoundingClientRect().width ?? 0,
        overflow: el.scrollWidth > el.clientWidth + 1,
      }
    })
  }
  await page.goto('/users')
  const baseline = await measure()
  expect(baseline.titleSize).toBe('22px')
  expect(baseline.descriptionSize).toBe('13px')
  expect(baseline.paddingTop).toBe('16px')
  for (const path of businessRoutes) {
    await page.goto(path)
    await expect(page.locator('html')).toHaveAttribute('data-page-title-style', 'card')
    const actual = await measure()
    expect(actual.titleSize, path).toBe(baseline.titleSize)
    expect(actual.descriptionSize, path).toBe(baseline.descriptionSize)
    expect(actual.paddingTop, path).toBe(baseline.paddingTop)
    expect(actual.paddingBottom, path).toBe(baseline.paddingBottom)
    expect(actual.paddingLeft, path).toBe(baseline.paddingLeft)
    expect(actual.paddingRight, path).toBe(baseline.paddingRight)
    expect(actual.radius, path).toBe(baseline.radius)
    expect(Math.abs(actual.width - actual.shellWidth), path).toBeLessThanOrEqual(1)
    expect(Math.abs(actual.width - baseline.width), path).toBeLessThanOrEqual(1)
    expect(Math.abs(actual.x - baseline.x), path).toBeLessThanOrEqual(1)
    expect(actual.overflow, path).toBe(false)
    await testInfo.attach(`header-geometry-${path.replace(/\//g, '_')}`, { body: JSON.stringify(actual), contentType: 'application/json' })
  }
})
