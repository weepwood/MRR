import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => { await isolateLocalVisualSettings(page) })

const evidence = process.env.MRR_FIELD_EVIDENCE
const capture = process.env.MRR_FIELD_CAPTURE === 'true'
if (capture && !evidence) { throw new Error('Capture requires evidence directory') }

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`${palette}/${theme} 输入及文本域视觉与编辑状态`, async ({ page }) => {
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, themePalette: palette, enableMotion: false }, menu: { mode: 'single' } })), { palette, theme })
      for (const width of [1440, 760]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto('/ui-components')
        await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
        if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
        const search = page.getByLabel('查找组件', { exact: true })
        await search.fill('输入与筛选')
        const fields = page.locator('article').filter({ has: page.getByRole('heading', { name: '输入与筛选', exact: true }) })
        await expect(fields).toBeVisible()
        await search.blur()
        await page.mouse.move(0, 0)
        for (const label of ['查找组件', '示例名称', '禁用输入', '只读名称', '示例说明', '独立校验示例']) {
          const input = page.getByLabel(label, { exact: true })
          await expect(input).toBeVisible()
          const surface = input.locator('xpath=ancestor-or-self::*[contains(concat(" ",normalize-space(@class)," ")," el-input__wrapper ") or contains(concat(" ",normalize-space(@class)," ")," fa-input-control ") or self::textarea][1]')
          const data = await surface.evaluate((el) => {
            const r = el.getBoundingClientRect()
            const s = getComputedStyle(el)
            const field = el.matches('input,textarea') ? el : el.querySelector('input,textarea')!
            const f = getComputedStyle(field)
            return { width: r.width, height: r.height, radius: s.borderRadius, background: s.backgroundColor, font: f.fontSize, color: f.color, opacity: s.opacity }
          })
          if (!capture) {
            await expect(input).not.toHaveClass(/fa-input-control|fa-textarea-control/)
          }
          if (evidence) {
            await fs.mkdir(evidence, { recursive: true })
            const key = path.join(evidence, `${palette}-${theme}-${width}-${label === '独立校验示例' ? '封装校验示例' : label}`)
            const png = await surface.screenshot({ animations: 'disabled' })
            if (capture) {
              await fs.writeFile(`${key}.json`, JSON.stringify(data, null, 2))
              await fs.writeFile(`${key}-before.png`, png)
            }
            else {
              const old = JSON.parse(await fs.readFile(`${key}.json`, 'utf8')) as typeof data
              await fs.writeFile(`${key}-after.json`, JSON.stringify(data, null, 2))
              await fs.writeFile(`${key}-after.png`, png)
              for (const prop of ['width', 'height'] as const) { expect(Math.abs(data[prop] - old[prop]), `${key} ${prop}`).toBeLessThanOrEqual(1) }
              for (const prop of ['radius', 'background', 'font', 'color', 'opacity'] as const) { expect(data[prop], `${key} ${prop}`).toBe(old[prop]) }
            }
          }
        }
        await fields.getByLabel('示例名称', { exact: true }).fill('新值')
        await expect(fields.getByLabel('示例名称', { exact: true })).toHaveValue('新值')
        await expect(fields.getByLabel('禁用输入', { exact: true })).toBeDisabled()
        await expect(fields.getByLabel('只读名称', { exact: true })).toHaveAttribute('readonly', '')
        await fields.getByLabel('示例说明', { exact: true }).fill('第一行\n第二行')
        await expect(fields.getByLabel('示例说明', { exact: true })).toHaveValue('第一行\n第二行')
        await expect(fields.getByLabel('独立校验示例', { exact: true })).toHaveAccessibleDescription('请输入示例名称')
        await fields.getByLabel('独立校验示例', { exact: true }).fill('有效')
        await expect(fields.getByLabel('独立校验示例', { exact: true })).not.toHaveAttribute('aria-invalid', 'true')
      }
    })
  }
}
