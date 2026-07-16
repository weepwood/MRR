import { expect, test } from '@playwright/test'

test.describe('浏览器兼容性通知', () => {
  test.use({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36',
  })

  test('版本过低时显示通知并由用户手动关闭', async ({ page }) => {
    await page.goto('/')

    const notice = page.locator('#browser-compat-notice')
    await expect(notice).toBeVisible()
    await expect(notice).toContainText('Chrome 110.0.0.0')
    await expect(notice).toContainText('最低支持版本 Chrome 111')

    await page.getByRole('button', { name: '关闭浏览器兼容性通知' }).click()
    await expect(notice).toBeHidden()

    await page.reload()
    await expect(notice).toBeHidden()
  })

  test('Chromium 内核版本低于 111 时显示通知', async ({ browser }) => {
    const context = await browser.newContext({
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chromium/110.0.0.0 Safari/537.36',
    })
    const page = await context.newPage()

    await page.goto('/')

    const notice = page.locator('#browser-compat-notice')
    await expect(notice).toBeVisible()
    await expect(notice).toContainText('Chrome 110.0.0.0')
    await expect(notice).toContainText('最低支持版本 Chrome 111')

    await context.close()
  })
})
