import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => { await isolateLocalVisualSettings(page) })

test.use({ actionTimeout: 10_000 })

// Keep the original regression boundary: the old 320ms minimum must not
// be reachable before the query has finished, regardless of Runner speed.
const QUERY_VIRTUAL_MS = 100
const TOAST_POLL_WINDOW_MS = 1500

async function openUsers(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: 'light' },
      layout: { enableMobileAdaptation: true },
    }))
  })
  await page.goto('/users')
  await expect(page.locator('.user-identity').first()).toBeVisible()
}

test('查询属读操作：反馈只走表格通道，不弹成功通知、不人为放慢', async ({ page }) => {
  await openUsers(page)
  const queryButton = page.getByRole('button', { name: '查询', exact: true })

  await expect(queryButton).toBeVisible()
  await expect(queryButton).toHaveAttribute('aria-busy', 'false')
  await expect(queryButton).toHaveAttribute('aria-disabled', 'false')
  await page.clock.install()
  await page.clock.pauseAt(new Date(await page.evaluate(() => Date.now()) + 1000))

  // Hold the actual Mock response until the in-flight state has been observed.
  // Virtual time advances only on our command; protocol and network latency
  // cannot accidentally consume the old minimum-duration timer.
  let requestSeen = false
  let releaseResponse!: () => void
  const responseGate = new Promise<void>((resolve) => { releaseResponse = resolve })
  await page.route(/\/api\/v1\/auth\/users(?:\?.*)?$/, async (route) => {
    expect(route.request().method()).toBe('GET')
    requestSeen = true
    await responseGate
    await route.continue()
  })
  await page.evaluate(() => {
    const record = window as { __mrrQuerySuccessToast?: boolean }
    const seen = () => [...document.querySelectorAll('.el-message')]
      .some(element => element.textContent?.includes('查询成功'))
    record.__mrrQuerySuccessToast = seen()
    new MutationObserver(() => {
      if (seen()) {
        record.__mrrQuerySuccessToast = true
      }
    }).observe(document.body, { subtree: true, childList: true, characterData: true })
  })

  const startedAt = await page.evaluate(() => Date.now())
  const usersResponse = page.waitForResponse(response =>
    response.request().method() === 'GET' && new URL(response.url()).pathname.endsWith('/auth/users'))
  const rolesResponse = page.waitForResponse(response =>
    response.request().method() === 'GET' && new URL(response.url()).pathname.endsWith('/auth/roles'))
  try {
    // Dispatch the actual button handler without waiting for a frozen rAF
    // actionability check. Visibility/availability were asserted above.
    await queryButton.dispatchEvent('click')
    await expect.poll(() => requestSeen).toBe(true)
    await expect(queryButton).toHaveAttribute('aria-busy', 'true')
    await page.clock.runFor(QUERY_VIRTUAL_MS)
    await expect(queryButton).toHaveAttribute('aria-busy', 'true')
  }
  finally {
    releaseResponse()
  }
  const responses = await Promise.all([usersResponse, rolesResponse])
  expect(responses.every(response => response.ok())).toBe(true)
  // A request-owned spinner must clear without any more time passing.
  // The old minimum would remain busy at 100ms, regardless of host latency.
  await expect(queryButton).toHaveAttribute('aria-busy', 'false', { timeout: 5000 })
  expect(await page.evaluate(() => Date.now()) - startedAt).toBe(QUERY_VIRTUAL_MS)
  await expect(page.locator('.user-identity').first()).toBeVisible()
  await page.clock.runFor(TOAST_POLL_WINDOW_MS)
  expect(await page.evaluate(() => (window as { __mrrQuerySuccessToast?: boolean }).__mrrQuerySuccessToast), '「查询成功」读操作通知不应出现').toBe(false)
})
