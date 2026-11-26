import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('父菜单悬停才显示引导线，快速反向展开收起保持连续', async ({ page }, testInfo) => {
  await isolateLocalVisualSettings(page)
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { colorScheme: 'light', enableMotion: true },
    menu: { mode: 'single', subMenuCollapse: false },
    layout: { enableMobileAdaptation: true },
  })))
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/patients')
  const nav = page.locator('#mrr-sub-navigation')
  const group = nav.getByRole('button', { name: '业务', exact: true })
  await expect(group).toHaveAttribute('aria-expanded', 'true')
  const id = await group.getAttribute('aria-controls')
  expect(id).toBeTruthy()
  const branch = nav.locator(`[id="${id}"]`)
  const leaf = branch.locator('a.menu-item-container').first()
  const guideOpacity = () => branch.evaluate(el => getComputedStyle(el, '::before').opacity)
  await page.mouse.move(900, 500)
  await expect.poll(guideOpacity).toBe('0')
  await group.hover()
  await expect.poll(guideOpacity).toBe('1')
  await nav.screenshot({ path: testInfo.outputPath('parent-hover.png'), animations: 'disabled' })
  await leaf.hover()
  await expect.poll(guideOpacity).toBe('0')
  await nav.screenshot({ path: testInfo.outputPath('leaf-hover.png'), animations: 'disabled' })
  await page.mouse.move(900, 500)
  const samples = await group.evaluate(async (button) => {
    const panel = document.getElementById(button.getAttribute('aria-controls')!)!
    const height = () => panel.getBoundingClientRect().height
    const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))
    const full = height()
    button.click()
    await wait(65)
    const closing = height()
    button.click()
    await Promise.resolve()
    await Promise.resolve()
    const reversal = height()
    const opening: number[] = []
    const start = performance.now()
    while (performance.now() - start < 300) {
      await new Promise(requestAnimationFrame)
      opening.push(height())
    }
    return { full, closing, reversal, opening, end: height() }
  })
  expect(samples.full).toBeGreaterThan(200)
  expect(samples.closing).toBeGreaterThan(0)
  expect(samples.closing).toBeLessThan(samples.full - 1)
  expect(Math.abs(samples.reversal - samples.closing)).toBeLessThanOrEqual(2)
  expect(samples.opening.every((h, i) => i === 0 || h >= samples.opening[i - 1] - 1)).toBe(true)
  expect(Math.abs(samples.full - samples.end)).toBeLessThanOrEqual(1)

  await group.click()
  await expect(group).toHaveAttribute('aria-expanded', 'false')
  await expect(branch).toHaveAttribute('aria-hidden', 'true')
  await expect(leaf).toHaveAttribute('tabindex', '-1')
  await expect.poll(() => branch.evaluate(el => el.getBoundingClientRect().height)).toBe(0)
  await expect(branch).toHaveCSS('visibility', 'hidden')
  await group.click()
  await expect(leaf).toHaveAttribute('tabindex', '0')
  await expect.poll(() => branch.evaluate(el => el.getBoundingClientRect().height)).toBe(samples.full)

  await page.emulateMedia({ reducedMotion: 'reduce' })
  await group.click()
  await expect.poll(() => branch.evaluate(el => getComputedStyle(el).transitionDuration.split(',').every(value => Number.parseFloat(value) === 0))).toBe(true)
  await expect(branch).toHaveCSS('visibility', 'hidden')
  await expect.poll(() => branch.evaluate(el => el.getBoundingClientRect().height)).toBe(0)
  await group.press('Enter')
  await expect(leaf).toHaveAttribute('tabindex', '0')
  await expect(branch).toHaveCSS('visibility', 'visible')
})
