import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const touch of [false, true]) {
  test.describe(touch ? 'touch' : 'mouse', () => {
    test.use({ hasTouch: touch })
    test('影像工具触控尺寸与原图阅读区域不遮挡', async ({ page }, info) => {
      await isolateLocalVisualSettings(page)
      await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { enableMotion: false },
        layout: { enableMobileAdaptation: true },
      })))
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto('/archive-workbench')
      const search = page.getByRole('textbox', { name: '病案查询值' })
      await search.fill('26070000')
      await search.press('Enter')
      await page.getByRole('group', { name: '有效影像列表' }).getByRole('button').first().click()
      await page.getByRole('button', { name: '专注原图' }).click()
      const image = page.getByAltText('待核对影像')
      await expect.poll(() => image.evaluate((el: HTMLImageElement) => el.complete && el.naturalWidth > 0)).toBe(true)
      const source = await image.getAttribute('src')
      const bar = page.getByRole('group', { name: '原图显示控制' })
      const region = page.getByRole('region', { name: '原图滚动查看区' })
      for (const width of [1440, 1100, 760, 390]) {
        await page.setViewportSize({ width, height: 900 })
        await expect.poll(() => bar.evaluate(el => [...el.querySelectorAll('button')].every(b => b.offsetHeight >= (matchMedia('(pointer: coarse)').matches ? 44 : 24)))).toBe(true)
        expect(await page.evaluate(() => matchMedia('(pointer: coarse)').matches)).toBe(touch)
        const a = (await bar.boundingBox())!; const b = (await region.boundingBox())!
        expect(a.y + a.height).toBeLessThanOrEqual(b.y + 1)
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width + 1)
        expect(b.height).toBeGreaterThanOrEqual(260)
        await expect(image).toHaveAttribute('src', source!)
      }
      await page.screenshot({ path: info.outputPath('reader-390.png'), fullPage: true })
    })
  })
}
