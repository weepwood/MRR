import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { chromium } from '@playwright/test'

const dir = process.env.MRR_UI_MATRIX_EVIDENCE
if (!dir || !path.isAbsolute(dir)) { throw new Error('请指定绝对截图目录 MRR_UI_MATRIX_EVIDENCE') }
await fs.mkdir(dir, { recursive: true })
// Local Mock preview only. No write requests or live medical data.
const routes = ['archive-boxes', 'statistics-detail', 'users', 'records', 'patients', 'logs', 'audit-images', 'statistics', 'runtime-errors', 'oss-migration', 'database-browse', 'ui-components', ...['patients', 'statistics', 'archives', 'archive-boxes', 'scan'].map(tab => `data-exchange?dataset=${tab}`)]
const browser = await chromium.launch()
const rows = []
for (const theme of ['light', 'dark']) {
  const page = await browser.newPage({ viewport: { width: 1920, height: 900 } })
  await page.route('**/api/v1/settings/ui', route => route.fulfill({ json: { code: 200, data: {} } }))
  await page.addInitScript(theme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, enableMotion: false, pageTitleStyle: 'card' }, menu: { mode: 'single' }, layout: { enableMobileAdaptation: true } })), theme)
  for (const route of routes) {
    try {
      await page.setViewportSize({ width: 1920, height: 900 })
      await page.goto(`http://127.0.0.1:9037/${route}`)
      if (route === 'database-browse') {
        await page.getByRole('button', { name: '浏览影像记录（合成）' }).click()
      }
      const bars = page.locator('.mrr-filter-bar:visible')
      await bars.first().waitFor({ timeout: 10000 })
      for (const width of [1920, 1440, 375]) {
        await page.setViewportSize({ width, height: 900 })
        await page.waitForFunction(w => document.body.dataset.mode === (w < 1024 ? 'mobile' : 'pc'), width)
        await page.waitForTimeout(200)
        const measurements = await bars.evaluateAll(els => els.map((el) => {
          const boundary = el.getBoundingClientRect()
          const visible = node => node.getBoundingClientRect().width > 0 && node.getBoundingClientRect().height > 0
          const nodes = [...el.querySelectorAll('.mrr-filter-bar__fields > *, .mrr-filter-bar__advanced > *, .mrr-filter-bar__actions, .mrr-filter-bar__business')].filter(visible)
          const rects = nodes.map(node => node.getBoundingClientRect())
          const controls = [...el.querySelectorAll('.el-input__wrapper,.el-select__wrapper,.el-date-editor,.el-button')].filter(visible).map(node => node.getBoundingClientRect())
          return {
            width: el.clientWidth,
            height: Math.round(boundary.height),
            scroll: el.scrollWidth,
            controlHeights: [...new Set(controls.map(rect => Math.round(rect.height)))],
            out: rects.filter(rect => rect.left < boundary.left - 1 || rect.right > boundary.right + 1).length,
            overlap: rects.some((rect, index) => rects.slice(index + 1).some(other => Math.min(rect.right, other.right) - Math.max(rect.left, other.left) > 1 && Math.min(rect.bottom, other.bottom) - Math.max(rect.top, other.top) > 1)),
          }
        }))
        const row = { route, width, theme, dark: await page.locator('html').evaluate(el => el.classList.contains('dark')), measurements }
        rows.push(row)
        console.log(JSON.stringify(row))
        for (let i = 0; i < measurements.length; i++) {
          await bars.nth(i).screenshot({ path: `${dir}/${theme}-${width}-${route.replace(/[^a-z0-9-]/g, '-')}-${i}.png`, animations: 'disabled', timeout: 8000 })
        }
      }
    }
    catch (error) {
      const row = { route, theme, error: String(error).slice(0, 300) }
      rows.push(row)
      console.log(JSON.stringify(row))
    }
  }
  await page.close()
}
await browser.close()
await fs.writeFile(`${dir}/matrix.json`, JSON.stringify(rows, null, 2))
const failures = rows.filter(row => row.error || row.dark !== (row.theme === 'dark') || !row.measurements.length || row.measurements.some(m => m.scroll > m.width + 1 || m.out || m.overlap))
console.log(JSON.stringify({ cases: rows.length, bars: rows.reduce((n, row) => n + (row.measurements?.length || 0), 0), failures: failures.length }))
process.exitCode = failures.length ? 1 : 0
