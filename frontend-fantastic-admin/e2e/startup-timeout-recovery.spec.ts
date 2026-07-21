import { expect, test } from '@playwright/test'

type StartupWindow = Window & {
  __MRR_APP_STARTUP_BLOCKED__?: boolean
  __MRR_APP_STARTUP_READY__?: boolean
}

test.describe('slow application startup recovery', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      const nativeSetTimeout = window.setTimeout.bind(window)
      window.setTimeout = ((handler: TimerHandler, timeout?: number) => {
        const effectiveTimeout = timeout === 15_000 ? 20 : timeout
        return nativeSetTimeout(handler, effectiveTimeout)
      }) as typeof window.setTimeout
    })

    await page.route('**/src/main.ts', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/javascript',
        body: '',
      })
    })
  })

  test('offers recovery actions instead of blocking a slow startup', async ({ page }) => {
    await page.goto('/')

    const blocker = page.locator('#browser-compat-blocker')
    await expect(blocker).toBeVisible()
    await expect(page.locator('#browser-compat-title')).toHaveText('系统启动较慢')
    await expect(page.locator('#browser-compat-message')).toContainText('网络速度较慢')
    await expect(page.getByRole('button', { name: '继续等待' })).toBeVisible()
    await expect(page.getByRole('button', { name: '重新加载' })).toBeVisible()
    await expect(page.getByRole('button', { name: '返回上一页' })).toBeVisible()
    await expect.poll(() => page.evaluate(() => (window as StartupWindow).__MRR_APP_STARTUP_BLOCKED__)).not.toBe(true)
  })

  test('continues waiting and dismisses the warning when the app becomes ready', async ({ page }) => {
    await page.goto('/')

    const blocker = page.locator('#browser-compat-blocker')
    await expect(blocker).toBeVisible()
    await page.getByRole('button', { name: '继续等待' }).click()
    await expect(blocker).toBeHidden()

    await page.evaluate(() => {
      window.dispatchEvent(new CustomEvent('mrr:app-ready'))
    })

    await expect(blocker).toBeHidden()
    await expect.poll(() => page.evaluate(() => (window as StartupWindow).__MRR_APP_STARTUP_READY__)).toBe(true)
  })
})
