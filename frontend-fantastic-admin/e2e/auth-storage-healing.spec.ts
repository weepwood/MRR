import { expect, test } from '@playwright/test'

test.describe('登录状态迁移与自愈', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/public/status/developer-mode', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { enabled: false } }),
      })
    })
  })

  test('旧版强制改密与权限在服务端验证前不会成为最终状态', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('token', 'legacy-token')
      localStorage.setItem('profile', JSON.stringify({
        username: 'doctor',
        mustChangePassword: true,
        permissions: ['stale:permission'],
      }))
      localStorage.setItem('permissions', JSON.stringify(['stale:permission']))
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
    await page.addInitScript(() => {
      localStorage.setItem('token', 'expired-token')
      localStorage.setItem('profile', JSON.stringify({ username: 'doctor' }))
      localStorage.setItem('permissions', '[]')
      localStorage.setItem('login_account', 'doctor')
      localStorage.setItem('mrr:archive:preference', 'keep-me')
    })
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'UNAUTHORIZED', message: 'Token 已失效' }),
      })
    })

    await page.goto('/records', { waitUntil: 'domcontentloaded' })
    await expect(page).toHaveURL(/\/login/)

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
    await page.addInitScript(() => {
      localStorage.setItem('token', 'candidate-token')
      localStorage.setItem('profile', JSON.stringify({ username: 'doctor', mustChangePassword: true }))
      localStorage.setItem('permissions', JSON.stringify(['stale:permission']))
      localStorage.setItem('mrr:archive:preference', 'keep-me')
    })
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'AUTH_SERVICE_UNAVAILABLE', message: '认证服务暂时不可用' }),
      })
    })

    await page.goto('/records', { waitUntil: 'domcontentloaded' })
    await expect(page).toHaveURL(/\/login/)
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
