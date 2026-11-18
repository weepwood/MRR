import { expect, test } from '@playwright/test'

test('搜索保留原有页头，已在屏内的设置不滚动，屏外设置只定位一次', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 720 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/settings', { waitUntil: 'domcontentloaded' })

  const hero = page.locator('.settings-page > .page-header')
  const search = page.getByRole('textbox', { name: '搜索设置' })
  await expect(hero.getByRole('heading', { name: '系统设置', exact: true })).toBeInViewport()
  await expect(hero.getByText('统一管理系统标识', { exact: false })).toBeVisible()
  await expect(search).toBeInViewport()

  await page.evaluate(() => {
    let calls = 0
    const scrollTo = window.scrollTo
    document.documentElement.dataset.settingsScrollCalls = '0'
    window.scrollTo = (...args) => {
      document.documentElement.dataset.settingsScrollCalls = String(++calls)
      Reflect.apply(scrollTo, window, args)
    }
  })

  await search.fill('系统名称')
  await page.getByRole('option', { name: /系统标识.*系统信息/ }).click()
  await expect(page.getByText('系统标识', { exact: true })).toBeFocused()
  expect(await page.evaluate(() => window.scrollY)).toBe(0)
  await expect(page.locator('html')).toHaveAttribute('data-settings-scroll-calls', '0')

  await search.fill('记住选择状态')
  await page.getByRole('option', { name: /记住选择状态.*档案浏览/ }).click()
  const result = page.getByText('记住选择状态', { exact: true })
  await expect(result).toBeFocused()
  await expect(result).toBeInViewport()
  expect(await page.evaluate(() => window.scrollY)).toBeGreaterThan(100)
  await expect(page.locator('html')).toHaveAttribute('data-settings-scroll-calls', '1')
})
