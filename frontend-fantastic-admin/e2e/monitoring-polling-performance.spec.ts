import { expect, test } from '@playwright/test'

test('监控仅在可见的活动路由轮询，返回后尊重手动暂停', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { enableMotion: false },
      menu: { mode: 'single' },
    }))
  })
  let reads = 0
  let totalReads = 0
  let statusReads = 0
  let slowOverview: Promise<void> | undefined
  await page.route(/\/api\/v1\/public\/status\/(summary|daily|minutes|incidents)(?:\?|$)/, async (route) => {
    expect(route.request().method()).toBe('GET')
    if (route.request().frame().parentFrame()) {
      statusReads++
    }
    const summary = new URL(route.request().url()).pathname.endsWith('/summary')
    await route.fulfill({ json: { code: 200, data: summary ? { currentStatus: 'NO_DATA' } : [] } })
  })
  await page.route(/\/api\/v1\/system\/(health|runtime|memory|info|database\/overview|data-quality\/(?:summary|issues))(?:\?|$)/, async (route) => {
    expect(route.request().method()).toBe('GET')
    totalReads++
    if (new URL(route.request().url()).pathname.endsWith('/database/overview')) {
      reads++
      await slowOverview
    }
    const data = new URL(route.request().url()).pathname.endsWith('/issues') ? [] : {}
    await route.fulfill({ json: { code: 200, data } })
  })
  await page.clock.install()
  await page.goto('/monitoring')
  const status = page.locator('.subtitle').filter({ hasText: '上次刷新' })
  await expect(status).toContainText(/上次刷新 \d/)
  await page.clock.pauseAt(new Date(await page.evaluate(() => Date.now()) + 1000))
  expect(reads).toBe(1)
  const frame = page.locator('iframe[title="MRR 服务可用性状态"]')
  await frame.evaluate(element => element.scrollIntoView())
  await page.clock.runFor(100)
  await expect.poll(() => statusReads).toBe(4)

  await page.clock.runFor(15_000)
  await expect.poll(() => reads).toBe(2)

  const system = page.locator('.sub-sidebar-container .menu-item-container[title="系统"]')
  const users = page.getByRole('link', { name: '用户管理', exact: true })
  async function goToUsers() {
    if (!await users.isVisible()) {
      await system.click()
      await page.clock.runFor(500)
    }
    await users.click()
    await expect(page.getByRole('heading', { name: '用户管理', exact: true })).toBeVisible()
  }

  await goToUsers()
  const away = reads
  const totalAway = totalReads
  const statusAway = statusReads
  await expect(frame).toHaveCount(0)
  await page.clock.runFor(60_000)
  await page.waitForTimeout(250)
  expect(reads - away).toBe(0)
  expect(totalReads - totalAway).toBe(0)
  expect(statusReads - statusAway).toBe(0)

  await page.goBack()
  await expect(status).toBeVisible()
  await expect.poll(() => reads).toBe(away + 1)
  await expect(frame).toBeAttached()
  await page.getByRole('button', { name: '暂停刷新' }).click()

  await goToUsers()
  await page.goBack()
  await expect(page.getByRole('button', { name: '恢复刷新' })).toBeVisible()
  const paused = reads
  await page.clock.runFor(30_000)
  expect(reads).toBe(paused)
  await page.getByRole('button', { name: '立即刷新' }).click()
  await expect.poll(() => reads).toBe(paused + 1)
  await expect(page.getByRole('button', { name: '立即刷新' })).toBeEnabled()

  await page.getByRole('button', { name: '恢复刷新' }).click()
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    Object.defineProperty(document, 'hidden', { configurable: true, value: true })
    document.dispatchEvent(new Event('visibilitychange'))
  })
  const hidden = reads
  await page.clock.runFor(60_000)
  expect(reads).toBe(hidden)
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
    Object.defineProperty(document, 'hidden', { configurable: true, value: false })
    document.dispatchEvent(new Event('visibilitychange'))
  })
  const beforePending = totalReads
  const pending = Promise.withResolvers<void>()
  slowOverview = pending.promise
  await page.clock.runFor(15_000)
  await expect.poll(() => reads).toBe(hidden + 1)
  await expect.poll(() => totalReads).toBe(beforePending + 7)

  const manual = page.getByRole('button', { name: '立即刷新' })
  const inFlight = reads
  const totalInFlight = totalReads
  await manual.click()
  await expect(manual).toBeDisabled()
  await page.clock.runFor(30_000)
  expect(reads).toBe(inFlight)
  expect(totalReads).toBe(totalInFlight)
  slowOverview = undefined
  pending.resolve()
  await expect(manual).toBeEnabled()
  await page.clock.runFor(15_000)
  await expect.poll(() => reads).toBe(inFlight + 1)
})
