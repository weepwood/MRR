import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const palette of ['mrr', 'gr8r']) {
  for (const theme of ['light', 'dark']) {
    test(`侧栏悬停和右侧选中指示线 ${palette}/${theme}`, async ({ page }, testInfo) => {
      await isolateLocalVisualSettings(page)
      await page.emulateMedia({ reducedMotion: 'no-preference' })
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { colorScheme: theme, themePalette: palette, enableMotion: true },
        menu: { mode: 'single', subMenuCollapse: false },
        layout: { enableMobileAdaptation: true },
      })), { palette, theme })
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto('/patients')
      const nav = page.locator('#mrr-sub-navigation')
      const current = nav.getByRole('link', { name: '患者管理', exact: true })
      const target = nav.getByRole('link', { name: '记录管理', exact: true })
      await expect(current).toHaveAttribute('aria-current', 'page')
      const rail = (el: Element) => {
        const s = getComputedStyle(el, '::after')
        return { height: Number.parseFloat(s.height), width: s.width, opacity: s.opacity, right: s.right, background: s.backgroundColor, duration: s.transitionDuration }
      }
      await expect.poll(() => current.evaluate(rail)).toMatchObject({ height: 22, width: '3px', opacity: '1', right: '3px' })
      await expect(current).toHaveCSS('border-right-color', 'rgba(0, 0, 0, 0)')
      expect((await current.evaluate(rail)).duration).toContain('0.18s')
      const icon = target.locator('.menu-item-container-icon')
      const before = await icon.boundingBox()
      const idle = await target.evaluate(el => getComputedStyle(el).backgroundColor)
      await target.hover()
      await expect.poll(() => target.evaluate(el => getComputedStyle(el).backgroundColor)).not.toBe(idle)
      await expect(icon).toHaveCSS('transform', 'none')
      expect(await icon.boundingBox()).toEqual(before)
      await expect.poll(() => target.evaluate(rail)).toMatchObject({ height: 0, opacity: '0' })
      await nav.screenshot({ path: testInfo.outputPath('hover.png'), animations: 'disabled' })
      await page.mouse.down()
      await expect.poll(() => target.evaluate(rail)).toMatchObject({ height: 10, opacity: '0.65' })
      await page.mouse.up()
      await expect(page).toHaveURL(/\/records$/)
      await expect(target).toHaveAttribute('aria-current', 'page')
      await expect.poll(() => target.evaluate(rail)).toMatchObject({ height: 22, opacity: '1' })
      await expect(target).toHaveCSS('border-right-color', 'rgba(0, 0, 0, 0)')
      await expect.poll(() => current.evaluate(rail)).toMatchObject({ height: 0, opacity: '0' })
      await page.mouse.move(800, 400)
      await nav.screenshot({ path: testInfo.outputPath('selected.png'), animations: 'disabled' })
      await page.emulateMedia({ reducedMotion: 'reduce' })
      await current.focus()
      await current.press('Enter')
      await expect(page).toHaveURL(/\/patients$/)
      await expect.poll(() => current.evaluate(rail)).toMatchObject({ height: 22, opacity: '1', duration: '0s' })
      await expect(current).toHaveCSS('outline-style', 'solid')
    })
  }
}
