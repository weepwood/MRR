import { expect, test } from '@playwright/test'

// 不写入移动适配选项，验证项目默认值而非测试专用覆盖值。
for (const theme of ['mrr', 'gr8r'] as const) {
  for (const scheme of ['light', 'dark'] as const) {
    test(`${theme}/${scheme} 默认移动适配释放主内容区且保留桌面导航`, async ({ page }, testInfo) => {
      await page.addInitScript(({ themePalette, colorScheme }) => {
        localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { themePalette, colorScheme, enableMotion: false } }))
      }, { themePalette: theme, colorScheme: scheme })
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto('/settings?section=documentation')
      const list = page.locator('.documentation-list')
      await expect(list).toBeVisible()
      await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', theme)
      if (scheme === 'dark') {
        await expect(page.locator('html')).toHaveClass(/\bdark\b/)
      }
      else {
        await expect(page.locator('html')).not.toHaveClass(/\bdark\b/)
      }
      const layout = page.locator('body[data-mode]')
      const main = page.locator('.main-container')
      for (const width of [1440, 1100, 760, 390]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(layout).toHaveAttribute('data-mode', width < 1024 ? 'mobile' : 'pc')
        await expect.poll(() => main.evaluate(el => el.clientWidth)).toBeGreaterThan(width < 1024 ? width - 2 : 700)
        await expect.poll(() => list.evaluate(el => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(2)
        if (width === 390) {
          await expect.poll(() => list.evaluate(el => el.clientWidth)).toBeGreaterThan(280)
        }
        await page.screenshot({ path: testInfo.outputPath(`documentation-${width}.png`), animations: 'disabled' })
      }
      const toggle = page.getByRole('button', { name: '切换侧栏导航' })
      await toggle.focus()
      await toggle.press('Enter')
      await expect(toggle).toHaveAttribute('aria-expanded', 'true')
      const sidebar = page.locator('#mrr-sidebar')
      await expect(sidebar).toBeVisible()
      await sidebar.locator('button, a').first().focus()
      await page.keyboard.press('Escape')
      await expect(sidebar).toBeHidden()
      await expect(toggle).toBeFocused()
      await page.goto('/')
      const lookup = page.locator('.home-lookup')
      await expect(lookup).toBeVisible()
      await expect.poll(() => lookup.evaluate(el => el.clientWidth)).toBeGreaterThan(320)
      await page.screenshot({ path: testInfo.outputPath('home-390.png'), animations: 'disabled' })
    })
  }
}

test('显式关闭移动适配仍保留桌面模式', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ layout: { enableMobileAdaptation: false } })))
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await expect(page.locator('body[data-mode]')).toHaveAttribute('data-mode', 'pc')
})

for (const reducedMotion of ['no-preference', 'reduce'] as const) {
  test(`${reducedMotion} 侧栏快速反向切换后状态与焦点一致`, async ({ page }) => {
    await page.emulateMedia({ reducedMotion })
    await page.setViewportSize({ width: 390, height: 844 })
    await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { enableMotion: true } })))
    await page.goto('/')
    const toggle = page.getByRole('button', { name: '切换侧栏导航' })
    const sidebar = page.locator('#mrr-sidebar')
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await toggle.focus()
    await toggle.press('Enter')
    await toggle.press('Enter')
    await toggle.press('Enter')
    await expect(toggle).toHaveAttribute('aria-expanded', 'true')
    await expect(sidebar).toBeVisible()
    await sidebar.locator('button, a').first().focus()
    await page.keyboard.press('Escape')
    await expect(sidebar).toBeHidden()
    await expect(toggle).toBeFocused()
    await page.setViewportSize({ width: 1440, height: 900 })
    await expect(page.locator('body')).toHaveAttribute('data-mode', 'pc')
    await expect(sidebar).toBeVisible()
  })
}
