import { expect, test } from '@playwright/test'

async function readVariable(page: import('@playwright/test').Page, name: string) {
  return page.evaluate(key => getComputedStyle(document.documentElement).getPropertyValue(key).trim().toLowerCase(), name)
}

test('暖色扁平风格可切换明暗、持久保存，并安全返回 MRR 配色', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/settings?section=appearance', { waitUntil: 'domcontentloaded' })
  const gr8r = page.getByRole('button', { name: /暖色扁平风格/ })
  const classic = page.getByRole('button', { name: /MRR 经典/ })
  await expect(gr8r).toBeVisible()

  await gr8r.click()
  await expect(gr8r).toHaveAttribute('aria-pressed', 'true')
  await expect.poll(() => readVariable(page, '--g-main-area-bg')).toBe('#faf9f7')
  await expect.poll(() => readVariable(page, '--mrr-navigation-bg-solid')).toBe('#f3f2ef')
  await expect.poll(() => readVariable(page, '--color-primary')).toBe('#5362ce')

  const themeTabs = page.locator('.theme-tabs')
  await themeTabs.getByRole('tab', { name: '暗黑' }).click()
  await expect.poll(() => readVariable(page, '--g-main-area-bg')).toBe('#1b1b1a')
  await expect.poll(() => readVariable(page, '--color-primary')).toBe('#9da8f7')

  await themeTabs.getByRole('tab', { name: '明亮' }).click()
  await expect.poll(() => readVariable(page, '--g-main-area-bg')).toBe('#faf9f7')
  await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem('MRR-ADMIN:app-settings') || '{}').app?.themePalette)).toBe('gr8r')

  await page.reload({ waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('button', { name: /暖色扁平风格/ })).toHaveAttribute('aria-pressed', 'true')
  await expect.poll(() => readVariable(page, '--color-primary')).toBe('#5362ce')

  await page.getByRole('button', { name: /MRR 经典/ }).click()
  await expect.poll(() => readVariable(page, '--color-primary')).toBe('#2563eb')
  await expect.poll(() => page.evaluate(() => document.documentElement.style.getPropertyValue('--g-main-area-bg'))).toBe('')
  await expect(page.locator('.theme-colors')).toBeVisible()
  await expect(classic).toBeVisible()
})
