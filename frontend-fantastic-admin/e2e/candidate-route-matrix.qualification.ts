import { expect, test } from '@playwright/test'

const routes = [
  ['/', '管理概览'],
  ['/records', '记录管理'],
  ['/patients', '患者管理'],
  ['/archive-workbench', '病案工作台'],
  ['/statistics', '病案扫描数据统计'],
  ['/archive-boxes', '档案装箱管理'],
  ['/data-exchange', '数据导入导出'],
  ['/data-relations', '数据关系工作台'],
  ['/operations-center', '运维诊断中心'],
  ['/database-browse', '数据表浏览'],
  ['/logs', '日志管理'],
  ['/runtime-errors', '运行错误中心'],
  ['/audit-images', '病历访问审计可视化'],
  ['/monitoring', '系统与数据库监控'],
  ['/response-analysis', '接口响应分析'],
  ['/oss-browser', 'OSS 文件浏览'],
  ['/oss-migration', 'OSS 迁移管理'],
  ['/users', '用户管理'],
  ['/permissions', '权限管理'],
  ['/ocr-batches', '历史未分类影像识别'],
  ['/help', '帮助与文档'],
  ['/nginx-browser', 'Nginx 文件浏览'],
] as const

for (const theme of ['light', 'dark'] as const) {
  for (const [path, heading] of routes) {
    test(`${theme} ${path} · ${heading}`, async ({ page }) => {
      const errors: string[] = []
      page.on('pageerror', error => errors.push(error.message))
      await page.addInitScript(theme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { colorScheme: theme, enableMotion: false },
        menu: { mode: 'single' },
        layout: { enableMobileAdaptation: true },
      })), theme)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(path)
      await expect(page.getByRole('heading', { name: heading, exact: true }).first()).toBeVisible()
      await expect.poll(() => new URL(page.url()).pathname).toBe(path)
      await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
      await expect(page.locator('#browser-compat-blocker')).toBeHidden()
      // Tables may have intentional internal scroll, but the page and shared action containers must fit.
      for (const width of [1440, 390]) {
        await page.setViewportSize({ width, height: 900 })
        await expect.poll(() => page.evaluate(() => document.body.dataset.mode)).toBe(width < 1024 ? 'mobile' : 'pc')
        await expect.poll(() => page.evaluate(() => ({
          root: document.documentElement.scrollWidth > innerWidth + 1,
          shared: Array.from(document.querySelectorAll<HTMLElement>(
            '.mrr-filter-bar__actions,.mrr-section-card__actions,.mrr-page-header__aside,.mrr-filter-bar__fields,.mrr-page-header__copy,.mrr-section-card__copy',
          )).filter(el => el.getClientRects().length && el.clientWidth > 0 && el.scrollWidth > el.clientWidth + 2).map(el => el.className),
        }))).toEqual({ root: false, shared: [] })
        await expect(page.getByRole('heading', { name: heading, exact: true }).first()).toBeVisible()
        await page.screenshot({ path: test.info().outputPath(`${width}.png`) })
      }
      expect(errors).toEqual([])
    })
  }
}
