import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => { await isolateLocalVisualSettings(page) })

const evidence = process.env.MRR_MENU_EVIDENCE
const capture = process.env.MRR_MENU_CAPTURE === 'true'
if (capture && !evidence) { throw new Error('Capture requires evidence directory') }

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`${palette}/${theme} 账户菜单与关闭保护`, async ({ page }) => {
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, themePalette: palette, enableMotion: false }, menu: { mode: 'single' }, layout: { enableMobileAdaptation: true } })), { palette, theme })
      for (const width of [1440, 760]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto('/users')
        await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
        if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
        if (width < 768) { await page.getByRole('button', { name: '切换侧栏导航' }).click() }
        const trigger = page.getByRole('button', { name: /账号菜单：/ }).filter({ visible: true }).first()
        await trigger.focus()
        await trigger.press('Enter')
        const menu = page.getByRole('menu').filter({ hasText: '个人设置' }).filter({ visible: true }).last()
        await expect(menu).toBeVisible()
        const surface = capture ? menu : page.locator('.el-popper.mrr-account-menu').filter({ visible: true })
        await expect.poll(() => surface.evaluate((el) => {
          for (let n: Element | null = el; n && n !== document.body; n = n.parentElement) {
            if (n.getAnimations().some(a => a.pending || a.playState === 'running')) { return false }
          }
          return true
        })).toBe(true)
        const data = await surface.evaluate((el) => {
          const measure = (node: Element) => {
            const r = node.getBoundingClientRect()
            const s = getComputedStyle(node)
            return { x: r.x, y: r.y, width: r.width, height: r.height, color: s.color, background: s.backgroundColor, radius: s.borderRadius, font: s.fontSize }
          }
          return { surface: measure(el), items: [...el.querySelectorAll('[role="menuitem"]')].map(measure) }
        })
        expect(data.surface.x).toBeGreaterThanOrEqual(0)
        expect(data.surface.x + data.surface.width).toBeLessThanOrEqual(width + 1)
        if (evidence) {
          await fs.mkdir(evidence, { recursive: true })
          const key = path.join(evidence, `${palette}-${theme}-${width}`)
          const png = await surface.screenshot({ animations: 'disabled' })
          if (capture) {
            await fs.writeFile(`${key}.json`, JSON.stringify(data, null, 2))
            await fs.writeFile(`${key}-before.png`, png)
          }
          else {
            const old = JSON.parse(await fs.readFile(`${key}.json`, 'utf8')) as typeof data
            await fs.writeFile(`${key}-after.json`, JSON.stringify(data, null, 2))
            await fs.writeFile(`${key}-after.png`, png)
            expect(data.items.length).toBe(old.items.length)
            for (const [i, current] of [data.surface, ...data.items].entries()) {
              const prior = [old.surface, ...old.items][i]
              for (const prop of ['x', 'y', 'width', 'height'] as const) { expect(Math.abs(current[prop] - prior[prop]), `${key} ${i} ${prop}`).toBeLessThanOrEqual(1) }
              for (const prop of ['color', 'radius', 'font'] as const) { expect(current[prop], `${key} ${i} ${prop}`).toBe(prior[prop]) }
            }
          }
        }
        await page.keyboard.press('Escape')
        await expect(menu).toBeHidden()
        await expect(trigger).toBeFocused()
        await trigger.click()
        await menu.getByRole('menuitem', { name: '个人设置' }).click()
        const dialog = page.getByRole('dialog', { name: '个人设置' })
        await expect(dialog).toBeVisible()
        await dialog.getByPlaceholder('请输入原密码').fill('Synthetic-123')
        await page.keyboard.press('Escape')
        await expect(dialog).toBeVisible()
        await page.mouse.click(5, 5)
        await expect(dialog).toBeVisible()
        await expect(dialog.getByPlaceholder('请输入原密码')).toHaveValue('Synthetic-123')
        await dialog.locator('.el-dialog__headerbtn').click()
        await expect(dialog).toBeHidden()
      }
    })
  }
}
