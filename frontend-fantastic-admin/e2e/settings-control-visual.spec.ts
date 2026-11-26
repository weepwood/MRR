import type { Locator } from '@playwright/test'
import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => { await isolateLocalVisualSettings(page) })

const evidence = process.env.MRR_CONTROL_VISUAL_EVIDENCE
const capture = process.env.MRR_CONTROL_VISUAL_CAPTURE === 'true'
if (capture && !evidence) { throw new Error('Capture requires MRR_CONTROL_VISUAL_EVIDENCE') }

async function measure(row: Locator) {
  return row.evaluate((el) => {
    const r = el.getBoundingClientRect()
    const controls = [...el.querySelectorAll('.fa-input-control, .el-input__wrapper, .button-options button')]
    return {
      row: { width: r.width, height: r.height },
      controls: controls.map((node) => {
        const b = node.getBoundingClientRect()
        const style = getComputedStyle(node)
        const text = node.querySelector('input') ?? node
        const ink = getComputedStyle(text)
        return {
          x: b.x - r.x,
          y: b.y - r.y,
          width: b.width,
          height: b.height,
          font: ink.fontSize,
          weight: ink.fontWeight,
          radius: style.borderRadius,
          background: style.backgroundColor,
          color: ink.color,
        }
      }),
    }
  })
}

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`外观设置控件迁移视觉契约 ${palette}/${theme}`, async ({ page }) => {
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { colorScheme: theme, themePalette: palette, enableMotion: false },
        menu: { mode: 'single' },
      })), { palette, theme })
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto('/settings?section=appearance')
      const panel = page.locator('.app-config-panel')
      await expect(panel).toBeVisible()
      await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
      if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
      else { await expect(page.locator('html')).not.toHaveAttribute('data-mrr-palette', 'gr8r') }
      for (const width of [1440, 760]) {
        await page.setViewportSize({ width, height: 900 })
        for (const [tab, labels] of [
          ['导航与顶栏', ['主导航点击模式', '顶栏定位']],
          ['主页、版权与其他', ['主页名称', '日期', '公司', '网址', '备案号']],
        ] as const) {
          await panel.getByRole('tab', { name: tab, exact: true }).click()
          for (const label of labels) {
            const row = panel.locator('.setting-item').filter({ has: page.getByText(label, { exact: true }) })
            await expect(row).toHaveCount(1)
            await row.scrollIntoViewIfNeeded()
            await page.mouse.move(0, 0)
            const measured = await measure(row)
            expect(measured.controls.length).toBe(label === '主导航点击模式' || label === '顶栏定位' ? 3 : 1)
            if (!capture) {
              await expect(row.locator('.fa-input-control, .fa-button-control')).toHaveCount(0)
              if (measured.controls.length === 1) {
                await expect(row.locator('.el-input')).toHaveCount(1)
                expect(measured.controls[0].width).toBe(200)
                const field = row.getByRole('textbox', { name: label, exact: true })
                if (label === '主页名称') { await expect(field).toBeEnabled() }
                else { await expect(field).toBeDisabled() }
              }
              else {
                await expect(row.locator('.button-options .el-button')).toHaveCount(3)
                await expect(row.locator('.button-options .el-button[aria-pressed="true"]')).toHaveCount(1)
                expect(measured.controls[1].x - measured.controls[0].x - measured.controls[0].width).toBe(8)
                expect(measured.controls[2].x - measured.controls[1].x - measured.controls[1].width).toBe(8)
              }
            }
            const key = `${palette}-${theme}-${width}-${label}`
            if (evidence) {
              await fs.mkdir(evidence, { recursive: true })
              const file = path.join(evidence, `${key}.json`)
              if (capture) {
                await fs.writeFile(file, JSON.stringify(measured, null, 2))
                await row.screenshot({ path: path.join(evidence, `${key}-before.png`), animations: 'disabled' })
              }
              else {
                const baseline = JSON.parse(await fs.readFile(file, 'utf8')) as typeof measured
                await fs.writeFile(path.join(evidence, `${key}-after.json`), JSON.stringify(measured, null, 2))
                await row.screenshot({ path: path.join(evidence, `${key}-after.png`), animations: 'disabled' })
                for (const prop of ['width', 'height'] as const) {
                  expect(Math.abs(measured.row[prop] - baseline.row[prop]), `${key} row ${prop}`).toBeLessThanOrEqual(1)
                }
                for (const [i, current] of measured.controls.entries()) {
                  const old = baseline.controls[i]
                  for (const prop of ['x', 'y', 'width', 'height'] as const) {
                    expect(Math.abs(current[prop] - old[prop]), `${key} control ${prop}`).toBeLessThanOrEqual(1)
                  }
                  for (const prop of ['font', 'weight', 'radius', 'background', 'color'] as const) {
                    expect(current[prop], `${key} ${prop}`).toBe(old[prop])
                  }
                }
              }
            }
            expect(measured.controls.every(c => c.x >= -1 && c.x + c.width <= measured.row.width + 1)).toBe(true)
          }
        }
      }
    })
  }
}

test('迁移后的设置字段与选项保持可操作、禁用联动和本地保存', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/settings?section=appearance')
  const panel = page.locator('.app-config-panel')
  await panel.getByRole('tab', { name: '导航与顶栏' }).click()
  const choice = panel.getByRole('button', { name: '智能选择', exact: true })
  await choice.click()
  await expect(choice).toHaveAttribute('aria-pressed', 'true')
  await panel.getByRole('tab', { name: '主页、版权与其他' }).click()
  const home = panel.getByRole('textbox', { name: '主页名称', exact: true })
  const company = panel.getByRole('textbox', { name: '公司', exact: true })
  await expect(company).toBeDisabled()
  const copyright = panel.getByRole('switch', { name: '启用底部版权', exact: true })
  await copyright.focus()
  await copyright.press('Space')
  await expect(company).toBeEnabled()
  await home.fill('合成设置回归')
  await company.fill('合成机构')
  await expect.poll(() => page.evaluate(() => {
    const settings = JSON.parse(localStorage.getItem('MRR-ADMIN:app-settings') || '{}')
    return [settings.menu?.mainMenuClickMode, settings.home?.title, settings.copyright?.company]
  })).toEqual(['smart', '合成设置回归', '合成机构'])
  await page.reload()
  await panel.getByRole('tab', { name: '主页、版权与其他' }).click()
  await expect(home).toHaveValue('合成设置回归')
  await expect(company).toHaveValue('合成机构')
})
