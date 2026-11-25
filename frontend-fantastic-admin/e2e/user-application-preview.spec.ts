import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('申请信息单行摘要，完整内容在只读弹窗展开', async ({ page }) => {
  const remark = `申请原因：需要查看相关档案。\n第二段申请说明。\n${'长内容'.repeat(220)}\n${'X'.repeat(180)}`
  const contact = 'mock-applicant@example.invalid'
  const reason = '合成拒绝原因\n请补充申请资料。'
  const list = [
    { id: 21, username: 'applicant.test', displayName: '测试申请人', status: 'pending', contactInfo: contact, applyRemark: remark, appliedAt: '2026-11-25T10:00:00' },
    { id: 22, username: 'rejected.test', displayName: '测试拒绝人', status: 'rejected', rejectReason: reason, reviewedAt: '2026-11-25T12:00:00' },
    { id: 23, username: 'created.test', displayName: '测试创建用户', status: 'active' },
  ]
  let writes = 0
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.includes('/auth/users') && request.method() !== 'GET') { writes++ }
  })
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { colorScheme: 'light', pageTitleStyle: 'card', enableMotion: false },
    layout: { enableMobileAdaptation: true },
  })))
  await page.route('**/api/v1/auth/users?*', route => route.fulfill({ json: { code: 200, data: { list, total: list.length } } }))
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/users')
  const row = page.getByRole('row').filter({ hasText: '测试申请人' })
  const preview = row.getByRole('button', { name: '查看 applicant.test 的申请详情' })
  const rejected = page.getByRole('row').filter({ hasText: '测试拒绝人' })
  const created = page.getByRole('row').filter({ hasText: '测试创建用户' })
  await expect(preview).toBeVisible()
  await expect(created.getByText('管理员创建', { exact: true })).toBeVisible()
  await expect(created.locator('.application-preview')).toHaveCount(0)
  await expect(row.getByText(contact, { exact: true })).toHaveCount(0)
  await expect.poll(() => preview.locator('.application-preview__text').evaluate(el => ({
    clipped: el.scrollWidth > el.clientWidth,
    whitespace: getComputedStyle(el).whiteSpace,
  }))).toEqual({ clipped: true, whitespace: 'nowrap' })
  await expect.poll(async () => {
    const a = await row.evaluate(el => el.getBoundingClientRect().height)
    const b = await created.evaluate(el => el.getBoundingClientRect().height)
    return Math.abs(a - b)
  }).toBeLessThanOrEqual(1)

  await preview.focus()
  await preview.press('Enter')
  const dialog = page.getByRole('dialog', { name: '申请详情', exact: true })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText(contact, { exact: true })).toBeVisible()
  await expect(dialog.locator('.application-detail__section p')).toHaveText(remark)
  expect(await dialog.locator('.application-detail__section p').textContent()).toBe(remark)
  await expect(dialog.getByText('申请时间', { exact: true })).toBeVisible()
  await expect.poll(() => dialog.locator('.mrr-dialog-body').evaluate(el => el.scrollWidth <= el.clientWidth + 1 && el.scrollHeight > el.clientHeight)).toBe(true)
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  await expect(preview).toBeFocused()

  await rejected.getByRole('button', { name: '查看 rejected.test 的申请详情' }).click()
  await expect(dialog.getByText('拒绝原因', { exact: true })).toBeVisible()
  expect(await dialog.locator('.application-detail__section--rejected p').textContent()).toBe(reason)
  await expect(dialog.getByText('审核时间', { exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: '关闭', exact: true }).click()
  await expect(dialog).toBeHidden()

  await page.setViewportSize({ width: 375, height: 812 })
  await preview.click()
  await expect(dialog).toBeVisible()
  await expect.poll(() => dialog.locator('.mrr-dialog-body').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await dialog.getByRole('button', { name: '关闭', exact: true }).click()
  await expect(dialog).toBeHidden()
  expect(writes).toBe(0)
})
