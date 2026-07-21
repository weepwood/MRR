import { expect, test } from '@playwright/test'

test.describe('系统设置工作区聚焦', () => {
  test('点击工作区内容后按框架结构对齐并保留上下间距', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 720 })
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto('/settings', { waitUntil: 'domcontentloaded' })

    const frame = page.locator('.settings-workspace-frame')
    const shell = page.locator('.settings-shell')
    await expect(frame).toBeVisible({ timeout: 20_000 })
    await expect(shell).toBeVisible()

    await expect.poll(async () => frame.evaluate(
      element => element.getBoundingClientRect().top,
    )).toBeGreaterThan(10)

    await page.locator('.section-header').click()

    await expect.poll(async () => frame.evaluate(
      element => Math.abs(element.getBoundingClientRect().top),
    )).toBeLessThanOrEqual(2)
    await expect.poll(async () => frame.evaluate(
      element => Math.abs(element.getBoundingClientRect().height - window.innerHeight),
    )).toBeLessThanOrEqual(3)
    await expect.poll(async () => shell.evaluate(
      element => Math.abs(element.getBoundingClientRect().top - 10),
    )).toBeLessThanOrEqual(2)
    await expect.poll(async () => shell.evaluate(
      element => Math.abs(element.getBoundingClientRect().bottom - (window.innerHeight - 10)),
    )).toBeLessThanOrEqual(2)
  })
})
