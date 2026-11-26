import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const palette of ['mrr', 'gr8r']) {
  for (const theme of ['light', 'dark']) {
    test(`侧栏悬停和左侧选中指示线 ${palette}/${theme}`, async ({ page }, testInfo) => {
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
        const scale = s.transform === 'none' ? 1 : Math.abs(new DOMMatrixReadOnly(s.transform).d)
        return { height: Number.parseFloat(s.height) * scale, width: s.width, opacity: s.opacity, left: s.left, background: s.backgroundColor, duration: s.transitionDuration }
      }
      await expect.poll(() => current.evaluate(rail)).toMatchObject({ height: 22, width: '3px', opacity: '1', left: '3px' })
      await expect(current).toHaveCSS('border-left-color', 'rgba(0, 0, 0, 0)')
      expect((await current.evaluate(rail)).duration).toContain('0.18s')
      const icon = target.locator('.menu-item-container-icon')
      const before = await icon.boundingBox()
      // The rail remains inside the link, separate from the branch guide and icon.
      const geometry = await current.evaluate((el) => {
        const r = el.getBoundingClientRect()
        const branch = el.closest('.sub-menu')!
        const guide = branch.getBoundingClientRect().left + Number.parseFloat(getComputedStyle(branch, '::before').left) + 1
        const rail = getComputedStyle(el, '::after')
        const railLeft = r.left + Number.parseFloat(getComputedStyle(el).borderLeftWidth) + Number.parseFloat(rail.left)
        const iconLeft = el.querySelector('.menu-item-container-icon')!.getBoundingClientRect().left
        return { guideGap: railLeft - guide, iconGap: iconLeft - railLeft - Number.parseFloat(rail.width) }
      })
      expect(geometry.guideGap).toBeGreaterThanOrEqual(11)
      expect(geometry.iconGap).toBeGreaterThanOrEqual(8)
      const idle = await target.evaluate(el => getComputedStyle(el).backgroundColor)
      await target.hover()
      await expect.poll(() => target.evaluate(el => getComputedStyle(el).backgroundColor)).not.toBe(idle)
      await expect(icon).toHaveCSS('transform', 'none')
      expect(await icon.boundingBox()).toEqual(before)
      await expect.poll(() => target.evaluate(rail)).toMatchObject({ height: 0, opacity: '0' })
      await nav.screenshot({ path: testInfo.outputPath('hover.png'), animations: 'disabled' })
      // A canceled press must never flash a selection rail.
      await page.mouse.down()
      await expect.poll(() => target.evaluate(rail)).toMatchObject({ height: 0, opacity: '0' })
      await page.mouse.move(800, 400)
      await page.mouse.up()
      await expect(current).toHaveAttribute('aria-current', 'page')
      await target.hover()
      await page.mouse.down()
      await expect.poll(() => target.evaluate(rail)).toMatchObject({ height: 0, opacity: '0' })
      // Sample real animation frames from pointer release through route activation.
      await nav.evaluate((el) => {
        const links = [...el.querySelectorAll('a.menu-item-container')]
        const target = links.find(a => a.textContent?.trim() === '记录管理')!
        const frames: { height: number, active: boolean, visible: number }[] = []
        const start = performance.now()
        function sample() {
          const style = getComputedStyle(target, '::after')
          const scale = Math.abs(new DOMMatrixReadOnly(style.transform).d)
          frames.push({
            height: Number.parseFloat(style.height) * scale,
            active: target.getAttribute('aria-current') === 'page',
            visible: links.filter(a => !a.closest('[aria-hidden=true]') && Number.parseFloat(getComputedStyle(a, '::after').opacity) > 0.01).length,
          })
          if (performance.now() - start < 1200) { requestAnimationFrame(sample) }
          else { el.setAttribute('data-rail-test-frames', JSON.stringify(frames)) }
        }
        requestAnimationFrame(sample)
      })
      await page.mouse.up()
      await expect(page).toHaveURL(/\/records$/)
      await expect(target).toHaveAttribute('aria-current', 'page')
      await expect.poll(() => target.evaluate(rail)).toMatchObject({ height: 22, opacity: '1' })
      await expect(target).toHaveCSS('border-left-color', 'rgba(0, 0, 0, 0)')
      await expect.poll(() => current.evaluate(rail)).toMatchObject({ height: 0, opacity: '0' })
      await expect(nav).toHaveAttribute('data-rail-test-frames', /./)
      const frames = JSON.parse((await nav.getAttribute('data-rail-test-frames'))!) as { height: number, active: boolean, visible: number }[]
      const activeFrames = frames.filter(frame => frame.active)
      expect(frames.every(frame => frame.visible <= 1)).toBe(true)
      expect(activeFrames.some(frame => frame.height > 0.5 && frame.height < 21.5)).toBe(true)
      expect(activeFrames.every((frame, index) => index === 0 || frame.height >= activeFrames[index - 1].height - 0.1)).toBe(true)
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
