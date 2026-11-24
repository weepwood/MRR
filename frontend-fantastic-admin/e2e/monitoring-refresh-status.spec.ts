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
  const subtitle = page.locator('.mrr-page-header__description').filter({ hasText: /上次(?:完整)?刷新/ })
  await expect(subtitle).toContainText('12:00:00', { timeout: 30_000 })
  await expect(page.getByText('17/100', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '暂停刷新', exact: true }).click()
  failed = true
  await page.clock.setFixedTime(new Date('2026-11-21T12:03:00+08:00'))
  await page.getByRole('button', { name: '立即刷新', exact: true }).click()
  await expect.poll(() => failureCount).toBe(7)
  await expect(page.getByRole('alert').filter({ hasText: '本次监控刷新未完整完成' })).toBeVisible()
  await expect(page.getByRole('alert').filter({ hasText: '本次监控刷新未完整完成' })).toContainText('健康状态上次取得数据 12:00:00')
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

test('部分区域持续失败时可分辨各分区最后一次取得数据的时间，从未完整刷新不再恒显 --', async ({ page }) => {
  test.setTimeout(60_000)
  await page.clock.setFixedTime(new Date('2026-11-21T12:00:00+08:00'))
  let qualityFailure = true
  await page.route(/\/api\/v1\/system\/(health|runtime|memory|info|database\/overview|data-quality\/(?:summary|issues))(?:\?|$)/, (route) => {
    const pathname = new URL(route.request().url()).pathname
    if (qualityFailure && (pathname.endsWith('/data-quality/summary') || pathname.endsWith('/data-quality/issues'))) {
      return route.fulfill({ status: 503, json: { code: 503, message: '合成监控故障' } })
    }
    if (pathname.endsWith('/health')) {
      return route.fulfill(ok({ status: 'UP', components: { database: { status: 'UP' } } }))
    }
    if (pathname.endsWith('/database/overview')) {
      return route.fulfill(ok({ database: { name: 'audit_db', max_connections: 100 }, connections: { total: 17 }, hikari: {}, transactions: {}, contention: {}, tables: [] }))
    }
    return route.fulfill(ok(pathname.endsWith('/issues') ? [] : {}))
  })
  await page.goto('/monitoring')
  const description = page.locator('.mrr-page-header__description')
  const alert = page.getByRole('alert').filter({ hasText: '本次监控刷新未完整完成' })

  // 从未完整刷新过：页头不再是「--」
  await expect(description).toContainText('尚未完成过一次完整刷新', { timeout: 30_000 })
  await expect(alert).toBeVisible()
  // 分区级「本次会话尚未取得过数据」可读
  await expect(alert).toContainText('数据质量摘要本次会话尚未取得过数据')

  await page.getByRole('button', { name: '暂停刷新', exact: true }).click()
  qualityFailure = false
  await page.clock.setFixedTime(new Date('2026-11-21T12:03:00+08:00'))
  await page.getByRole('button', { name: '立即刷新', exact: true }).click()
  // 恢复后既有语义完整保留
  await expect(description).toContainText('上次完整刷新 12:03:00')
  await expect(alert).toHaveCount(0)
})

test('严重数据异常指标支持键盘跳转，不改变监控路由', async ({ page }) => {
  await page.goto('/monitoring')
  const url = page.url()
  const link = page.getByRole('link', { name: '查看严重数据异常明细' })
  await expect(link).toBeVisible()
  await link.focus()
  await page.keyboard.press('Enter')
  const target = page.locator('#monitor-issues')
  await expect(target).toBeFocused()
  await expect(target.getByRole('heading', { name: '异常样本' })).toBeInViewport()
  expect(page.url()).toBe(url)
})
