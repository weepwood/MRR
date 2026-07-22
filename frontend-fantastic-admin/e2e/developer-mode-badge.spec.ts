import { expect, test } from '@playwright/test'

const developerModeStatusPattern = '**/api/v1/public/status/developer-mode'

test.describe('开发者模式标题标识', () => {
  test('档案袋兼容模式启用时在应用名称旁显示紧凑标识', async ({ page }) => {
    await page.route(developerModeStatusPattern, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { enabled: true, accessMode: 'ARCHIVE_LEGACY' } }),
      })
    })

    await page.goto('/', { waitUntil: 'domcontentloaded' })

    const badge = page.getByTestId('developer-mode-badge')
    await expect(badge).toBeVisible({ timeout: 20_000 })
    await expect(badge).toHaveText('开发者模式')
    await expect(badge).toHaveAttribute('title', '开发者模式已启用')
    await expect(page.getByTestId('developer-mode-banner')).toHaveCount(0)
  })

  test('关闭时不显示开发者模式标识', async ({ page }) => {
    await page.route(developerModeStatusPattern, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { enabled: false, accessMode: 'DISABLED' } }),
      })
    })

    await page.goto('/settings', { waitUntil: 'domcontentloaded' })
    await expect(page.getByTestId('developer-mode-badge')).toHaveCount(0)
    await expect(page.getByTestId('developer-mode-banner')).toHaveCount(0)
  })
})
