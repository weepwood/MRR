import { expect, test } from '@playwright/test'

test.use({ storageState: 'e2e/.auth/anonymous.json' })

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
    const temporaryProfile = {
      id: 20,
      username: 'doctor.test',
      displayName: '测试医生',
      roleCode: 'DOCTOR',
      status: 'active',
      mustChangePassword: true,
      passwordVersion: 1,
      permissions: [],
    }
    await page.addInitScript((profile) => {
      localStorage.clear()
      localStorage.setItem('token', 'temporary-token')
      localStorage.setItem('profile', JSON.stringify(profile))
      localStorage.setItem('permissions', '[]')
    }, temporaryProfile)
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: temporaryProfile }),
      })
    })
    await page.route('**/api/v1/auth/password/required-change', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: '密码修改成功' }),
      })
    })

    await page.goto('/records', { waitUntil: 'domcontentloaded' })
    // 强制改密跳转发生在会话校验之后（GET /api/v1/auth/me），并行跑 dev server
    // 时可能超过断言默认的 5s，故显式放宽到与同套件其他用例一致的量级。
    await expect(page).toHaveURL(/\/password\/change-required/, { timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '首次登录，请设置新密码' })).toBeVisible({ timeout: 20_000 })

    await page.getByPlaceholder('输入当前使用的初始密码').fill('TemporaryPassword123')
    await page.getByPlaceholder('至少 6 位，建议使用更长的易记口令').fill('A brand new password 123')
    await page.getByPlaceholder('再次输入新密码').fill('A brand new password 123')
    await page.getByRole('button', { name: '保存新密码并重新登录' }).click()
    await expect(page).toHaveURL(/\/login/, { timeout: 20_000 })
  })

  test('管理员创建用户后只展示一次临时密码且账号固定启用', async ({ page }) => {
    await page.addInitScript((profile) => {
      localStorage.clear()
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
    await expect(page.getByText('默认启用', { exact: false })).toBeVisible()
    await page.getByLabel('用户名').fill('doctor.test')
    await page.getByLabel('显示名称').fill('测试医生')
    const roleField = page.locator('.el-form-item').filter({ hasText: '角色' }).locator('.el-select__wrapper')
    await roleField.click()
    await page.getByRole('option', { name: '医生' }).click()
    await page.getByRole('button', { name: '创建用户', exact: true }).last().click()

    await expect(page.getByText('TempPass123!ABCD', { exact: true })).toBeVisible()
    await expect(page.getByText('临时密码关闭窗口后无法再次查看')).toBeVisible()
    await expect(page.getByRole('button', { name: '完成' })).toBeDisabled()
    await page.getByText('我已通过安全方式保存或交付临时密码').click()
    await expect(page.getByRole('button', { name: '完成' })).toBeEnabled()
  })

  test('禁用用户对话框仅在确认后提交，拒绝后保留账号状态并允许重试', async ({ page }) => {
    const user = { id: 20, username: 'doctor.test', displayName: '测试医生', roleCode: 'DOCTOR', status: 'active' }
    let attempts = 0
    const retry = Promise.withResolvers<void>()

    await page.addInitScript((profile) => {
      localStorage.setItem('token', 'admin-token')
      localStorage.setItem('profile', JSON.stringify(profile))
      localStorage.setItem('permissions', JSON.stringify(profile.permissions))
    }, adminProfile)
    await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { code: 200, data: adminProfile } }))
    await page.route('**/api/v1/auth/users?*', route => route.fulfill({ json: { code: 200, data: { list: [user], total: 1 } } }))
    await page.route('**/api/v1/auth/users/20', async (route) => {
      expect(route.request().method()).toBe('DELETE')
      attempts++
      if (attempts === 1) {
        await route.fulfill({ status: 403, json: { code: 403, message: '无权禁用此账号' } })
        return
      }
      await retry.promise
      await route.fulfill({ json: { code: 200 } })
    })

    await page.goto('/users')
    const row = page.getByRole('row').filter({ hasText: '测试医生' })
    const dialog = page.getByRole('dialog', { name: '禁用用户' })
    const confirm = dialog.getByRole('button', { name: '确认禁用' })
    async function openDisable() {
      await row.getByRole('button', { name: '更多操作' }).click()
      await page.getByRole('menuitem', { name: '禁用用户' }).click()
      await expect(dialog.getByText(/测试医生.*无法登录/)).toBeVisible()
    }

    await openDisable()
    await dialog.getByRole('button', { name: '取消', exact: true }).click()
    await expect(dialog).toBeHidden()
    await openDisable()
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    expect(attempts).toBe(0)

    await openDisable()
    await confirm.click()
    await expect(page.getByText('无权禁用此账号', { exact: true }).first()).toBeVisible()
    await expect(row.getByText('启用', { exact: true })).toBeVisible()
    await expect(confirm).toBeEnabled()

    await confirm.click()
    await expect.poll(() => attempts).toBe(2)
    await expect(confirm).toBeDisabled()
    await page.keyboard.press('Escape')
    await expect(dialog).toBeVisible()
    retry.resolve()
    await expect(dialog).toBeHidden()
    await expect(row.getByText('禁用', { exact: true })).toBeVisible()
    expect(attempts).toBe(2)
  })

  test('拒绝注册申请使用 Dialog，空白原因和取消均不提交', async ({ page }) => {
    const applicant = { id: 21, username: 'applicant.test', displayName: '测试申请人', status: 'pending' }
    let submissions = 0
    await page.addInitScript((profile) => {
      localStorage.setItem('token', 'admin-token')
      localStorage.setItem('profile', JSON.stringify(profile))
      localStorage.setItem('permissions', JSON.stringify(profile.permissions))
    }, adminProfile)
    await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { code: 200, data: adminProfile } }))
    await page.route('**/api/v1/auth/users?*', route => route.fulfill({ json: { code: 200, data: { list: [applicant], total: 1 } } }))
    await page.route('**/api/v1/auth/users/21/registration/reject', async (route) => {
      submissions++
      expect(route.request().postDataJSON()).toEqual({ rejectReason: '第一行\n第二行' })
      await route.fulfill({ json: { code: 200, data: { ...applicant, status: 'rejected' } } })
    })

    await page.goto('/users')
    const row = page.getByRole('row').filter({ hasText: '测试申请人' })
    const dialog = page.getByRole('dialog', { name: '拒绝注册申请' })
    const reason = dialog.getByPlaceholder('拒绝原因将保存在审核记录中')
    async function openReject() {
      await row.getByRole('button', { name: '拒绝申请' }).click()
      await expect(dialog).toHaveClass(/\bel-overlay-dialog\b/)
      await expect(reason).toBeFocused()
    }

    await openReject()
    await reason.fill('   ')
    await dialog.getByRole('button', { name: '确认拒绝' }).click()
    await expect(dialog.getByText('拒绝原因不能为空')).toBeVisible()
    await dialog.getByRole('button', { name: '取消', exact: true }).click()
    await expect(dialog).toBeHidden()
    expect(submissions).toBe(0)

    await openReject()
    await expect(reason).toHaveValue('')
    await reason.fill(' 第一行')
    await reason.press('Enter')
    await expect(reason).toHaveValue(' 第一行\n')
    await reason.fill(' 第一行\n第二行 ')
    expect(submissions).toBe(0)
    await dialog.getByRole('button', { name: '确认拒绝' }).click()
    await expect(row.getByText('已拒绝', { exact: true })).toBeVisible()
    expect(submissions).toBe(1)
  })
})
