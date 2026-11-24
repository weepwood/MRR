import { expect, test } from '@playwright/test'

test('统计明细通过日期快捷项筛选整年并支持重置', async ({ page }) => {
  await page.goto('/statistics')
  await expect(page.getByPlaceholder('全部年份')).toHaveCount(0)
  const start = page.getByPlaceholder('开始日期')
  const end = page.getByPlaceholder('结束日期')
  await start.click()
  const year = await page.evaluate(() => new Date().getFullYear())
  const request = page.waitForRequest((req) => {
    const url = new URL(req.url())
    return url.pathname.endsWith('/api/v1/statistics')
      && url.searchParams.get('startDate') === `${year}-01-01`
      && url.searchParams.get('endDate') === `${year}-12-31`
      && url.searchParams.get('page') === '1'
  })
  await page.locator('.el-picker-panel__shortcut:visible').and(page.getByRole('button', { name: `${year} 年`, exact: true })).click()
  await request
  await expect(start).toHaveValue(`${year}-01-01`)
  await expect(end).toHaveValue(`${year}-12-31`)
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(start).toHaveValue('')
  await expect(end).toHaveValue('')
})
