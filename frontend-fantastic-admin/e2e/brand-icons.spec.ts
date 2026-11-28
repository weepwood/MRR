import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('站点图标与主屏幕资源正确加载', async ({ page, request }, info) => {
  await page.goto('/')
  const manifest = await (await request.get('/manifest.json')).json()
  const icons = [...manifest.icons, { src: '/pwa/apple-touch-icon.png', sizes: '180x180' }]
  for (const icon of icons) {
    const response = await request.get(icon.src)
    expect(response.ok()).toBe(true)
    expect(response.headers()['content-type']).toMatch(/image\//)
    const image = await page.evaluate(async (src) => {
      const img = new Image()
      img.src = src
      await img.decode()
      return { width: img.naturalWidth, height: img.naturalHeight }
    }, icon.src)
    expect(image.width).toBeGreaterThan(0)
    expect(image.width).toBe(image.height)
    if (icon.sizes !== 'any') { expect(image.width).toBe(Number.parseInt(icon.sizes)) }
  }
  await expect(page.locator('link[rel="icon"][type="image/svg+xml"]')).toHaveAttribute('href', '/favicon.svg')
  await expect(page.locator('link[rel="apple-touch-icon"]')).toHaveAttribute('href', '/pwa/apple-touch-icon.png')
  const ico = await request.get('/favicon.ico')
  expect(ico.ok()).toBe(true)
  const bytes = await ico.body()
  expect(bytes.readUInt16LE(2)).toBe(1)
  expect(bytes.readUInt16LE(4)).toBe(3)
  expect([bytes[6], bytes[22], bytes[38]]).toEqual([16, 32, 48])
  await page.screenshot({ path: info.outputPath('home.png') })
})

for (const [palette, theme] of [['mrr', 'light'], ['mrr', 'dark'], ['gr8r', 'light'], ['gr8r', 'dark']] as const) {
  test(`${palette}/${theme} 侧栏品牌图标清晰且折叠操作保留`, async ({ page }, info) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { themePalette: palette, colorScheme: theme, enableMotion: false }, menu: { mode: 'single' }, layout: { enableMobileAdaptation: true } })), { palette, theme })
    await page.goto('/')
    const mark = page.locator('.logo-mark').first()
    await expect(mark).toBeVisible()
    await expect(mark).toHaveCSS('width', '22px')
    const header = page.locator('.logo-container--collapsible').first()
    const title = header.locator('.title-text')
    await expect(title).toHaveCSS('font-size', '13px')
    await expect(title).toHaveCSS('font-weight', '600')
    const geometry = await header.evaluate((el) => {
      const image = el.querySelector('img')!.getBoundingClientRect()
      const text = el.querySelector<HTMLElement>('.title-text')!
      const label = text.getBoundingClientRect()
      return {
        height: el.getBoundingClientRect().height,
        centerDelta: label.y + label.height / 2 - image.y - image.height / 2,
        gap: label.x - image.right,
        clipped: text.scrollWidth > text.clientWidth + 1,
      }
    })
    expect(geometry.height).toBe(48)
    expect(geometry.centerDelta).toBeGreaterThanOrEqual(0.5)
    expect(geometry.centerDelta).toBeLessThanOrEqual(1.5)
    expect(geometry.gap).toBeGreaterThanOrEqual(4)
    expect(geometry.gap).toBeLessThanOrEqual(12)
    expect(geometry.clipped).toBe(false)
    await expect.poll(() => mark.evaluate((el: HTMLImageElement) => el.complete && el.naturalWidth > 0)).toBe(true)
    // Actual loaded asset must follow the application theme, not the OS theme.
    const source = await mark.evaluate(async (el: HTMLImageElement) => (await fetch(el.currentSrc)).text())
    expect(source).toContain(theme === 'dark' ? '#7097E0' : '#4775D2')
    expect(source).toContain(theme === 'dark' ? '#C5D7F7' : '#A3BDF0')
    expect(source).toContain('M16 5h21l17 17')
    expect(source).not.toContain('M5 34.5h54')
    const toggle = page.getByRole('button', { name: '收起侧边栏' })
    await expect(toggle).toBeVisible()
    await page.screenshot({ path: info.outputPath('sidebar.png') })
    await toggle.click()
    await expect(page.getByRole('button', { name: '展开侧边栏' })).toBeVisible()
    await page.getByRole('button', { name: '展开侧边栏' }).click()
    await expect(toggle).toBeVisible()
  })
}
