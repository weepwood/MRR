import type { Page } from '@playwright/test'
import { errors, expect, test } from '@playwright/test'

async function expectApplicationToRemainStopped(page: Page) {
  let applicationMounted = true
  try {
    await page.locator('#app > *').first().waitFor({
      state: 'attached',
      timeout: 15_000,
    })
  }
  catch (error) {
    if (!(error instanceof errors.TimeoutError)) {
      throw error
    }
    applicationMounted = false
  }

  expect(applicationMounted).toBe(false)
  await expect(page.locator('#app')).toBeEmpty()
}

test.describe('浏览器兼容性阻断', () => {
  test('Chrome 85 低于基线时阻止应用启动', async ({ browser }) => {
    const context = await browser.newContext({
      storageState: 'e2e/.auth/anonymous.json',
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.0.0 Safari/537.36',
    })
    const page = await context.newPage()

    await page.goto('/')

    const blocker = page.locator('#browser-compat-blocker')
    await expect(blocker).toBeVisible()
    await expect(blocker).toContainText('检测到 Chrome 85.0.0.0')
    await expect(blocker).toContainText('最低支持版本 Chrome 86')
    // 覆盖“遮罩先出现、Vue 随后仍启动”的竞态。
    await expectApplicationToRemainStopped(page)

    await context.close()
  })

  test('Chromium 85 同样按 Chrome 内核基线阻断', async ({ browser }) => {
    const context = await browser.newContext({
      storageState: 'e2e/.auth/anonymous.json',
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chromium/85.0.0.0 Safari/537.36',
    })
    const page = await context.newPage()

    await page.goto('/')

    const blocker = page.locator('#browser-compat-blocker')
    await expect(blocker).toBeVisible()
    await expect(blocker).toContainText('检测到 Chrome 85.0.0.0')
    await expect(blocker).toContainText('最低支持版本 Chrome 86')
    await expectApplicationToRemainStopped(page)

    await context.close()
  })

  test('Chrome 86 可以进入应用', async ({ browser }) => {
    const context = await browser.newContext({
      storageState: 'e2e/.auth/admin.json',
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.0.0 Safari/537.36',
    })
    const page = await context.newPage()

    await page.goto('/', { waitUntil: 'domcontentloaded' })

    await expect(page.locator('#browser-compat-blocker')).toBeHidden({ timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '管理概览' })).toBeVisible({ timeout: 20_000 })

    await context.close()
  })
})
