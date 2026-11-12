import type { BrowserContext, Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

async function expectCustomFont(page: Page, context: BrowserContext, text: string, prefix: string) {
  await page.evaluate(async ({ text, prefix }) => {
    for (const weight of [400, 500, 700]) {
      const sample = document.createElement('span')
      sample.id = `${prefix}-${weight}`
      sample.textContent = text
      sample.style.cssText = `font-family: "HarmonyOS Sans SC"; font-weight: ${weight}; font-size: 32px;`
      document.body.append(sample)
      await document.fonts.load(`${weight} 32px "HarmonyOS Sans SC"`, text)
    }
    await document.fonts.ready
  }, { text, prefix })
  const session = await context.newCDPSession(page)
  await session.send('DOM.enable')
  await session.send('CSS.enable')
  const { root } = await session.send('DOM.getDocument')
  for (const weight of [400, 500, 700]) {
    const { nodeId } = await session.send('DOM.querySelector', { nodeId: root.nodeId, selector: `#${prefix}-${weight}` })
    const { fonts } = await session.send('CSS.getPlatformFontsForNode', { nodeId })
    expect(fonts.some(font => font.isCustomFont && font.glyphCount > 0)).toBe(true)
  }
  await session.detach()
}

test.use({ storageState: { cookies: [], origins: [] } })

test.beforeEach(async ({ page }) => {
  await page.route('**/api/**', route => route.fulfill({
    json: { code: 200, data: { enabled: false, serverTime: '2026-11-12T12:00:00Z' } },
  }))
  await page.goto('/login')
  await expect(page.locator('.login-page')).toBeVisible()
  await expect(page.locator('[data-app-loading]')).toHaveCount(0)
  await page.evaluate(() => document.fonts.ready)
})

test('普通登录首屏字体低于2MiB，三个字重不下载完整字库', async ({ page, context }) => {
  await expectCustomFont(page, context, '用户管理', 'font-ui')
  const fonts = await page.evaluate(() => performance.getEntriesByType('resource')
    .filter((entry): entry is PerformanceResourceTiming => entry instanceof PerformanceResourceTiming && entry.name.includes('/fonts/'))
    .map(entry => ({ name: entry.name.split('/').pop()!, bytes: entry.encodedBodySize })))
  expect(fonts).toHaveLength(3)
  expect(fonts.reduce((sum, font) => sum + font.bytes, 0)).toBeGreaterThan(0)
  expect(fonts.reduce((sum, font) => sum + font.bytes, 0)).toBeLessThan(2 * 1024 * 1024)
  expect(fonts.some(font => /harmonyos-sans-sc-(?:regular|medium|bold)\.woff2$/.test(font.name))).toBe(false)
})

test('子集之外的合成生僻字仍使用原字库，覆盖全部字重', async ({ page, context }) => {
  await expectCustomFont(page, context, '\u9F98', 'font-fallback')
  const names = await page.evaluate(() => performance.getEntriesByType('resource')
    .filter(entry => entry.name.includes('/fonts/'))
    .map(entry => entry.name.split('/').pop()))
  for (const weight of ['regular', 'medium', 'bold']) {
    expect(names).toContain(`harmonyos-sans-sc-${weight}.woff2`)
  }
})
