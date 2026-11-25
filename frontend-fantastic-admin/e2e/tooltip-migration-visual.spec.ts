import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'

const evidence = process.env.MRR_TOOLTIP_EVIDENCE
const capture = process.env.MRR_TOOLTIP_CAPTURE === 'true'
if (capture && !evidence) { throw new Error('Capture requires evidence directory') }

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`${palette}/${theme} 提示与气泡视觉及键盘关闭`, async ({ page }) => {
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, themePalette: palette, enableMotion: false }, menu: { mode: 'single' } })), { palette, theme })
      for (const width of [1440, 760]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto('/ui-components')
        await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
        if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
        await page.getByRole('button', { name: /基础/ }).click()
        const trigger = page.getByRole('button', { name: '查看提示', exact: true })
        await trigger.scrollIntoViewIfNeeded()
        await trigger.focus()
        await trigger.hover()
        const tip = page.locator('[data-reka-popper-content-wrapper] > div[data-state], .el-popper.mrr-tooltip').filter({ hasText: '这是组件说明提示' }).filter({ visible: true }).last()
        await expect(tip).toBeVisible()
        async function record(name: string, locator: typeof tip, anchor: typeof trigger) {
          await expect.poll(() => locator.evaluate(el => el.getAnimations().every(animation => animation.playState === 'finished' || animation.playState === 'idle'))).toBe(true)
          const data = await locator.evaluate((el) => {
            const rect = el.getBoundingClientRect()
            const style = getComputedStyle(el)
            return { x: rect.x, y: rect.y, width: rect.width, height: rect.height, radius: style.borderRadius, color: style.color, background: style.backgroundColor, font: style.fontSize, padding: style.padding, border: style.borderColor, shadow: style.boxShadow }
          })
          const bounds = await anchor.boundingBox()
          expect(bounds).not.toBeNull()
          expect(data.x).toBeGreaterThanOrEqual(0)
          expect(data.x + data.width).toBeLessThanOrEqual(width + 1)
          if (!capture) { await expect(locator).toHaveClass(/el-popper/) }
          if (evidence) {
            await fs.mkdir(evidence, { recursive: true })
            const key = path.join(evidence, `${palette}-${theme}-${width}-${name}`)
            const rect = await locator.boundingBox()
            expect(rect).not.toBeNull()
            const png = await page.screenshot({ clip: rect! })
            const measured = { ...data, x: data.x - bounds!.x, y: data.y - bounds!.y }
            if (capture) {
              await fs.writeFile(`${key}.json`, JSON.stringify(measured, null, 2))
              await fs.writeFile(`${key}-before.png`, png)
            }
            else {
              const old = JSON.parse(await fs.readFile(`${key}.json`, 'utf8')) as typeof measured
              await fs.writeFile(`${key}-after.json`, JSON.stringify(measured, null, 2))
              await fs.writeFile(`${key}-after.png`, png)
              for (const prop of ['x', 'y', 'width', 'height'] as const) { expect(Math.abs(measured[prop] - old[prop]), `${key} ${prop}`).toBeLessThanOrEqual(1) }
              for (const prop of ['radius', 'color', 'background', 'font', 'padding'] as const) { expect(measured[prop], `${key} ${prop}`).toBe(old[prop]) }
            }
          }
        }
        await record('tooltip', tip, trigger)
        await page.keyboard.press('Escape')
        await expect(tip).toBeHidden()
        await expect(trigger).toBeFocused()
        if (!capture) {
          await page.mouse.move(0, 0)
          await trigger.blur()
          await trigger.focus()
          await expect(tip).toBeVisible()
          await page.keyboard.press('Escape')
          await expect(tip).toBeHidden()
        }
        await page.getByRole('button', { name: /反馈与浮层/ }).click()
        const popTrigger = page.getByRole('button', { name: '打开气泡', exact: true })
        await popTrigger.click()
        const pop = page.getByText('气泡用于补充轻量说明，点击外部或按 Escape 关闭。', { exact: true }).locator('xpath=ancestor::*[@role="dialog" or contains(concat(" ",normalize-space(@class)," ")," el-popper ")][1]')
        await expect(pop).toBeVisible()
        await record('popover', pop, popTrigger)
        await page.keyboard.press('Escape')
        await expect(pop).toBeHidden()
        await expect(popTrigger).toBeFocused()
        await popTrigger.click()
        await expect(pop).toBeVisible()
        await page.getByRole('heading', { name: '对话框、抽屉与气泡', exact: true }).click()
        await expect(pop).toBeHidden()
      }
    })
  }
}
