import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('所有账号状态固定两图标，恢复拒绝申请后须重新审核', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  const list = [
    { id: 301, username: 'pending.test', displayName: '待审核测试', status: 'pending', roleCode: 'OPERATOR', applyRemark: '模拟申请' },
    { id: 302, username: 'rejected.test', displayName: '已拒绝测试', status: 'rejected', roleCode: 'OPERATOR', rejectReason: '原拒绝原因测试' },
    { id: 303, username: 'disabled.test', displayName: '已禁用测试', status: 'disabled', roleCode: 'OPERATOR' },
    { id: 304, username: 'active.test', displayName: '已启用测试', status: 'active', roleCode: 'OPERATOR' },
  ]
  let reopenRequests = 0
  let approvals = 0
  await page.route('**/api/v1/auth/users?*', route => route.fulfill({ json: { code: 200, data: { list: [...list].sort((a, b) => Number(b.status === 'pending') - Number(a.status === 'pending')), total: list.length } } }))
  await page.route('**/api/v1/auth/users/302/registration/reopen', async (route) => {
    expect(route.request().method()).toBe('POST')
    reopenRequests++
    if (reopenRequests === 1) {
      await route.fulfill({ json: { code: 409, message: '模拟状态冲突' } })
      return
    }
    list[1]!.status = 'pending'
    await route.fulfill({ json: { code: 200, data: list[1] } })
  })
  await page.route('**/api/v1/auth/users/302/registration/approve', async (route) => {
    approvals++
    expect(list[1]!.status).toBe('pending')
    list[1]!.status = 'active'
    await route.fulfill({ json: { code: 200, data: list[1] } })
  })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/users')
  const rows = page.locator('.users-table-panel .el-table__body tr.el-table__row')
  await expect(rows).toHaveCount(4)
  for (const width of [1440, 768, 375]) {
    await page.setViewportSize({ width, height: 900 })
    for (const row of await rows.all()) {
      await expect(row.locator('.mrr-table-actions__button')).toHaveCount(2)
      await expect(row.getByRole('button', { name: '更多操作' })).toBeVisible()
    }
  }
  await page.setViewportSize({ width: 1440, height: 900 })
  const pending = rows.filter({ hasText: '待审核测试' })
  await pending.getByRole('button', { name: '审核注册申请' }).click()
  const audit = page.getByRole('dialog', { name: '审核注册申请', exact: true })
  await expect(audit).toBeVisible()
  await expect(audit.getByRole('button', { name: '拒绝申请' })).toBeVisible()
  await audit.getByRole('button', { name: '取消' }).click()
  await expect(audit).toBeHidden()

  const rejected = rows.filter({ hasText: '已拒绝测试' })
  await rejected.getByRole('button', { name: '恢复待审核' }).click()
  const restore = page.getByRole('dialog', { name: '恢复注册申请' })
  await expect(restore.getByText('原拒绝原因测试')).toBeVisible()
  await restore.getByRole('button', { name: '取消' }).click()
  await expect(restore).toBeHidden()
  expect(reopenRequests).toBe(0)
  await rejected.getByRole('button', { name: '恢复待审核' }).click()
  await restore.getByRole('button', { name: '恢复待审核', exact: true }).click()
  await expect(page.getByText('模拟状态冲突', { exact: true }).first()).toBeVisible()
  await expect(restore).toBeVisible()
  await restore.getByRole('button', { name: '恢复待审核', exact: true }).click()
  await expect(restore).toBeHidden()
  await expect(rejected.getByRole('button', { name: '审核注册申请' })).toBeVisible()
  expect(reopenRequests).toBe(2)
  expect(approvals).toBe(0)
  await rejected.getByRole('button', { name: '审核注册申请' }).click()
  await expect(audit.getByText('上次拒绝原因')).toBeVisible()
  await expect(audit.getByText('原拒绝原因测试')).toBeVisible()
  await audit.getByRole('button', { name: '确认通过' }).click()
  await expect(audit).toBeHidden()
  await expect(rejected.getByRole('button', { name: '编辑用户' })).toBeVisible()
  expect(approvals).toBe(1)

  const disabled = rows.filter({ hasText: '已禁用测试' })
  await disabled.getByRole('button', { name: '更多操作' }).click()
  await page.getByRole('menuitem', { name: '恢复启用' }).click()
  const edit = page.getByRole('dialog', { name: '编辑用户', exact: true })
  await expect(edit).toBeVisible()
  await expect(edit.getByText('启用', { exact: true })).toBeVisible()
  await edit.getByRole('button', { name: '取消' }).click()
})
