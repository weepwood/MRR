import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('长按实时FLIP重排、停留防振荡、反向与取消不写入偏好', async ({ page }, info) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { enableMotion: true, colorScheme: 'light' }, layout: { enableMobileAdaptation: true } }))
  })
  await page.goto('/')
  const grid = page.locator('.dashboard-grid')
  const records = grid.locator('[data-widget-id="records"]')
  const stats = grid.locator('[data-widget-id="statistics"]')
  const archive = grid.locator('[data-widget-id="archive"]')
  const order = () => grid.locator('article').evaluateAll(els => els.map(el => (el as HTMLElement).dataset.widgetId))
  const stored = () => page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('MRR-ADMIN:dashboard-widgets:') && !key.endsWith(':appearance')).sort().map(key => [key, localStorage.getItem(key)]))
  const point = async (card: typeof records) => {
    const rect = (await card.boundingBox())!
    return { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 }
  }
  await expect(records).toBeVisible()
  await expect.poll(() => grid.evaluate((el) => {
    for (let node: Element | null = el; node; node = node.parentElement) {
      if (node.getAnimations().some(a => a.pending || a.playState === 'running')) { return false }
    }
    return true
  })).toBe(true)
  const before = await order()
  const persisted = await stored()
  const source = await point(records)
  const target = await point(stats)
  const pinned = await point(archive)
  await page.mouse.move(source.x, source.y)
  await page.mouse.down()
  await expect(records).toHaveClass(/is-dragging/)
  await expect(page.locator('.widget-drag-preview')).toBeVisible()
  await page.screenshot({ path: info.outputPath('01-picked-up.png') })
  await grid.evaluate((el) => {
    const trace: { elapsed: number, order: string[], transforms: string[] }[] = []
    const started = performance.now()
    ;(window as unknown as { __dragTrace: typeof trace }).__dragTrace = trace
    function sample(now: number) {
      trace.push({ elapsed: now - started, order: [...el.querySelectorAll<HTMLElement>(':scope > article')].map(e => e.dataset.widgetId!), transforms: [...el.querySelectorAll(':scope > article')].map(e => getComputedStyle(e).transform) })
      if (now - started < 600) { requestAnimationFrame(sample) }
    }
    requestAnimationFrame(sample)
  })
  await page.mouse.move(target.x, target.y)
  await expect.poll(async () => (await order()).indexOf('records')).toBeGreaterThan(before.indexOf('records'))
  const changed = await order()
  expect(await stored()).toEqual(persisted)
  await page.screenshot({ path: info.outputPath('02-reordered-before-release.png') })
  await page.waitForTimeout(650)
  const trace = await page.evaluate(() => (window as unknown as { __dragTrace: { elapsed: number, order: string[], transforms: string[] }[] }).__dragTrace)
  expect(trace.some(f => f.order.join() !== before.join() && f.transforms.some(t => t !== 'none' && t !== 'matrix(1, 0, 0, 1, 0, 0)'))).toBe(true)
  expect(await order()).toEqual(changed)
  // The former source slot now contains a sibling. Moving back restores the draft.
  await page.mouse.move(source.x, source.y)
  await expect.poll(order).toEqual(before)
  expect(await stored()).toEqual(persisted)
  await page.mouse.move(target.x, target.y)
  await expect.poll(order).toEqual(changed)
  await page.mouse.move(pinned.x, pinned.y)
  await expect(archive).toHaveClass(/is-drop-invalid/)
  await page.mouse.up()
  await expect(records).not.toHaveClass(/is-dragging/)
  await expect.poll(order).toEqual(before)
  expect(await stored()).toEqual(persisted)
  await expect(page).toHaveURL(/\/$/)

  await page.waitForTimeout(220)
  const start = await point(records)
  const end = await point(stats)
  await page.mouse.move(start.x, start.y)
  await page.mouse.down()
  await expect(records).toHaveClass(/is-dragging/)
  await page.mouse.move(end.x, end.y)
  await expect.poll(order).not.toEqual(before)
  await page.keyboard.press('Escape')
  await page.mouse.up()
  await expect.poll(order).toEqual(before)
  expect(await stored()).toEqual(persisted)
  await expect(page.locator('.widget-drag-preview')).toHaveCount(0)
  await expect(page).toHaveURL(/\/$/)
  await page.waitForTimeout(220)
  const start2 = await point(records)
  const end2 = await point(stats)
  await page.mouse.move(start2.x, start2.y)
  await page.mouse.down()
  await expect(records).toHaveClass(/is-dragging/)
  await page.mouse.move(end2.x, end2.y)
  await expect.poll(order).not.toEqual(before)
  const finalDraft = await order()
  await page.mouse.up()
  await expect.poll(order).toEqual(finalDraft)
  await expect.poll(stored).not.toEqual(persisted)
  await expect(page.locator('.widget-drag-preview')).toHaveCount(0)
  await page.reload()
  await expect.poll(order).toEqual(finalDraft)
})
