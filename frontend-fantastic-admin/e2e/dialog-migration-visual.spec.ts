import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'

const evidence = process.env.MRR_DIALOG_EVIDENCE
const capture = process.env.MRR_DIALOG_CAPTURE === 'true'
if (capture && !evidence) { throw new Error('Capture requires evidence directory') }

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`${palette}/${theme} 对话框与抽屉视觉基准`, async ({ page }) => {
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, themePalette: palette, enableMotion: false }, menu: { mode: 'single' }, layout: { enableMobileAdaptation: true } })), { palette, theme })
      for (const width of [1440, 760]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto('/ui-components')
        await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
        if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
        await page.getByRole('button', { name: /反馈与浮层/ }).click()
        for (const [label, name] of [['打开对话框', '对话框示例'], ['打开抽屉', '抽屉示例'], ['打开应用外观设置', '应用外观设置']] as const) {
          const trigger = page.getByRole('button', { name: label, exact: true })
          await trigger.click()
          const dialogRole = page.getByRole('dialog', { name, exact: true })
          await expect(dialogRole).toBeVisible()
          const dialog = name === '对话框示例' && !capture ? dialogRole.locator('.el-dialog') : dialogRole
          await expect.poll(() => dialog.evaluate((el) => {
            // Element Plus animates an ancestor of the actual surface.
            for (let node: Element | null = el; node; node = node.parentElement) {
              if (node.getAnimations().some(animation => animation.playState === 'running' || animation.pending)) { return false }
              if (node === document.body) { break }
            }
            return true
          })).toBe(true)
          await expect.poll(() => dialog.evaluate((el) => {
            const r = el.getBoundingClientRect()
            return r.left >= 0 && r.right <= innerWidth + 1
          })).toBe(true)
          await page.mouse.move(0, 899)
          const data = await dialog.evaluate((el) => {
            const measure = (node: Element) => {
              const r = node.getBoundingClientRect()
              const s = getComputedStyle(node)
              return { x: r.x, y: r.y, width: r.width, height: r.height, color: s.color, background: s.backgroundColor, radius: s.borderRadius, font: s.fontSize }
            }
            return { surface: measure(el), title: measure(el.querySelector('h2, h3, .el-dialog__title, .el-drawer__title')!), buttons: [...el.querySelectorAll('button')].filter(node => node.textContent?.trim() === '确定' || node.textContent?.trim() === '取消').map(measure) }
          })
          expect(data.surface.x).toBeGreaterThanOrEqual(0)
          expect(data.surface.x + data.surface.width).toBeLessThanOrEqual(width + 1)
          if (evidence) {
            await fs.mkdir(evidence, { recursive: true })
            const key = path.join(evidence, `${palette}-${theme}-${width}-${name}`)
            const png = await dialog.screenshot({ animations: 'disabled' })
            if (capture) {
              await fs.writeFile(`${key}.json`, JSON.stringify(data, null, 2))
              await fs.writeFile(`${key}-before.png`, png)
            }
            else {
              const old = JSON.parse(await fs.readFile(`${key}.json`, 'utf8')) as typeof data
              await fs.writeFile(`${key}-after.json`, JSON.stringify(data, null, 2))
              await fs.writeFile(`${key}-after.png`, png)
              expect(data.buttons.length).toBe(old.buttons.length)
              for (const [i, current] of [data.surface, data.title, ...data.buttons].entries()) {
                const prior = [old.surface, old.title, ...old.buttons][i]
                for (const prop of ['x', 'y', 'width', 'height'] as const) { expect(Math.abs(current[prop] - prior[prop]), `${key} ${i} ${prop}`).toBeLessThanOrEqual(1) }
                for (const prop of ['color', 'background', 'radius', 'font'] as const) { expect(current[prop], `${key} ${i} ${prop}`).toBe(prior[prop]) }
              }
            }
          }
          await page.keyboard.press('Escape')
          await expect(dialogRole).toBeHidden()
        }
      }
    })
  }
}
