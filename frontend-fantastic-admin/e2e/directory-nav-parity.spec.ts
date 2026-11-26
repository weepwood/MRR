import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const palette of ['mrr', 'gr8r']) {
  for (const colorScheme of ['light', 'dark']) {
    test(`目录与业务子菜单样式同源 ${palette}/${colorScheme}`, async ({ page }, testInfo) => {
      await isolateLocalVisualSettings(page)
      await page.addInitScript(({ palette, colorScheme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { themePalette: palette, colorScheme, enableMotion: true },
        menu: { mode: 'single', subMenuCollapse: false },
        layout: { enableMobileAdaptation: true },
      })), { palette, colorScheme })
      await page.emulateMedia({ reducedMotion: 'no-preference' })
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto('/patients')
      const leaf = page.locator('#mrr-sub-navigation a[aria-current="page"]')
      await expect(leaf).toBeVisible()
      const style = (el: Element) => {
        const s = getComputedStyle(el)
        const rail = getComputedStyle(el, '::after')
        const icon = el.querySelector('.menu-item-container-icon, .mrr-directory-nav-icon')!
        return {
          font: s.fontSize,
          weight: s.fontWeight,
          radius: s.borderRadius,
          background: s.backgroundColor,
          color: s.color,
          border: s.borderLeftColor,
          padding: s.padding,
          height: Math.round(el.getBoundingClientRect().height * 10) / 10,
          iconWidth: icon.getBoundingClientRect().width,
          railWidth: rail.width,
          railHeight: rail.height,
          railLeft: rail.left,
          railColor: rail.backgroundColor,
          duration: rail.transitionDuration,
        }
      }
      const expected = await leaf.evaluate(style)
      for (const [url, selector, active, next] of [
        ['/settings?section=system', '.settings-nav', '[aria-current=true]', '登录与支持'],
        ['/ui-components', '.gallery-categories', '[aria-pressed=true]', '表单与选择'],
      ]) {
        await page.goto(url)
        const nav = page.locator(selector)
        const selected = nav.locator(active)
        await expect(selected).toHaveCount(1)
        const actual = await selected.evaluate(style)
        expect(actual).toMatchObject(expected)
        const target = nav.getByRole('button', { name: new RegExp(next) })
        await target.scrollIntoViewIfNeeded()
        const iconGeometry = (el: Element) => {
          const r = el.getBoundingClientRect()
          const icon = el.querySelector('.mrr-directory-nav-icon')!.getBoundingClientRect()
          return [icon.left - r.left, icon.top - r.top, icon.width, icon.height]
        }
        const before = await target.evaluate(iconGeometry)
        await target.hover()
        await expect.poll(() => target.evaluate(el => getComputedStyle(el, '::after').opacity)).toBe('0')
        const after = await target.evaluate(iconGeometry)
        expect(after.every((value, i) => Math.abs(value - before[i]) <= 0.5)).toBe(true)
        await target.click()
        await expect(target).toHaveAttribute(url.startsWith('/settings') ? 'aria-current' : 'aria-pressed', 'true')
        await expect.poll(() => nav.evaluate(el => [...el.querySelectorAll('.mrr-nav-leaf')].filter(node => getComputedStyle(node, '::after').opacity === '1').length)).toBe(1)
        await expect.poll(() => target.evaluate(el => Math.abs(new DOMMatrixReadOnly(getComputedStyle(el, '::after').transform).d))).toBe(1)
        await page.mouse.move(1000, 500)
        await nav.screenshot({ path: testInfo.outputPath(url.startsWith('/settings') ? 'settings.png' : 'gallery.png'), animations: 'disabled' })
        await page.emulateMedia({ reducedMotion: 'reduce' })
        await expect.poll(() => target.evaluate(el => Number.parseFloat(getComputedStyle(el, '::after').transitionDuration))).toBe(0)
        await page.emulateMedia({ reducedMotion: 'no-preference' })
        await page.setViewportSize({ width: 375, height: 900 })
        if (url.startsWith('/settings')) {
          await expect(page.getByRole('combobox', { name: '设置分类' })).toBeVisible()
        }
        else {
          await expect.poll(() => nav.evaluate(el => el.scrollWidth <= el.clientWidth + 1 && [...el.querySelectorAll('.mrr-directory-nav-label')].every(node => node.scrollWidth <= node.clientWidth + 1))).toBe(true)
        }
        await page.setViewportSize({ width: 1440, height: 900 })
      }
    })
  }
}
