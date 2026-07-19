import { expect, test } from '@playwright/test'

const developerModeStatusPattern = '**/api/v1/public/status/developer-mode'

test.describe('开发者模式全局警告', () => {
  test('启用时在后台主布局持续显示安全警告', async ({ page }) => {
    await page.route(developerModeStatusPattern, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { enabled: true } }),
      })
    })

    await page.goto('/', { waitUntil: 'domcontentloaded' })

    const banner = page.getByTestId('developer-mode-banner')
    await expect(banner).toBeVisible({ timeout: 20_000 })
    await expect(banner.getByText('开发者模式已启用', { exact: true })).toBeVisible()
    await expect(banner).toContainText('虚拟管理员身份')
    await expect(banner).toContainText('请勿在正式环境长期启用')

    await banner.getByRole('button', { name: '打开系统设置' }).click()
    await expect(page).toHaveURL(/\/settings(?:\?.*)?$/)
  })

  test('关闭时不占用后台页面空间', async ({ page }) => {
    await page.route(developerModeStatusPattern, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { enabled: false } }),
      })
    })

    await page.goto('/settings', { waitUntil: 'domcontentloaded' })
    await expect(page.getByTestId('developer-mode-banner')).toHaveCount(0)
  })
})
