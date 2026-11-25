import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'

const evidence = process.env.MRR_CONTEXT_EVIDENCE
const capture = process.env.MRR_CONTEXT_CAPTURE === 'true'

for (const theme of ['light', 'dark'] as const) {
  test(`${theme} 标签页右键菜单定位、禁用和关闭`, async ({ page }) => {
    await page.addInitScript(theme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, enableMotion: false }, menu: { mode: 'single' }, tabbar: { enable: true } })), theme)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/users')
    const tabs = page.locator('.tabbar .tab')
    const first = tabs.filter({ hasText: '用户管理' })
    await expect(first).toBeVisible()
    await first.locator('.tab-content').click({ button: 'right', position: { x: 30, y: 15 } })
    const menu = page.getByRole('menu').filter({ hasText: '关闭其他标签页' }).filter({ visible: true }).last()
    await expect(menu).toBeVisible()
    await expect(menu.getByRole('menuitem', { name: '关闭标签页', exact: true })).toHaveAttribute('aria-disabled', 'true')
    await page.keyboard.press('Escape')
    await expect(menu).toBeHidden()
    const group = page.getByRole('button', { name: '业务', exact: true })
    const records = page.locator('.sub-sidebar-container').getByRole('link', { name: '记录管理', exact: true })
    if (!await records.isVisible()) { await group.click() }
    await records.click()
    await expect(page).toHaveURL(/\/records$/)
    await expect(first).toBeVisible()
    const content = first.locator('.tab-content')
    const box = await content.boundingBox()
    await content.click({ button: 'right', position: { x: 30, y: 15 } })
    await expect(menu).toBeVisible()
    const surface = capture ? menu : page.locator('.el-popper.mrr-context-menu').filter({ visible: true })
    await expect.poll(() => surface.evaluate(el => el.getAnimations().every(a => !a.pending && a.playState !== 'running'))).toBe(true)
    const data = await surface.evaluate((el) => {
      const r = el.getBoundingClientRect()
      return { x: r.x, y: r.y, width: r.width, height: r.height, items: [...el.querySelectorAll('[role="menuitem"]')].map((node) => {
        const b = node.getBoundingClientRect()
        const s = getComputedStyle(node)
        return { x: b.x - r.x, y: b.y - r.y, width: b.width, height: b.height, font: s.fontSize, radius: s.borderRadius }
      }) }
    })
    expect(Math.abs(data.x - box!.x - 30)).toBeLessThanOrEqual(8)
    expect(Math.abs(data.y - box!.y - 15)).toBeLessThanOrEqual(8)
    if (evidence) {
      await fs.mkdir(evidence, { recursive: true })
      const key = path.join(evidence, theme)
      const png = await surface.screenshot({ animations: 'disabled' })
      if (capture) {
        await fs.writeFile(`${key}.json`, JSON.stringify(data, null, 2))
        await fs.writeFile(`${key}-before.png`, png)
      }
      else {
        const old = JSON.parse(await fs.readFile(`${key}.json`, 'utf8')) as typeof data
        await fs.writeFile(`${key}-after.json`, JSON.stringify(data, null, 2))
        await fs.writeFile(`${key}-after.png`, png)
        for (const k of ['x', 'y', 'width', 'height'] as const) { expect(Math.abs(data[k] - old[k]), k).toBeLessThanOrEqual(1) }
        expect(data.items.length).toBe(old.items.length)
        for (const [i, item] of data.items.entries()) {
          for (const k of ['x', 'y', 'width', 'height'] as const) { expect(Math.abs(item[k] - old.items[i][k]), k).toBeLessThanOrEqual(1) }
          for (const k of ['font', 'radius'] as const) { expect(item[k]).toBe(old.items[i][k]) }
        }
      }
    }
    await expect(menu.getByRole('menuitem', { name: '重新加载', exact: true })).toHaveAttribute('aria-disabled', 'true')
    await menu.getByRole('menuitem', { name: '关闭标签页', exact: true }).click()
    await expect(first).toHaveCount(0)
    await expect(menu).toBeHidden()
    await expect(page).toHaveURL(/\/records$/)
    if (!capture) {
      const remaining = tabs.filter({ hasText: '记录管理' })
      await remaining.focus()
      await remaining.press('Shift+F10')
      await expect(menu).toBeVisible()
      await page.keyboard.press('Escape')
      await expect(menu).toBeHidden()
      await expect(remaining).toBeFocused()
    }
  })
}
