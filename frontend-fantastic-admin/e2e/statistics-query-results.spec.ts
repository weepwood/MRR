import type { Page, Route } from '@playwright/test'
import { expect, test } from '@playwright/test'

const ok = (data: unknown) => ({ json: { code: 200, data } })
async function statsRepro(page: Page) {
  let pending: Route | undefined
  let exportKeyword
  await page.route(/\/api\/v1\/statistics\/export\/csv(?:\?|$)/, (route) => {
    exportKeyword = new URL(route.request().url()).searchParams.get('keyword')
    return route.fulfill({ contentType: 'text/csv', body: 'case\r\nSYN-NEW\r\n' })
  })
  const payload = (marker: string) => ok({ list: [{ id: 41, bah: `SYN-${marker}`, date: '2026-11-01', pages: 2 }], total: 1, page: 1, size: 100 })
  await page.route(/\/api\/v1\/statistics(?:\?|$)/, (route) => {
    const query = new URL(route.request().url()).searchParams
    const keyword = query.get('keyword')
    if (keyword === 'OLD') {
      pending = route
      return
    }
    return route.fulfill(payload(keyword === 'NEW' ? 'NEW' : 'BASE'))
  })
  await page.goto('/statistics')
  const table = page.locator('.el-table')
  await expect(table.getByText('SYN-BASE', { exact: true })).toBeVisible()
  const filter = page.getByPlaceholder('搜索病案号 / 上架号 / 操作员')
  await filter.fill('OLD')
  await filter.press('Enter')
  await expect.poll(() => Boolean(pending)).toBe(true)
  await filter.fill('NEW')
  await filter.press('Enter')
  await expect(table.getByText('SYN-NEW', { exact: true })).toBeVisible()
  const oldResponse = page.waitForResponse(response => new URL(response.url()).searchParams.get('keyword') === 'OLD')
  await pending!.fulfill(payload('OLD'))
  await (await oldResponse).finished()
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
  await expect(table.getByText('SYN-NEW', { exact: true })).toBeVisible()
  await expect(table.getByText('SYN-OLD', { exact: true })).toHaveCount(0)
  await expect(filter).toHaveValue('NEW')
  await filter.fill('UNSUBMITTED')
  await expect(page.getByRole('button', { name: '导出表格', exact: true })).toBeDisabled()
  await filter.fill('NEW')
  const downloadReady = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出表格', exact: true }).click()
  const download = await downloadReady
  expect(await download.failure()).toBeNull()
  expect(exportKeyword).toBe('NEW')
}

test('迟到查询不覆盖最新列表，导出只使用当前已加载的筛选', async ({ page }) => {
  await statsRepro(page)
})
