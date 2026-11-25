import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'

const evidence = process.env.MRR_BUTTON_EVIDENCE
const capture = process.env.MRR_BUTTON_CAPTURE === 'true'
if (capture && !evidence) { throw new Error('Capture requires evidence directory') }

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`${palette}/${theme} 按钮角色与按钮组迁移视觉`, async ({ page }) => {
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, themePalette: palette, enableMotion: false }, menu: { mode: 'single' } })), { palette, theme })
      for (const width of [1440, 760]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto('/ui-components')
        await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
        if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
        await page.getByLabel('查找组件', { exact: true }).fill('按钮与操作')
        const article = page.locator('article').filter({ has: page.getByRole('heading', { name: '按钮与操作', exact: true }) })
        const state = article.getByRole('radiogroup', { name: '按钮示例状态' })
        const rows = article.locator('.example-row')
        await expect(rows).toHaveCount(4)
        for (const mode of ['默认', '禁用', '加载']) {
          await state.getByText(mode, { exact: true }).click()
          await page.mouse.move(0, 0)
          for (const index of mode === '默认' ? [0, 1, 2, 3] : [0, 1]) {
            const row = rows.nth(index)
            const data = await row.evaluate((root) => {
              const rect = root.getBoundingClientRect()
              return [...root.querySelectorAll('button')].map((el) => {
                const r = el.getBoundingClientRect()
                const s = getComputedStyle(el)
                return { x: r.x - rect.x, y: r.y - rect.y, width: r.width, height: r.height, radius: s.borderRadius, background: s.backgroundColor, color: s.color, border: s.borderColor, font: s.fontSize, opacity: s.opacity, disabled: el.disabled }
              })
            })
            if (!capture) { await expect(row.locator('button:not(.el-button)')).toHaveCount(0) }
            if (evidence) {
              await fs.mkdir(evidence, { recursive: true })
              const key = path.join(evidence, `${palette}-${theme}-${width}-${mode}-${index}`)
              const png = await row.screenshot({ animations: 'disabled' })
              if (capture) {
                await fs.writeFile(`${key}.json`, JSON.stringify(data, null, 2))
                await fs.writeFile(`${key}-before.png`, png)
              }
              else {
                const old = JSON.parse(await fs.readFile(`${key}.json`, 'utf8')) as typeof data
                await fs.writeFile(`${key}-after.json`, JSON.stringify(data, null, 2))
                await fs.writeFile(`${key}-after.png`, png)
                expect(data.length).toBe(old.length)
                for (const [i, current] of data.entries()) {
                  for (const prop of ['x', 'y', 'width', 'height'] as const) { expect(Math.abs(current[prop] - old[i][prop]), `${key} ${i} ${prop}`).toBeLessThanOrEqual(1) }
                  for (const prop of ['radius', 'background', 'color', 'border', 'font', 'opacity', 'disabled'] as const) { expect(current[prop], `${key} ${i} ${prop}`).toBe(old[i][prop]) }
                }
              }
            }
          }
        }
      }
    })
  }
}
