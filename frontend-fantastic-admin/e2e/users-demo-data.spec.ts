import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('扩充模拟用户覆盖四种状态、长申请与真实分页', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/users')
  const panel = page.getByRole('region', { name: '用户列表', exact: true })
  const metrics = page.getByRole('region', { name: '当前页账号状态' }).locator('.mrr-metric-card__value')
  const rows = panel.locator('.el-table__body tr.el-table__row')
  await expect(metrics).toHaveText(['6', '9', '3', '2'])
  await expect(rows).toHaveCount(20)
  await expect(panel.locator('.el-pagination__total')).toHaveText('共 28 条')
  await expect(rows.locator('td:nth-child(5) .mrr-status-tag')).toHaveText([
    ...Array.from({ length: 6 }).fill('待审核'),
    '启用',
    '启用',
    '启用',
    '启用',
    '禁用',
    '已拒绝',
    '启用',
    '启用',
    '已拒绝',
    '禁用',
    '启用',
    '启用',
    '启用',
    '已拒绝',
  ])
  const admin = rows.filter({ hasText: '系统管理员' })
  const normal = admin.getByText('正常', { exact: true })
  const active = admin.getByText('启用', { exact: true })
  for (const tag of [normal, active]) {
    await expect(tag.locator('..')).toHaveClass(/mrr-status-tag--success/)
    await expect.poll(() => tag.locator('..').evaluate((el) => {
      const s = getComputedStyle(el)
      return s.borderTopWidth === '1px' && s.backgroundColor !== 'rgba(0, 0, 0, 0)' && s.paddingLeft === '8px'
    })).toBe(true)
  }
  for (const width of [1920, 1440, 768, 375]) {
    await page.setViewportSize({ width, height: 900 })
    await expect.poll(() => rows.evaluateAll(elements => elements.every((row) => {
      const cell = row.querySelector('td:nth-child(2) .cell')!
      const text = cell.querySelector('.application-preview__text, .user-source')!
      const left = cell.getBoundingClientRect().left + Number.parseFloat(getComputedStyle(cell).paddingLeft)
      return Math.abs(text.getBoundingClientRect().left - left) <= 1
    }))).toBe(true)
  }
  await page.setViewportSize({ width: 1440, height: 900 })

  const long = rows.filter({ hasText: '长说明申请演示' })
  await long.getByRole('button', { name: '查看 apply.longtext 的申请详情' }).click()
  const dialog = page.getByRole('dialog', { name: '申请详情', exact: true })
  await expect(dialog.getByText('apply.longtext@example.invalid', { exact: true })).toBeVisible()
  await expect(dialog.locator('.application-detail__section p')).toContainText('补充说明')
  await dialog.getByRole('button', { name: '关闭', exact: true }).click()
  await expect(dialog).toBeHidden()
  await panel.locator('.btn-next').click()
  await expect(rows).toHaveCount(8)
  await expect(metrics).toHaveText(['0', '4', '1', '3'])
  const keyword = panel.getByRole('textbox', { name: '关键词' })
  await keyword.fill('apply.longtext@example.invalid')
  await keyword.press('Enter')
  await expect(rows).toHaveCount(1)
  await expect(rows.first()).toContainText('长说明申请演示')
  await expect(panel.locator('.el-pagination__total')).toHaveText('共 1 条')
  await panel.getByRole('button', { name: '重置', exact: true }).click()
  await expect(rows).toHaveCount(20)
  await panel.locator('.users-filter__select .el-select__wrapper').nth(1).click()
  await page.getByRole('option', { name: '禁用', exact: true }).click()
  await panel.getByRole('button', { name: '查询', exact: true }).click()
  await expect(rows).toHaveCount(5)
  await expect(metrics).toHaveText(['0', '0', '0', '5'])
})
