import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { chromium } from '@playwright/test'

const dir = process.env.MRR_UI_MATRIX_EVIDENCE
if (!dir || !path.isAbsolute(dir)) { throw new Error('请通过 MRR_UI_MATRIX_EVIDENCE 指定绝对截图目录') }
const baseURL = 'http://127.0.0.1:9037'
await fs.mkdir(dir, { recursive: true })
// Only use the local Mock preview; never run against a medical production host.
const allRoutes = ['users', 'patients', 'records', 'logs', 'audit-images', 'archive-boxes', 'permissions', 'runtime-errors', 'data-exchange', 'operations-center', 'statistics', 'data-relations', 'ocr-batches', 'settings', 'monitoring', 'response-analysis', 'oss-migration', 'statistics-detail', 'database-browse', 'oss-browser', 'nginx-browser', 'help', 'auth-test', 'ui-components', 'archive-workbench']
const routes = process.env.MRR_UI_MATRIX_ROUTES?.split(',').filter(route => allRoutes.includes(route)) ?? allRoutes
if (!routes.length) { throw new Error('没有有效的审查路由') }
const browser = await chromium.launch()
const results = []
for (const theme of ['light', 'dark']) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  await page.route('**/api/v1/settings/ui', r => r.fulfill({ json: { code: 200, data: {} } }))
  await page.addInitScript(theme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, enableMotion: false, pageTitleStyle: 'card' }, menu: { mode: 'single' }, layout: { enableMobileAdaptation: true } })), theme)
  for (const route of routes) {
    try {
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(`${baseURL}/${route}`, { waitUntil: 'domcontentloaded' })
      await page.locator('.mrr-page-shell h1').first().waitFor({ timeout: 8000 })
      await page.waitForFunction(() => !document.querySelector('.mrr-page-shell .el-loading-mask:not([style*="display: none"])'), {}, { timeout: 5000 }).catch(() => {})
      await page.waitForTimeout(200)
      for (const width of [1440, 375]) {
        await page.setViewportSize({ width, height: 900 })
        await page.waitForFunction(w => document.body.dataset.mode === (w < 1024 ? 'mobile' : 'pc'), width, { timeout: 5000 })
        await page.waitForFunction(w => document.querySelector('.mrr-page-shell').clientWidth > Math.min(w - 80, 700), width, { timeout: 5000 })
        await page.waitForTimeout(150)
        const data = await page.evaluate(() => {
          const root = document.querySelector('.mrr-page-shell')
          const visible = el => el.getBoundingClientRect().width > 0 && el.getBoundingClientRect().height > 0 && getComputedStyle(el).visibility !== 'hidden'
          const overflow = [...root.querySelectorAll('.mrr-page-header,.mrr-section-card__header,.mrr-filter-bar,.el-pagination,.settings-layout,.analysis-stack,.app-loading,.app-empty,.app-error')].filter(el => visible(el) && el.scrollWidth > el.clientWidth + 2).map(el => [String(el.className), el.clientWidth, el.scrollWidth])
          return { title: root.querySelector('h1')?.textContent, document: document.documentElement.scrollWidth, shell: [root.clientWidth, root.scrollWidth], dark: document.documentElement.classList.contains('dark'), overflow }
        })
        const row = { theme, route, width, ...data }; results.push(row)
        await page.screenshot({ path: `${dir}/${theme}-${width}-${route}.png`, animations: 'disabled', timeout: 8000 })
        console.log(JSON.stringify(row))
      }
    }
    catch (e) { const row = { theme, route, error: String(e).slice(0, 250) }; results.push(row); console.log(JSON.stringify(row)) }
  }
  await page.close()
}
await fs.writeFile(`${dir}/matrix.json`, JSON.stringify(results, null, 2))
await browser.close()
const failures = results.filter(row => row.error || row.document > row.width || row.shell[1] > row.shell[0] + 2 || row.overflow.length || row.dark !== (row.theme === 'dark'))
console.log(JSON.stringify({ cases: results.length, failures: failures.length }))
process.exitCode = failures.length ? 1 : 0
