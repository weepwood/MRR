import { expect, test } from '@playwright/test'

test.use({ storageState: 'e2e/.auth/anonymous.json' })

test.describe('登录页统一设计', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/public/status/developer-mode', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { enabled: false, accessMode: 'DISABLED' } }),
      })
    })
  })

  test('展示统一品牌、登录表单和管理员账号说明', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    await expect(page.locator('.login-shell')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('heading', { name: 'MRR 病案文件管理系统' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '登录 MRR' })).toBeVisible()
    await expect(page.getByRole('textbox', { name: '用户名' })).toBeVisible()
    await expect(page.getByLabel('密码', { exact: true })).toBeVisible()
    await expect(page.getByRole('checkbox', { name: '记住用户名' })).toBeVisible()
    await expect(page.getByRole('button', { name: '登录系统' })).toBeVisible()
    await expect(page.getByText('账号创建、角色调整或密码问题请联系系统管理员。', { exact: false })).toBeVisible()
    await expect(page.locator('.theme-control')).toHaveCount(0)
    await expect(page.locator('.brand-mark')).toHaveCount(0)
    await expect(page.getByText('注册新帐号', { exact: true })).toHaveCount(0)
    await expect(page.getByText('忘记密码了?', { exact: true })).toHaveCount(0)
  })

  test('匿名用户可以直接提交注册申请', async ({ page }) => {
    let registrationRequested = false
    await page.route('**/api/v1/auth/register', async (route) => {
      registrationRequested = true
      expect(route.request().method()).toBe('POST')
      expect(route.request().headers().authorization).toBeUndefined()
      expect(route.request().postDataJSON()).toMatchObject({
        username: 'doctor.apply',
        password: '123456',
        displayName: '申请医生',
        contactInfo: '内线 6123',
      })
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: '注册申请已提交，请等待管理员审核',
          data: { username: 'doctor.apply', status: 'pending' },
        }),
      })
    })

    await page.goto('/login?mode=register', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: '申请系统账号' })).toBeVisible()
    await page.getByLabel('用户名').fill('doctor.apply')
    await page.getByLabel('显示名称').fill('申请医生')
    await page.getByLabel('联系方式').fill('内线 6123')
    await page.getByLabel('密码', { exact: true }).fill('123456')
    await page.getByLabel('确认密码').fill('123456')
    await page.getByRole('button', { name: '提交注册申请' }).click()

    await expect.poll(() => registrationRequested).toBe(true)
    await expect(page.getByText('注册申请已提交，请等待管理员审核。审核通过后即可登录。')).toBeVisible()
    await expect(page.getByRole('heading', { name: '登录 MRR' })).toBeVisible()
  })

  test('匿名读取系统标识并悬停显示管理员联系方式', async ({ page }) => {
    await page.route('**/api/v1/public/config/login-page', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            systemName: '医院病案影像平台',
            systemShortName: '院内系统',
            systemEnglishName: 'Hospital Medical Archive',
            organizationName: '测试医院',
            systemDescription: '统一管理院内病案影像。',
            loginHelpText: '账号问题请联系系统管理员。',
            systemAdminContactVisible: 'true',
            systemAdminDisplayName: 'MRR 运维组',
            systemAdminDepartment: '信息科',
            systemAdminPhone: '0571-12345678',
            systemAdminServiceHours: '工作日 08:00–17:30',
          },
        }),
      })
    })

    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    await expect(page.getByRole('heading', { name: '医院病案影像平台' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '登录 院内系统' })).toBeVisible()
    const contact = page.getByRole('button', { name: '查看系统管理员信息' })
    await expect(contact).toBeVisible()
    await contact.hover()
    await expect(page.getByText('MRR 运维组')).toBeVisible()
    await expect(page.getByText('0571-12345678')).toBeVisible()
    await expect(page.getByText('工作日 08:00–17:30')).toBeVisible()
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
