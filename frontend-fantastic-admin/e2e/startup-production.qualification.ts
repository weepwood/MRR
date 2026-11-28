import { expect, test } from '@playwright/test'

const profile = { id: 92001, username: 'synthetic-doctor', roleCode: 'DOCTOR', status: 'active', permissions: ['record:read'], mustChangePassword: false }
const reply = (data: unknown) => ({ code: 200, data })
test.beforeEach(async ({ page }) => {
  await page.route('**/*', async (route) => {
    if (!route.request().url().startsWith('http://127.0.0.1:19383/')) {
      await route.abort()
      return
    }
    await route.continue()
  })
  await page.route('**/api/v1/**', route => route.fulfill({ json: reply({}) }))
  await page.route('**/api/v1/public/status/developer-mode', route => route.fulfill({ json: reply({ enabled: false, accessMode: 'DISABLED' }) }))
  await page.route('**/api/v1/settings', route => route.fulfill({ json: reply({ archiveWatermarkEnabled: true, archiveWatermarkOpacity: 14 }) }))
  await page.addInitScript(() => {
    Reflect.deleteProperty(Array.prototype, 'at')
    Reflect.deleteProperty(Array.prototype, 'findLast')
    Reflect.deleteProperty(Object, 'hasOwn')
  })
})

async function seedSession(page: import('@playwright/test').Page) {
  await page.addInitScript((user) => {
    localStorage.setItem('mrr:auth:schema-version', '1')
    localStorage.setItem('mrr:auth:token', 'synthetic-expiring-token')
    localStorage.setItem('mrr:auth:account', user.username)
    localStorage.setItem('mrr:auth:profile', JSON.stringify(user))
    localStorage.setItem('mrr:auth:permissions', JSON.stringify(user.permissions))
  }, profile)
}

test('生产 Polyfill 在缺少现代 API 时恢复运行，匿名不能访问病案', async ({ page }) => {
  let images = 0
  await page.route('**/api/v1/img/search?**', (route) => { images++; return route.fulfill({ json: reply([]) }) })
  await page.goto('/archive?bah=92001')
  await expect(page.locator('.login-page')).toBeVisible()
  await expect(page.locator('#browser-compat-notice')).toBeVisible()
  await expect(page.locator('[data-app-loading]')).toHaveCount(0)
  expect(images).toBe(0)
  expect(await page.evaluate(() => [typeof Array.prototype.at, typeof Array.prototype.findLast, typeof Object.hasOwn])).toEqual(['function', 'function', 'function'])
})

test('会话检查延迟时不展示医疗数据，401 后恢复到登录', async ({ page }, info) => {
  await seedSession(page)
  let release!: () => void
  let received!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  const started = new Promise<void>((resolve) => { received = resolve })
  let images = 0
  await page.route('**/api/v1/img/search?**', (route) => { images++; return route.fulfill({ json: reply([]) }) })
  await page.route('**/api/v1/auth/me', async (route) => {
    received()
    await gate
    await route.fulfill({ status: 401, json: { code: 'UNAUTHORIZED', message: '合成会话已失效' } })
  })
  try {
    await page.goto('/archive?bah=92001', { waitUntil: 'commit' })
    await started
    await expect(page.locator('[data-app-loading]')).toBeVisible()
    await expect(page.locator('#startup-message')).toContainText('会话校验')
    await expect(page.locator('#app')).toHaveCSS('visibility', 'hidden')
    await expect(page.locator('.preview-image')).toHaveCount(0)
    expect(images).toBe(0)
    await page.screenshot({ path: info.outputPath('session-check.png') })
    release()
    await expect(page.locator('.login-page')).toBeVisible()
    await expect(page.locator('[data-app-loading]')).toHaveCount(0)
    expect(images).toBe(0)
    expect(await page.evaluate(() => localStorage.getItem('mrr:auth:token'))).toBeNull()
  }
  finally { release() }
})

test('实际生产产物授权后显示全视口水印，离开档案袋移除', async ({ page }, info) => {
  await seedSession(page)
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: reply(profile) }))
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: reply([]) }))
  await page.route(/\/api\/v1\/search\/patient\/[^/?]+/, route => route.fulfill({ json: reply([]) }))
  await page.route('**/api/v1/archive-search-history**', route => route.fulfill({ json: reply([]) }))
  await page.goto('/archive?bah=92001')
  await expect(page.getByRole('heading', { name: '住院病案' })).toBeVisible()
  const watermark = page.locator('[data-archive-watermark="true"]')
  await expect(watermark).toBeAttached()
  await expect.poll(() => watermark.evaluate((el) => {
    const r = el.getBoundingClientRect()
    return r.x === 0 && r.y === 0 && Math.abs(r.width - innerWidth) <= 1 && Math.abs(r.height - innerHeight) <= 1 && getComputedStyle(el).backgroundImage.includes('data:')
  })).toBe(true)
  await page.screenshot({ path: info.outputPath('watermark.png') })
  await page.goto('/users')
  await expect(page.locator('[data-error-code="403"]')).toBeVisible()
  await expect(watermark).toHaveCount(0)
})
