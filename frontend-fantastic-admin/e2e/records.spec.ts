import { expect, test } from '@playwright/test'

test('免登录模式下可以查看扫描记录详情', async ({ page }) => {
  await page.route('**/api/v1/scan/cursor/condition?**', async (route) => {
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
          hasMore: false,
          size: 20,
        },
      },
    })
  })

  await page.goto('/records', { waitUntil: 'domcontentloaded' })

  await expect(page).toHaveURL(/\/records$/)
  await expect(page.getByRole('heading', { name: '记录管理' })).toBeVisible({ timeout: 20_000 })
  await expect(page.getByRole('cell', { name: 'E2E-2026-001' })).toBeVisible({ timeout: 20_000 })

  await page.getByRole('button', { name: '详情' }).click()

  await expect(page.getByRole('dialog', { name: '记录详情' })).toContainText('synthetic-record.pdf', { timeout: 20_000 })
})

test('编辑筛选不会把旧游标用于新条件，提交查询后从头读取', async ({ page }) => {
  const requests: { afterId: string | null, bah?: string }[] = []
  await page.route('**/api/v1/scan/cursor/condition?**', async (route) => {
    const url = new URL(route.request().url())
    requests.push({ afterId: url.searchParams.get('afterId'), bah: route.request().postDataJSON().bah })
    await route.fulfill({ json: { code: 200, data: { list: [{ id: 100, bah: '123', filename: 'synthetic.jpg' }], hasMore: true, nextCursorId: 100, size: 20 } } })
  })
  await page.goto('/records')
  await expect(page.getByRole('cell', { name: '123', exact: true })).toBeVisible()
  await expect(page.locator('label[for="records-bah"]')).toBeVisible()
  await page.getByLabel('病案号', { exact: true }).fill('000456')
  await expect(page.getByText('筛选条件已修改', { exact: false })).toBeVisible()
  await expect(page.locator('.records-query-context')).toContainText('已提交条件：全部记录')
  await page.getByRole('button', { name: '下一批', exact: true }).click()
  await expect.poll(() => requests.length).toBe(2)
  expect(requests[1]).toEqual({ afterId: '100', bah: undefined })
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => requests.length).toBe(3)
  expect(requests[2]).toEqual({ afterId: '0', bah: '000456' })
  await expect(page.locator('.records-query-context')).toContainText('病案号：000456')
  await expect(page.getByText('筛选条件已修改', { exact: false })).toHaveCount(0)
})

for (const theme of ['light', 'dark']) {
  test(`${theme} 主题下查询面板在宽窄屏保持可读`, async ({ page }) => {
    await page.addInitScript((scheme) => {
      localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { colorScheme: scheme, pageTitleStyle: 'card' },
        layout: { enableMobileAdaptation: true },
      }))
    }, theme)
    await page.goto('/records')
    await expect(page.getByRole('heading', { name: '扫描记录', exact: true })).toBeVisible()
    for (const width of [1366, 375]) {
      await page.setViewportSize({ width, height: 900 })
      await expect(page.locator('.records-query-context')).toBeVisible()
      await expect(page.locator('label[for="records-bah"]')).toBeVisible()
      await expect.poll(() => page.locator('.records-page').evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
    }
  })
}
