import { expect, test } from '@playwright/test'

test.use({ storageState: 'e2e/.auth/anonymous.json' })

async function installLegacySession(page: import('@playwright/test').Page, values: Record<string, string>) {
  await page.addInitScript((entries) => {
    localStorage.clear()
    for (const [key, value] of Object.entries(entries)) {
      localStorage.setItem(key, value)
    }
  }, values)
}

test.describe('登录状态迁移与自愈', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/public/status/developer-mode', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { enabled: false, accessMode: 'DISABLED' } }),
      })
    })
  })

  test('旧版强制改密与权限在服务端验证前不会成为最终状态', async ({ page }) => {
    await installLegacySession(page, {
      token: 'legacy-token',
      profile: JSON.stringify({
        username: 'doctor',
        mustChangePassword: true,
        permissions: ['stale:permission'],
      }),
      permissions: JSON.stringify(['stale:permission']),
    })
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          data: {
            id: 20,
            username: 'doctor',
            displayName: '测试医生',
            status: 'active',
            mustChangePassword: false,
            permissions: ['record:read'],
          },
        }),
      })
    })

    await page.goto('/records', { waitUntil: 'domcontentloaded' })

    await expect(page).toHaveURL(/\/records/)
    await expect.poll(() => page.evaluate(() => localStorage.getItem('mrr:auth:token'))).toBe('legacy-token')
    const state = await page.evaluate(() => ({
      token: localStorage.getItem('mrr:auth:token'),
      legacyToken: localStorage.getItem('token'),
      profile: JSON.parse(localStorage.getItem('mrr:auth:profile') || '{}'),
      permissions: JSON.parse(localStorage.getItem('mrr:auth:permissions') || '[]'),
    }))
    expect(state.token).toBe('legacy-token')
    expect(state.legacyToken).toBeNull()
    expect(state.profile.mustChangePassword).toBe(false)
    expect(state.permissions).toEqual(['record:read'])
  })

  test('401 会清理新旧认证键但保留记住用户名和业务偏好', async ({ page }) => {
    await installLegacySession(page, {
      token: 'expired-token',
      profile: JSON.stringify({ username: 'doctor' }),
      permissions: '[]',
      login_account: 'doctor',
      'mrr:archive:preference': 'keep-me',
    })
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'UNAUTHORIZED', message: 'Token 已失效' }),
      })
    })

    await page.goto('/records', { waitUntil: 'domcontentloaded' })
    await expect(page).toHaveURL(/\/login/, { timeout: 15_000 })

    const state = await page.evaluate(() => ({
      token: localStorage.getItem('mrr:auth:token'),
      legacyToken: localStorage.getItem('token'),
      profile: localStorage.getItem('mrr:auth:profile'),
      rememberedAccount: localStorage.getItem('mrr:login:remembered-account'),
      businessPreference: localStorage.getItem('mrr:archive:preference'),
    }))
    expect(state.token).toBeNull()
    expect(state.legacyToken).toBeNull()
    expect(state.profile).toBeNull()
    expect(state.rememberedAccount).toBe('doctor')
    expect(state.businessPreference).toBe('keep-me')
  })

  test('认证服务 503 时保留候选 Token 并进入可恢复登录状态', async ({ page }) => {
    await installLegacySession(page, {
      token: 'candidate-token',
      profile: JSON.stringify({ username: 'doctor', mustChangePassword: true }),
      permissions: JSON.stringify(['stale:permission']),
      'mrr:archive:preference': 'keep-me',
    })
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'AUTH_SERVICE_UNAVAILABLE', message: '认证服务暂时不可用' }),
      })
    })

    await page.goto('/records', { waitUntil: 'domcontentloaded' })
    await expect(page).toHaveURL(/\/login/, { timeout: 15_000 })
    expect(new URL(page.url()).searchParams.get('session')).toBe('unavailable')

    const state = await page.evaluate(() => ({
      token: localStorage.getItem('mrr:auth:token'),
      legacyToken: localStorage.getItem('token'),
      businessPreference: localStorage.getItem('mrr:archive:preference'),
    }))
    expect(state.token).toBe('candidate-token')
    expect(state.legacyToken).toBeNull()
    expect(state.businessPreference).toBe('keep-me')
  })
})
