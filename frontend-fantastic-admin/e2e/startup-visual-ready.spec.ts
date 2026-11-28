import { Buffer } from 'node:buffer'
import { expect, test } from '@playwright/test'

test('慢速字体使用稳定回退，不等待全部资源', async ({ page }, info) => {
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/fonts/*.woff2', async (route) => {
    await gate
    await route.continue()
  })
  await page.route('**/api/v1/settings/ui', route => route.fulfill({ json: { code: 200, data: {} } }))
  try {
    await page.goto('/', { waitUntil: 'commit' })
    await expect(page.locator('html')).toHaveAttribute('data-mrr-startup-font', 'system')
    await expect(page.locator('[data-app-loading]')).toHaveCount(0)
    const font = await page.locator('#app').evaluate(el => getComputedStyle(el).getPropertyValue('--mrr-font-sans'))
    expect(font).not.toContain('HarmonyOS Sans SC')
    // CDP captures the current frame without Playwright awaiting the deliberately held fonts.
    const cdp = await page.context().newCDPSession(page)
    const screenshot = await cdp.send('Page.captureScreenshot', { format: 'png' })
    await info.attach('fallback-first', { body: Buffer.from(screenshot.data, 'base64'), contentType: 'image/png' })
    await cdp.detach()
    release()
    await page.evaluate(() => document.fonts.ready)
    await expect(page.locator('html')).toHaveAttribute('data-mrr-startup-font', 'system')
    expect(await page.locator('#app').evaluate(el => getComputedStyle(el).getPropertyValue('--mrr-font-sans'))).toBe(font)
    await page.screenshot({ path: info.outputPath('fallback-settled.png') })
  }
  finally { release() }
})

test('首屏等待已在途外观配置，应用后才揭示页面', async ({ page }, info) => {
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: 'light', enableMotion: false } }))
    window.addEventListener('mrr:startup-stage', (event) => {
      if ((event as CustomEvent).detail === 'visual') { document.documentElement.dataset.visualTest = 'yes' }
    })
  })
  await page.route('**/api/v1/settings/ui', async (route) => {
    await gate
    await route.fulfill({ json: { code: 200, data: { adminAppSettings: JSON.stringify({ app: { colorScheme: 'dark' } }) } } })
  })
  try {
    await page.goto('/', { waitUntil: 'commit' })
    await expect(page.locator('html')).toHaveAttribute('data-visual-test', 'yes')
    await expect(page.locator('#app')).toHaveCSS('opacity', '0')
    release()
    await expect(page.locator('[data-app-loading]')).toHaveCount(0)
    await expect(page.locator('html')).toHaveClass(/\bdark\b/)
    await page.screenshot({ path: info.outputPath('first-dark.png') })
  }
  finally { release() }
})

test('桌面导航与首页在解除启动隐藏的同一渲染帧可见', async ({ page }, info) => {
  await page.route('**/api/v1/settings/ui', route => route.fulfill({ json: { code: 200, data: {} } }))
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { enableMotion: true }, menu: { mode: 'single', subMenuCollapse: false }, layout: { enableMobileAdaptation: true } }))
    window.addEventListener('mrr:app-ready', () => {
      requestAnimationFrame(() => {
        const sidebar = document.querySelector('#mrr-sidebar')
        const title = sidebar?.querySelector('.title-text')
        document.documentElement.dataset.firstSidebar = JSON.stringify({
          visible: sidebar && getComputedStyle(sidebar).visibility,
          titleVisible: title && getComputedStyle(title).visibility,
          // eslint-disable-next-line unicorn/prefer-dom-node-text-content -- visible text is the contract
          text: (sidebar as HTMLElement | null)?.innerText,
        })
      })
    })
  })
  await page.goto('/')
  await expect(page.locator('html')).toHaveAttribute('data-first-sidebar', /visible/)
  const snapshot = JSON.parse((await page.locator('html').getAttribute('data-first-sidebar'))!)
  expect(snapshot.visible).toBe('visible')
  expect(snapshot.titleVisible).toBe('visible')
  expect(snapshot.text).toContain('系统')
  expect(snapshot.text).toContain('业务')
  await page.screenshot({ path: info.outputPath('desktop-ready.png') })
})
