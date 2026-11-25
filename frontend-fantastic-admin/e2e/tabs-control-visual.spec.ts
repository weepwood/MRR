import type { Locator } from '@playwright/test'
import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'

const evidence = process.env.MRR_TABS_EVIDENCE
const capture = process.env.MRR_TABS_CAPTURE === 'true'
if (capture && !evidence) { throw new Error('Capture requires evidence directory') }

async function compare(region: Locator, key: string) {
  await region.scrollIntoViewIfNeeded()
  const data = await region.evaluate((root) => {
    const r = root.getBoundingClientRect()
    const props = (el: Element) => {
      const b = el.getBoundingClientRect()
      const s = getComputedStyle(el)
      return { x: b.x - r.x, y: b.y - r.y, width: b.width, height: b.height, radius: s.borderRadius, font: s.fontSize, weight: s.fontWeight, color: s.color, background: s.backgroundColor }
    }
    return { root: props(root), tabs: [...root.querySelectorAll('[role="tab"]')].map(el => ({ ...props(el), name: el.textContent?.trim(), selected: el.getAttribute('aria-selected') })) }
  })
  expect(data.tabs.length).toBeGreaterThanOrEqual(2)
  expect(data.tabs.filter(tab => tab.selected === 'true')).toHaveLength(1)
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
    expect(data.tabs.map(t => [t.name, t.selected])).toEqual(old.tabs.map(t => [t.name, t.selected]))
    for (const [i, current] of [data.root, ...data.tabs].entries()) {
      const prior = [old.root, ...old.tabs][i]
      for (const k of ['x', 'y', 'width', 'height'] as const) { expect(Math.abs(current[k] - prior[k]), `${key} ${i} ${k}`).toBeLessThanOrEqual(1) }
      for (const k of ['radius', 'font', 'weight', 'color', 'background'] as const) { expect(current[k], `${key} ${i} ${k}`).toBe(prior[k]) }
    }
  }
}

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`${palette}/${theme} 页签布局与状态保持`, async ({ page }) => {
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, themePalette: palette, enableMotion: false, pageTitleStyle: 'plain' }, menu: { mode: 'single' } })), { palette, theme })
      for (const width of [1440, 760]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto('/settings?section=appearance')
        await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
        if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
        else { await expect(page.locator('html')).not.toHaveAttribute('data-mrr-palette', 'gr8r') }
        const panel = page.locator('.app-config-panel')
        await expect(panel).toBeVisible()
        await page.mouse.move(0, 0)
        await compare(panel.locator('.appearance-tabs > .el-tabs__header').getByRole('tablist'), `${palette}-${theme}-${width}-categories`)
        await compare(panel.locator('.theme-tabs'), `${palette}-${theme}-${width}-theme`)
        await compare(panel.locator('.title-style-tabs'), `${palette}-${theme}-${width}-title`)
        await panel.getByRole('tab', { name: '导航与顶栏', exact: true }).click()
        await expect(panel.getByText('主导航点击模式', { exact: true })).toBeVisible()
        await expect(panel.getByRole('tab', { name: '导航与顶栏', exact: true })).toHaveAttribute('aria-selected', 'true')
        await page.goto('/ui-components')
        await page.getByLabel('查找组件', { exact: true }).fill('页签与提示')
        const tablist = page.getByRole('tablist').filter({ has: page.getByRole('tab', { name: '组件预览', exact: true }) })
        await expect(tablist).toBeVisible()
        await page.mouse.move(0, 0)
        await compare(tablist, `${palette}-${theme}-${width}-gallery`)
        await page.getByRole('tab', { name: '交互说明', exact: true }).click()
        await expect(page.getByText('页签内容随选择切换；提示可以通过悬停或键盘聚焦查看。', { exact: true })).toBeVisible()
      }
    })
  }
}
