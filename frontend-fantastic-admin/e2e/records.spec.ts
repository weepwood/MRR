import { expect, test } from '@playwright/test'

test('免登录模式下可以查看扫描记录详情', async ({ page }) => {
  await page.route('**/api/v1/scan/page?**', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      json: {
        code: 200,
        message: 'success',
        data: {
          list: [
            {
              id: 1,
              bah: 'E2E-2026-001',
              brxh: 'P001',
              sjh: 'S001',
              filename: 'synthetic-record.pdf',
              btype: 1,
              pages: 3,
              openerNo: 'tester',
              folder: '/synthetic/e2e',
              migrationStatus: 'verified',
            },
          ],
          total: 1,
          page: 1,
          size: 20,
        },
      },
    })
  })

  await page.goto('/records')

  await expect(page).toHaveURL(/\/records$/)
  await expect(page.getByRole('heading', { name: '记录管理' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'E2E-2026-001' })).toBeVisible()

  await page.getByRole('button', { name: '详情' }).click()

  await expect(page.getByRole('dialog', { name: '记录详情' })).toContainText('synthetic-record.pdf')
})
