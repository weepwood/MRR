import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const path of ['/logs', '/audit-images']) {
  test(`${path} 高级筛选连续反向、长帧、焦点与降级`, async ({ page }, info) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { enableMotion: true, colorScheme: 'light' },
      menu: { mode: 'single' },
      layout: { enableMobileAdaptation: true },
    })))
    await page.emulateMedia({ reducedMotion: 'no-preference' })
    await page.goto(path)
    await page.setViewportSize({ width: 375, height: 900 })
    const trigger = page.getByRole('button', { name: /高级筛选/ })
    const viewport = page.locator('.mrr-filter-bar__advanced-viewport')
    const input = page.getByLabel('客户端 IP', { exact: true })
    await expect(trigger).toBeVisible()
    await expect(input).toBeHidden()
    await trigger.click()
    await expect.poll(() => viewport.evaluate(el => el.style.height)).toBe('')
    await input.fill('192.0.2.42')
    await trigger.focus()
    const observations = await trigger.evaluate(async (node) => {
      const button = node as HTMLButtonElement
      const viewport = document.querySelector<HTMLElement>('.mrr-filter-bar__advanced-viewport')!
      const read = () => viewport.getBoundingClientRect().height
      const points = []
      for (let i = 0; i < 10; i++) {
        const before = read()
        button.click()
        const after = read()
        points.push({ before, after, error: Math.abs(before - after) })
        await new Promise(resolve => setTimeout(resolve, 37))
      }
      return points
    })
    expect(Math.max(...observations.map(p => p.error))).toBeLessThanOrEqual(1)
    await expect(trigger).toHaveAttribute('aria-expanded', 'true')
    await expect.poll(() => viewport.evaluate(el => el.style.height)).toBe('')
    await expect(input).toHaveValue('192.0.2.42')
    await expect(trigger).toBeFocused()
    await trigger.click()
    await page.evaluate(async () => {
      await new Promise(resolve => requestAnimationFrame(resolve))
      const deadline = performance.now() + 120
      while (performance.now() < deadline) { /* deliberately block one frame */ }
    })
    await expect(viewport).toBeHidden()
    await expect.poll(() => viewport.evaluate(el => el.style.height)).toBe('')
    await trigger.click()
    await expect.poll(() => viewport.evaluate(el => el.style.height)).toBe('')
    await expect(input).toHaveValue('192.0.2.42')
    await info.attach('interruption.json', { body: JSON.stringify(observations), contentType: 'application/json' })
    await page.screenshot({ path: info.outputPath('expanded.png') })
    await trigger.click()
    await expect(input).toBeHidden()
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await expect(viewport).toBeHidden()
    await expect.poll(() => viewport.evaluate(el => el.style.height)).toBe('')
    await trigger.click()
    await expect(input).toBeVisible()
    expect(await viewport.evaluate(el => el.style.height)).toBe('')
    await expect(input).toHaveValue('192.0.2.42')
    await trigger.click()
    await expect(input).toBeHidden()
    await expect(viewport).toBeHidden()
    await expect(trigger).toBeFocused()
  })
}
