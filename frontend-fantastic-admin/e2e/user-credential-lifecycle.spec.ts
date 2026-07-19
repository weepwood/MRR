import { expect, test } from '@playwright/test'

const adminProfile = {
  id: 1,
  username: 'admin',
  displayName: '系统管理员',
  roleCode: 'ADMIN',
  roleName: '管理员',
  status: 'active',
  permissions: ['user:manage', 'role:read', 'system:read'],
  mustChangePassword: false,
  passwordVersion: 1,
}

test.describe('用户凭据生命周期', () => {
  test('待改密用户访问业务页面时强制进入独立改密页', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('token', 'temporary-token')
      localStorage.setItem('profile', JSON.stringify({
        id: 20,
        username: 'doctor.test',
        displayName: '测试医生',
        roleCode: 'DOCTOR',
        status: 'active',
        mustChangePassword: true,
        passwordVersion: 1,
      }))
      localStorage.setItem('permissions', '[]')
    })

    await page.route('**/api/v1/auth/password/required-change', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: '密码修改成功' }),
      })
    })

    await page.goto('/records', { waitUntil: 'domcontentloaded' })
    await expect(page).toHaveURL(/\/password\/change-required/)
    await expect(page.getByRole('heading', { name: '首次登录，请设置新密码' })).toBeVisible()

    await page.getByLabel('当前初始密码').fill('TemporaryPassword123')
    await page.getByLabel('新密码').fill('A brand new password 123')
    await page.getByLabel('确认新密码').fill('A brand new password 123')
    await page.getByRole('button', { name: '保存新密码并重新登录' }).click()
    await expect(page).toHaveURL(/\/login/)
  })

  test('管理员创建用户后只展示一次临时密码且账号固定启用', async ({ page }) => {
    await page.addInitScript((profile) => {
      localStorage.setItem('token', 'admin-token')
      localStorage.setItem('profile', JSON.stringify(profile))
      localStorage.setItem('permissions', JSON.stringify(profile.permissions))
    }, adminProfile)

    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, data: adminProfile }) })
    })
    await page.route('**/api/v1/auth/roles', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: [
          { code: 'ADMIN', name: '管理员' },
          { code: 'DOCTOR', name: '医生' },
        ] }),
      })
    })
    await page.route('**/api/v1/auth/users*', async (route) => {
      if (route.request().method() === 'POST') {
        expect(route.request().postDataJSON()).toMatchObject({
          username: 'doctor.test',
          roleCode: 'DOCTOR',
          status: 'active',
        })
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 200,
            data: {
              user: {
                id: 20,
                username: 'doctor.test',
                displayName: '测试医生',
                roleCode: 'DOCTOR',
                status: 'active',
                mustChangePassword: true,
              },
              temporaryPassword: 'TempPass123!ABCD',
              temporaryPasswordExpiresAt: '2026-07-20T21:00:00',
            },
          }),
        })
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { list: [], total: 0, page: 1, size: 20 } }),
      })
    })

    await page.goto('/users', { waitUntil: 'domcontentloaded' })
    await page.getByRole('button', { name: '创建用户' }).click()
    await expect(page.getByLabel('初始状态')).toHaveCount(0)
    await expect(page.getByText('新用户默认启用', { exact: false })).toBeVisible()
    await page.getByLabel('用户名').fill('doctor.test')
    await page.getByLabel('显示名称').fill('测试医生')
    await page.getByLabel('角色').click()
    await page.getByRole('option', { name: '医生' }).click()
    await page.getByRole('button', { name: '创建用户', exact: true }).last().click()

    await expect(page.getByText('TempPass123!ABCD', { exact: true })).toBeVisible()
    await expect(page.getByText('临时密码关闭窗口后无法再次查看')).toBeVisible()
    await expect(page.getByRole('button', { name: '完成' })).toBeDisabled()
    await page.getByText('我已通过安全方式保存或交付临时密码').click()
    await expect(page.getByRole('button', { name: '完成' })).toBeEnabled()
  })
})
