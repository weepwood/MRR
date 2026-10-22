import { randomUUID } from 'node:crypto'
import process from 'node:process'
import { expect, test } from '@playwright/test'

test.beforeEach(async ({ browser, page }) => {
  const version = browser.version()
  process.stdout.write(`生产构建回归内核版本：${version}；API 为合成响应\n`)
  if (process.env.MRR_COMPAT_BROWSER_MAJOR) {
    expect(version.split('.')[0], '实际内核必须符合指定版本，禁止用 UA 冒充').toBe(process.env.MRR_COMPAT_BROWSER_MAJOR)
  }
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const data = path.endsWith('/public/status/developer-mode')
      ? { enabled: false, accessMode: 'DISABLED' }
      : null
    await route.fulfill({ json: { code: 200, data } })
  })
})

test('生产入口加载本地 polyfill，登录表单可操作', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  const polyfillLoaded = page.waitForResponse(response => /\/assets\/polyfills[^/]*\.js$/.test(new URL(response.url()).pathname))
  await page.goto('/login')
  expect((await polyfillLoaded).ok()).toBe(true)
  await expect(page.getByRole('heading', { name: '登录 MRR' })).toBeVisible()
  await page.locator('#login-account').fill('compat-synthetic')
  await page.locator('#login-password').fill(randomUUID())
  await expect(page.getByRole('button', { name: '登录系统', exact: true })).toBeEnabled()
  expect(await page.evaluate(() => typeof Array.prototype.at)).toBe('function')
  expect(await page.evaluate(() => typeof Array.prototype.findLast)).toBe('function')
  await expect(page.locator('#browser-compat-blocker')).toBeHidden()
  expect(errors).toEqual([])
})

test('合成会话可查询、翻页和查看详情，过期后回到登录页', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  let expired = false
  const profile = { id: 999, username: 'compat-synthetic', displayName: '兼容测试', status: 'ACTIVE', permissions: ['record:read'], mustChangePassword: false }
  const token = randomUUID()
  await page.route('**/api/v1/auth/login', route => route.fulfill({ json: { code: 200, data: { token, user: profile } } }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({
    status: expired ? 401 : 200,
    json: expired ? { code: 'UNAUTHORIZED', message: '合成会话已过期' } : { code: 200, data: profile },
  }))
  await page.route('**/api/v1/scan/cursor/condition?**', (route) => {
    const nextBatch = new URL(route.request().url()).searchParams.get('afterId') === '1'
    return route.fulfill({
      json: { code: 200, data: {
        list: [{ id: nextBatch ? 2 : 1, bah: 'COMPAT-SYNTHETIC', filename: nextBatch ? 'compat-next.jpg' : 'compat-synthetic.jpg', btype: 1 }],
        hasMore: !nextBatch,
        nextCursorId: nextBatch ? undefined : 1,
        size: 20,
      } },
    })
  })

  await page.goto('/records')
  await expect(page).toHaveURL(/\/login/)
  await page.locator('#login-account').fill(profile.username)
  await page.locator('#login-password').fill(randomUUID())
  await page.getByRole('button', { name: '登录系统', exact: true }).click()
  await expect(page.getByRole('heading', { name: '记录管理' })).toBeVisible()
  await page.getByPlaceholder('输入病案号').fill('COMPAT-SYNTHETIC')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('cell', { name: 'COMPAT-SYNTHETIC', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '下一批', exact: true }).click()
  await expect(page.getByRole('cell', { name: 'compat-next.jpg', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '下一批', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '上一批', exact: true }).click()
  await expect(page.getByRole('cell', { name: 'compat-synthetic.jpg', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '查看详情', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '记录详情' })).toContainText('compat-synthetic.jpg')

  expired = true
  await page.reload()
  await expect(page).toHaveURL(/\/login/)
  expect(await page.evaluate(() => localStorage.getItem('mrr:auth:token'))).toBeNull()
  expect(errors).toEqual([])
})
