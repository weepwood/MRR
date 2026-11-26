import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('普通分页条数靠右且文字紧凑，保留原生选择及跳页', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.route('**/api/v1/logs/search**', async (route) => {
    const params = new URL(route.request().url()).searchParams
    const size = Number(params.get('size') ?? 20)
    await route.fulfill({ json: { code: 200, data: { list: [], total: 2000000, page: Number(params.get('page') ?? 1), size, totalPages: Math.ceil(2000000 / size) } } })
  })
  await page.goto('/logs')
  const pager = page.locator('.mrr-data-table-panel__pagination .el-pagination')
  await expect(pager).toBeVisible()
  const sizes = pager.locator('.el-pagination__sizes')
  const jump = pager.locator('.el-pagination__jump')
  const input = jump.locator('input')
  await expect.poll(() => pager.evaluate((el) => {
    const rect = (selector: string) => el.querySelector(selector)!.getBoundingClientRect()
    const label = rect('.el-pagination__sizes .el-select__placeholder')
    const arrow = rect('.el-pagination__sizes .el-select__caret')
    return {
      rightmost: rect('.el-pagination__sizes').left >= rect('.el-pagination__jump').right,
      arrowGap: Math.round(arrow.left - label.right),
      jumpWidth: Math.round(rect('.el-pagination__editor').width),
    }
  })).toMatchObject({ rightmost: true, arrowGap: 4, jumpWidth: expect.any(Number) })
  expect(await input.evaluate(el => el.getBoundingClientRect().width)).toBeLessThanOrEqual(34)
  await sizes.locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: '100 条/页', exact: true }).click()
  await expect(sizes).toContainText('100 条/页')
  await input.fill('12345')
  await input.press('Enter')
  await expect(pager.locator('.el-pager .is-active')).toHaveText('12345')
  for (const width of [1440, 768, 375, 320]) {
    await page.setViewportSize({ width, height: 900 })
    await expect.poll(() => pager.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  }
})
