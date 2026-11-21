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
        // 把「启动看门狗」的 15s 压到 20ms；用 >= 判断，避免页面把常量改成计算值后补丁失效。
        const effectiveTimeout = (timeout ?? 0) >= 15_000 ? 20 : timeout
        return nativeSetTimeout(handler, effectiveTimeout)
      }) as typeof window.setTimeout
    })

    // 用正则而不是 glob：Vite 给改动过的入口会加 `?t=<时间戳>` 做缓存失效，
    // `**/src/main.ts` 匹配不到带查询串的 URL，真实应用就会照常挂载并发出 app-ready，
    // 看门狗随即被清掉，浮层永远不出现（表现为「元素存在但一直 hidden」）。
    await page.route(/\/src\/main\.ts(?:\?.*)?$/, async (route) => {
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
