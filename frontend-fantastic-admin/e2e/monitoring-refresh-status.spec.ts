import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

test.use({ timezoneId: 'Asia/Shanghai' })

const ok = (data: unknown) => ({ json: { code: 200, data } })
async function monitoringRepro(page: Page) {
  await page.clock.setFixedTime(new Date('2026-11-21T12:00:00+08:00'))
  let failed = false
  let failureCount = 0
  await page.route(/\/api\/v1\/system\/(health|runtime|memory|info|database\/overview|data-quality\/(?:summary|issues))(?:\?|$)/, (route) => {
    if (failed) {
      failureCount++
      return route.fulfill({ status: 503, json: { code: 503, message: '合成监控故障' } })
    }
    const pathname = new URL(route.request().url()).pathname
    if (pathname.endsWith('/health')) {
      return route.fulfill(ok({ status: 'UP', components: { database: { status: 'UP' } } }))
    }
    if (pathname.endsWith('/database/overview')) {
      return route.fulfill(ok({ database: { name: 'audit_db', max_connections: 100 }, connections: { total: 17 }, hikari: {}, transactions: {}, contention: {}, tables: [] }))
    }
    if (pathname.endsWith('/data-quality/summary')) {
      return route.fulfill(ok({ enabled: true, running: false, latestRun: null, checks: [] }))
    }
    return route.fulfill(ok(pathname.endsWith('/issues') ? [] : {}))
  })
  await page.goto('/monitoring')
  const subtitle = page.locator('.subtitle').filter({ hasText: /上次(?:完整)?刷新/ })
  await expect(subtitle).toContainText('12:00:00', { timeout: 30_000 })
  await expect(page.getByText('17/100', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '暂停刷新', exact: true }).click()
  failed = true
  await page.clock.setFixedTime(new Date('2026-11-21T12:03:00+08:00'))
  await page.getByRole('button', { name: '立即刷新', exact: true }).click()
  await expect.poll(() => failureCount).toBe(7)
  await expect(page.getByRole('alert').filter({ hasText: '本次监控刷新未完整完成' })).toBeVisible()
  await expect(subtitle).toContainText('12:00:00')
  await expect(page.getByText('17/100', { exact: true })).toBeVisible()
  await expect(page.locator('.monitor-card .el-tag').filter({ hasText: /^UP$/ }).first()).toBeVisible()
  failed = false
  await page.getByRole('button', { name: '立即刷新', exact: true }).click()
  await expect(subtitle).toContainText('12:03:00')
  await expect(page.getByRole('alert').filter({ hasText: '本次监控刷新未完整完成' })).toHaveCount(0)
}

test('监控接口失败时保留上次完整刷新时间并标记旧值，恢复后清除提示', async ({ page }) => {
  test.setTimeout(60_000)
  await monitoringRepro(page)
})
