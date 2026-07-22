import { expect, test } from '@playwright/test'

test.describe('浏览器兼容性阻断', () => {
  test('Chrome 108 低于基线时阻止应用启动', async ({ browser }) => {
    const context = await browser.newContext({
      storageState: 'e2e/.auth/anonymous.json',
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36',
    })
    const page = await context.newPage()

    await page.goto('/')

    const blocker = page.locator('#browser-compat-blocker')
    await expect(blocker).toBeVisible()
    await expect(blocker).toContainText('检测到 Chrome 108.0.0.0')
    await expect(blocker).toContainText('最低支持版本 Chrome 109')
    await expect(page.locator('#app')).toBeEmpty()

    await context.close()
  })

  test('Chromium 108 同样按 Chrome 内核基线阻断', async ({ browser }) => {
    const context = await browser.newContext({
      storageState: 'e2e/.auth/anonymous.json',
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chromium/108.0.0.0 Safari/537.36',
    })
    const page = await context.newPage()

    await page.goto('/')

    const blocker = page.locator('#browser-compat-blocker')
    await expect(blocker).toBeVisible()
    await expect(blocker).toContainText('检测到 Chrome 108.0.0.0')
    await expect(blocker).toContainText('最低支持版本 Chrome 109')

    await context.close()
  })

  test('Chrome 109 可以进入应用', async ({ browser }) => {
    const context = await browser.newContext({
      storageState: 'e2e/.auth/admin.json',
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36',
    })
    const page = await context.newPage()

    await page.goto('/', { waitUntil: 'domcontentloaded' })

    await expect(page.locator('#browser-compat-blocker')).toBeHidden({ timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '管理概览' })).toBeVisible({ timeout: 20_000 })

    await context.close()
  })
})
