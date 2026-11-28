import type { Locator } from '@playwright/test'
import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => { await isolateLocalVisualSettings(page) })

const evidence = process.env.MRR_SELECTION_EVIDENCE
const capture = process.env.MRR_SELECTION_CAPTURE === 'true'
if (capture && !evidence) { throw new Error('Capture requires evidence directory') }

async function compare(region: Locator, key: string, selectors: string[]) {
  const data = await region.evaluate((root, selectors) => {
    const r = root.getBoundingClientRect()
    return {
      width: r.width,
      height: r.height,
      controls: selectors.map((selector) => {
        const node = root.querySelector(selector)
        if (!node) { throw new Error(`Missing ${selector}`) }
        const b = node.getBoundingClientRect()
        const s = getComputedStyle(node)
        return { x: b.x - r.x, y: b.y - r.y, width: b.width, height: b.height, radius: s.borderRadius, font: s.fontSize, weight: s.fontWeight, color: s.color, background: s.backgroundColor }
      }),
    }
  }, selectors)
  if (!evidence) { return }
  await fs.mkdir(evidence, { recursive: true })
  const file = path.join(evidence, key)
  if (capture) {
    await fs.writeFile(`${file}.json`, JSON.stringify(data, null, 2))
    await region.screenshot({ path: `${file}-before.png`, animations: 'disabled' })
  }
  else {
    const old = JSON.parse(await fs.readFile(`${file}.json`, 'utf8')) as typeof data
    await fs.writeFile(`${file}-after.json`, JSON.stringify(data, null, 2))
    await region.screenshot({ path: `${file}-after.png`, animations: 'disabled' })
    for (const k of ['width', 'height'] as const) { expect(Math.abs(data[k] - old[k]), `${key} ${k}`).toBeLessThanOrEqual(1) }
    for (const [i, control] of data.controls.entries()) {
      for (const k of ['x', 'y', 'width', 'height'] as const) { expect(Math.abs(control[k] - old.controls[i][k]), `${key} ${i} ${k}`).toBeLessThanOrEqual(1) }
      for (const k of ['radius', 'font', 'weight', 'color', 'background'] as const) { expect(control[k], `${key} ${i} ${k}`).toBe(old.controls[i][k]) }
    }
  }
}

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`${palette}/${theme} 外观圆角滑块与展厅选择器视觉`, async ({ page }) => {
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, themePalette: palette, enableMotion: false, radius: 0.5 }, menu: { mode: 'single' } })), { palette, theme })
      for (const width of [1440, 760]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto('/settings?section=appearance')
        await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
        if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
        const slider = page.getByRole('slider', { name: '圆角系数' })
        await expect(slider).toBeVisible()
        await slider.focus()
        await slider.press('Home')
        await slider.press('ArrowRight')
        await slider.press('ArrowRight')
        await expect(slider).toHaveAttribute('aria-valuenow', '0.5')
        await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem('MRR-ADMIN:app-settings') || '{}').app?.radius)).toBe(0.5)
        await slider.blur()
        await page.mouse.move(0, 0)
        await compare(page.locator('.radius-control'), `${palette}-${theme}-${width}-radius`, ['.fa-slider__track, .el-slider__runway', '.fa-slider__thumb, .el-slider__button', '.radius-value'])
        await page.goto('/ui-components')
        await page.getByLabel('查找组件', { exact: true }).fill('ElSelect')
        const row = page.locator('.example-field').filter({ has: page.getByText('示例来源', { exact: true }) })
        const select = row.getByRole('combobox')
        await expect(select).toBeVisible()
        await page.mouse.move(0, 0)
        await compare(row, `${palette}-${theme}-${width}-select`, ['.fa-select-trigger, .el-select__wrapper'])
        if (!capture) {
          await expect(row.locator('.el-select')).toHaveCount(1)
          await row.locator('.el-select__wrapper').click()
          await expect(select).toHaveAttribute('aria-expanded', 'true')
          await page.getByRole('option', { name: '归档示例', exact: true }).click()
          await expect(row.locator('.el-select__selected-item:not(.el-select__input-wrapper)')).toHaveText('归档示例')
        }
      }
    })
  }
}
