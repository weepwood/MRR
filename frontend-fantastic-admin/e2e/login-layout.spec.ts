import { expect, test } from '@playwright/test'

test.describe('登录页统一设计', () => {
  test('展示统一品牌、登录表单和管理员账号说明', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    await expect(page.locator('.login-shell')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '病案文件管理系统' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '登录 MRR' })).toBeVisible()
    await expect(page.getByLabel('用户名')).toBeVisible()
    await expect(page.getByLabel('密码')).toBeVisible()
    await expect(page.getByRole('button', { name: '登录系统' })).toBeVisible()
    await expect(page.getByText('系统不开放自助注册和在线重置密码。', { exact: false })).toBeVisible()
    await expect(page.getByText('注册新帐号', { exact: true })).toHaveCount(0)
    await expect(page.getByText('忘记密码了?', { exact: true })).toHaveCount(0)
  })

  test('窄屏保持单列并隐藏非必要介绍', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    const shell = page.locator('.login-shell')
    await expect(shell).toBeVisible({ timeout: 20_000 })
    await expect.poll(async () => shell.evaluate(
      element => element.getBoundingClientRect().width <= window.innerWidth,
    )).toBe(true)
    await expect(page.getByRole('heading', { name: '登录 MRR' })).toBeInViewport()
    await expect(page.locator('.feature-list')).toBeHidden()
  })
})
