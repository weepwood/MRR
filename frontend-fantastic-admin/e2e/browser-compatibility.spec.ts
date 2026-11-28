import { expect, test } from '@playwright/test'

for (const product of ['Chrome', 'Chromium', 'Edg']) {
  test(`${product} 低于认证基线仍可进入登录并关闭提示`, async ({ browser }, info) => {
    const context = await browser.newContext({
      baseURL: test.info().project.use.baseURL,
      storageState: { cookies: [], origins: [] },
      userAgent: `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) ${product}/85.0.0.0 Safari/537.36`,
    })
    try {
      const page = await context.newPage()
      await page.goto('/login')
      await expect(page.locator('.login-page')).toBeVisible()
      await expect(page.locator('#browser-compat-blocker')).toBeHidden()
      const notice = page.locator('#browser-compat-notice')
      await expect(notice).toBeVisible()
      await expect(notice).toContainText('低于正式验收基线')
      await page.screenshot({ path: info.outputPath('compatibility.png') })
      await notice.getByRole('button', { name: '关闭兼容性提示' }).click()
      await expect(notice).toBeHidden()
      await expect(page.locator('.login-page')).toBeVisible()
    }
    finally { await context.close() }
  })
}

test('Chrome 86 认证基线不显示升级预警', async ({ browser }) => {
  const context = await browser.newContext({
    baseURL: test.info().project.use.baseURL,
    storageState: { cookies: [], origins: [] },
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.0.0 Safari/537.36',
  })
  try {
    const page = await context.newPage()
    await page.goto('/login')
    await expect(page.locator('.login-page')).toBeVisible()
    await expect(page.locator('#browser-compat-blocker')).toBeHidden()
    await expect(page.locator('#browser-compat-notice')).toBeHidden()
  }
  finally { await context.close() }
})

test('缺少不可补齐的 Proxy 才阻断应用', async ({ page }) => {
  await page.addInitScript(() => { Object.defineProperty(window, 'Proxy', { value: undefined }) })
  await page.goto('/login', { waitUntil: 'commit' })
  await expect(page.locator('#browser-compat-blocker')).toBeVisible()
  await expect(page.locator('#browser-compat-blocker')).toContainText('关键运行能力')
  await expect(page.locator('#app')).toBeEmpty()
  expect(await page.evaluate(() => window.__MRR_APP_STARTUP_BLOCKED__)).toBe(true)
})
