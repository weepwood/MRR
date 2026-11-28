import { expect, test } from '@playwright/test'

test.use({ storageState: { cookies: [], origins: [] } })

test('公开状态页刷新、日期过滤与清除保持匿名可用', async ({ page }) => {
  let summaries = 0
  await page.route('**/api/v1/public/status/summary**', async (route) => {
    summaries++
    await route.fallback()
  })
  await page.goto('/status')
  await expect(page.getByText('所有服务正常运行', { exact: true })).toBeVisible()
  const initial = summaries
  const refresh = page.getByRole('button', { name: '刷新状态', exact: true })
  await expect(refresh).toHaveClass(/el-button/)
  await refresh.focus()
  await refresh.press('Enter')
  await expect.poll(() => summaries).toBeGreaterThan(initial)
  await expect(refresh).toBeEnabled()
  const day = page.locator('.day-cell.is-down').first()
  await day.click()
  await expect(day).toHaveAttribute('aria-pressed', 'true')
  const clear = page.getByRole('button', { name: '查看全部', exact: true })
  await expect(clear).toHaveClass(/el-button/)
  await clear.click()
  await expect(day).toHaveAttribute('aria-pressed', 'false')
  await expect(clear).toHaveCount(0)
  await page.setViewportSize({ width: 390, height: 844 })
  await expect.poll(() => page.locator('html').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
})
