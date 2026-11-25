import { expect, test } from '@playwright/test'

test('运行错误详情拒绝错位响应、可重试且关闭后清除旧详情', async ({ page }) => {
  let attempt = 0
  await page.route('**/api/v1/system-errors/1', async (route) => {
    attempt++
    if (attempt === 1) {
      await route.fulfill({ json: { code: 200, data: { id: 2, errorId: 'DEMO-ERR-002', stackTrace: 'OTHER-SYNTHETIC-STACK' } } })
      return
    }
    await route.fallback()
  })
  await page.goto('/runtime-errors')
  const first = page.getByRole('row').filter({ hasText: 'DEMO-ERR-001' })
  await first.getByRole('button', { name: '详情', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '运行错误详情' })
  await expect(dialog.getByText('无法读取运行错误详情，请重试')).toBeVisible()
  await expect(dialog.getByText('OTHER-SYNTHETIC-STACK')).toHaveCount(0)
  await dialog.getByRole('button', { name: /重试/ }).click()
  await expect(dialog.getByText('合成异常摘要：无真实运行堆栈')).toBeVisible()
  await expect(dialog.getByText('DEMO-ERR-001', { exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await page.getByRole('row').filter({ hasText: 'DEMO-ERR-002' }).getByRole('button', { name: '详情', exact: true }).click()
  await expect(dialog.getByText('DEMO-ERR-002', { exact: true })).toBeVisible()
  await expect(dialog.getByText('DEMO-ERR-001', { exact: true })).toHaveCount(0)
  expect(attempt).toBe(2)
})
