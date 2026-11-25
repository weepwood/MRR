import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'

const evidence = process.env.MRR_BUSINESS_VISUAL_EVIDENCE
const routes = [
  ['users', '用户管理'],
  ['patients', '患者管理'],
  ['records', '记录管理'],
  ['logs', '日志管理'],
  ['audit-images', '病历访问审计可视化'],
  ['archive-boxes', '档案装箱管理'],
  ['permissions', '权限管理'],
  ['runtime-errors', '运行错误中心'],
  ['data-exchange', '数据导入导出'],
  ['operations-center', '运维诊断中心'],
] as const

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`${palette}/${theme} 实际业务页视觉调查`, async ({ page }, testInfo) => {
      test.setTimeout(180_000)
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, themePalette: palette, pageTitleStyle: 'plain', enableMotion: false }, menu: { mode: 'single' }, layout: { enableMobileAdaptation: true } })), { palette, theme })
      const results: unknown[] = []
      for (const [route, title] of routes) {
        await page.setViewportSize({ width: 1440, height: 900 })
        await page.goto(`/${route}`)
        await expect(page.getByRole('heading', { level: 1, name: title, exact: true })).toBeVisible()
        await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
        if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
        await expect.poll(() => page.locator('.mrr-page-shell').first().evaluate(el => !el.querySelector('.el-loading-mask:not([style*="display: none"]), [aria-busy="true"]'))).toBe(true)
        for (const width of [1440, 760, 375]) {
          await page.setViewportSize({ width, height: 900 })
          await expect.poll(() => page.evaluate(() => document.body.dataset.mode)).toBe(width < 1024 ? 'mobile' : 'pc')
          await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
          const metrics = await page.evaluate(() => {
            const root = document.querySelector('.mrr-page-shell')!
            const visible = (el: Element) => el.getBoundingClientRect().width > 0 && el.getBoundingClientRect().height > 0 && getComputedStyle(el).visibility !== 'hidden'
            function measure(el: Element) {
              const rect = el.getBoundingClientRect()
              const s = getComputedStyle(el)
              return { cls: el.className.toString().slice(0, 150), text: (el.textContent ?? '').trim().replace(/\s+/g, ' ').slice(0, 45), x: Math.round(rect.x), y: Math.round(rect.y), w: Math.round(rect.width), h: Math.round(rect.height), font: s.fontSize, weight: s.fontWeight, color: s.color, background: s.backgroundColor, border: s.borderColor, radius: s.borderRadius, padding: s.padding, gap: s.gap, scroll: el.scrollWidth, client: el.clientWidth }
            }
            function list(selector: string, max = 20) { return Array.from(root.querySelectorAll(selector)).filter(visible).slice(0, max).map(measure) }
            return {
              viewport: innerWidth,
              document: document.documentElement.scrollWidth,
              shell: measure(root),
              header: list('.mrr-page-header'),
              titles: list('h1'),
              cards: list('.mrr-section-card, .el-card', 10),
              filters: list('.mrr-filter-bar, .mrr-data-table-panel__filters'),
              fields: list('.mrr-filter-bar .el-input__wrapper, .mrr-filter-bar .el-select__wrapper, .mrr-filter-bar .el-date-editor, .mrr-filter-bar .el-form-item__label, .mrr-filter-field > label, .mrr-filter-field__label'),
              buttons: list('.mrr-filter-bar .el-button, .mrr-page-header .el-button'),
              tables: list('.el-table'),
              tableHeaders: list('.el-table__header th', 6),
              rows: list('.el-table__body tr', 3),
              pagination: list('.el-pagination'),
              overflow: list('.mrr-page-header, .mrr-section-card__header, .mrr-filter-bar, .mrr-data-table-panel__filters, .el-pagination').filter(el => el.scroll > el.client + 1),
            }
          })
          expect(metrics.titles, `${route} visible page title`).toHaveLength(1)
          expect(metrics.document, `${route} page overflow`).toBeLessThanOrEqual(width)
          expect(metrics.overflow, `${route} shared container overflow`).toEqual([])
          for (const field of metrics.fields.filter(item => item.cls.includes('el-select__wrapper'))) {
            expect(field.h, `${route} filter select height`).toBe(palette === 'gr8r' ? 30 : 33)
          }
          for (const label of metrics.fields.filter(item => item.cls === '' || item.cls.includes('el-form-item__label') || item.cls.includes('mrr-filter-field__label'))) {
            expect(label.font, `${route} filter label font`).toBe('12px')
            expect(label.weight, `${route} filter label weight`).toBe('600')
          }
          if (route === 'logs' || route === 'audit-images') {
            const columns = await page.locator('.mrr-filter-bar--grid .mrr-filter-bar__fields').evaluate(el => getComputedStyle(el).gridTemplateColumns.trim().split(/\s+/).length)
            expect(columns, 'query grid columns').toBe(width === 1440 ? 3 : width === 760 ? 2 : 1)
          }
          if (route === 'audit-images') {
            const summary = page.locator('.mrr-metric-grid--five')
            await expect(summary.locator('.mrr-metric-card')).toHaveCount(5)
            const cards = await summary.locator('.mrr-metric-card').evaluateAll(elements => elements.map((el) => {
              const rect = el.getBoundingClientRect()
              const label = el.querySelector('.mrr-metric-card__label')!
              return { x: rect.x, y: Math.round(rect.y), width: rect.width, right: rect.right, labelFits: label.scrollWidth <= label.clientWidth + 1 }
            }))
            expect(cards.every(card => card.labelFits)).toBe(true)
            const groups = new Map<number, number>()
            for (const card of cards) { groups.set(card.y, (groups.get(card.y) || 0) + 1) }
            expect([...groups.values()], `${width} audit metric rows`).toEqual(width === 1440 ? [5] : width === 760 ? [2, 2, 1] : [1, 1, 1, 1, 1])
          }
          results.push({ route, width, palette, theme, ...metrics })
          if (evidence) {
            await fs.mkdir(evidence, { recursive: true })
            await page.screenshot({ path: path.join(evidence, `${palette}-${theme}-${route}-${width}.png`), animations: 'disabled' })
            await fs.writeFile(path.join(evidence, `${palette}-${theme}.json`), JSON.stringify(results, null, 2))
          }
        }
      }
      await testInfo.attach('business-visual-metrics', { body: JSON.stringify(results), contentType: 'application/json' })
      expect(results).toHaveLength(routes.length * 3)
    })
  }
}
