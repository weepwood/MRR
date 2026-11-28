import { expect, test } from '@playwright/test'

test.use({ storageState: { cookies: [], origins: [] } })

test('真实延迟入口自动继续，成功后移除静态外壳', async ({ page }, info) => {
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route(/\/src\/main\.ts(?:\?.*)?$/, async (route) => {
    await gate
    await route.continue()
  })
  try {
    await page.clock.install()
    await page.clock.pauseAt(new Date(Date.now() + 1000))
    await page.goto('/login', { waitUntil: 'commit' })
    const shell = page.locator('[data-app-loading]')
    await expect(page.locator('html')).toHaveAttribute('data-mrr-startup', 'pending')
    await expect(shell).toBeAttached()
    await page.clock.runFor(499)
    await expect(shell).toBeHidden()
    await page.clock.runFor(1)
    await expect(shell).toBeVisible()
    await expect(shell).toContainText('正在加载应用资源')
    const loader = shell.locator('.startup-classic-loader')
    await expect(loader).toBeVisible()
    await expect(shell.locator('.startup-skeleton')).toHaveCount(0)
    const geometry = await loader.evaluate((el) => {
      const r = el.getBoundingClientRect()
      return {
        centerX: Math.abs(r.x + r.width / 2 - innerWidth / 2),
        centerY: Math.abs(r.y + r.height / 2 - innerHeight / 2),
        before: getComputedStyle(el, '::before').animationName,
        after: getComputedStyle(el, '::after').animationName,
        duration: getComputedStyle(el, '::before').animationDuration,
      }
    })
    expect(geometry.centerX).toBeLessThanOrEqual(1)
    expect(geometry.centerY).toBeLessThanOrEqual(1)
    expect(geometry.before).toBe('mrr-startup-before')
    expect(geometry.after).toBe('mrr-startup-after')
    expect(geometry.duration).toBe('2s')
    await page.screenshot({ path: info.outputPath('classic-loading.png') })
    await page.clock.runFor(15_000)
    await expect(shell).toContainText('系统仍在加载')
    await expect(loader).toBeVisible()
    await expect(shell.getByRole('button', { name: '重新加载' })).toBeVisible()
    await expect(page.locator('#browser-compat-blocker')).toBeHidden()
    await expect(page.locator('#app')).toBeEmpty()
    await page.screenshot({ path: info.outputPath('slow-desktop.png'), animations: 'disabled' })
    await page.setViewportSize({ width: 390, height: 820 })
    await page.screenshot({ path: info.outputPath('slow-mobile.png'), animations: 'disabled' })
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(391)
    await page.clock.runFor(60_000)
    await expect(shell).toContainText('系统仍在加载')
    await expect(page.locator('#browser-compat-blocker')).toBeHidden()
    await page.clock.resume()
    release()
    await expect(page.locator('.login-page')).toBeVisible({ timeout: 30_000 })
    await expect(shell).toHaveCount(0)
    await expect(page.locator('#browser-compat-blocker')).toBeHidden()
  }
  finally { release() }
})

test('入口资源失败提供恢复，重新加载可正常进入', async ({ page }) => {
  await page.route(/\/src\/main\.ts(?:\?.*)?$/, route => route.abort('failed'), { times: 1 })
  await page.goto('/login')
  const shell = page.locator('[data-app-loading]')
  await expect(shell).toContainText('应用资源加载失败')
  await expect(shell.locator('.startup-classic-loader')).toBeHidden()
  await expect(shell.getByRole('button', { name: '重新加载' })).toBeVisible()
  await shell.getByRole('button', { name: '重新加载' }).click()
  await expect(page.locator('.login-page')).toBeVisible()
  await expect(shell).toHaveCount(0)
})

test('网络断开时入口失败明确提示离线，不冒充版本不兼容', async ({ page, context }) => {
  let release!: () => void
  let received!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  const started = new Promise<void>((resolve) => { received = resolve })
  await page.route(/\/src\/main\.ts(?:\?.*)?$/, async (route) => {
    received()
    await gate
    await route.abort('internetdisconnected')
  })
  try {
    await page.goto('/login', { waitUntil: 'commit' })
    await started
    await expect(page.locator('[data-app-loading]')).toBeVisible()
    await context.setOffline(true)
    release()
    await expect(page.locator('[data-app-loading]')).toContainText('当前设备处于离线状态')
    await expect(page.locator('#browser-compat-blocker')).toBeHidden()
  }
  finally {
    release()
    await context.setOffline(false)
  }
})
