import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => {
  await isolateLocalVisualSettings(page)
})

for (const theme of ['light', 'dark']) {
  test(`${theme} 数据表分页失败保持上次结果、重试与窄屏筛选`, async ({ page }) => {
    await page.addInitScript(theme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: theme, enableMotion: false },
      layout: { enableMobileAdaptation: true },
    })), theme)
    let fail = true
    await page.route('**/api/v1/system/database/browse/tables/scan/rows**', async (route) => {
      if (fail && new URL(route.request().url()).searchParams.get('page') === '2') {
        fail = false
        await route.fulfill({ status: 503, json: { code: 503, message: '合成查询失败' } })
      }
      else { await route.fallback() }
    })
    await page.goto('/database-browse')
    await page.getByRole('button', { name: '浏览影像记录（合成）' }).click()
    await expect(page.getByText('第 1 页 · 本页 50 行', { exact: false })).toBeVisible()
    await page.getByRole('button', { name: '下一页', exact: true }).click()
    await expect(page.getByText('保留上次成功结果', { exact: false })).toBeVisible()
    await expect(page.getByText('第 1 页 · 本页 50 行', { exact: false })).toBeVisible()
    await page.screenshot({ path: test.info().outputPath(`${theme}-browse-error.png`) })
    await page.getByRole('button', { name: '重试请求' }).click()
    await expect(page.getByText('第 2 页 · 本页 14 行', { exact: false })).toBeVisible()
    await page.setViewportSize({ width: 390, height: 844 })
    const field = page.getByRole('textbox', { name: '病案号', exact: true })
    await field.fill('26070000')
    await field.press('Enter')
    await expect(page.getByText('第 1 页 · 本页 2 行', { exact: false })).toBeVisible()
    await expect.poll(() => page.locator('.filters').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
    await page.screenshot({ path: test.info().outputPath(`${theme}-browse-loaded-mobile.png`) })
  })

  test(`${theme} 日志详情失败不展示摘要，重试后真实内容及窄屏弹窗`, async ({ page }) => {
    await page.addInitScript(theme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: theme, enableMotion: false },
      layout: { enableMobileAdaptation: true },
    })), theme)
    let attempt = 0
    await page.route(/\/api\/v1\/logs\/\d+(?:\?|$)/, async (route) => {
      if (++attempt === 1) { await route.fulfill({ status: 503, json: { code: 503, message: 'synthetic detail unavailable' } }) }
      else { await route.fallback() }
    })
    await page.goto('/logs')
    await expect(page.getByRole('heading', { name: '日志管理' })).toBeVisible()
    await expect(page.locator('.el-table__body tr').first()).toBeVisible()
    await page.screenshot({ path: test.info().outputPath(`${theme}-logs.png`) })
    await page.getByRole('button', { name: '查看详情', exact: true }).first().click()
    const dialog = page.getByRole('dialog', { name: '接口访问审计详情' })
    await expect(dialog.getByText('无法读取访问日志详情，请重试')).toBeVisible()
    await expect(dialog.locator('.el-descriptions')).toHaveCount(0)
    await dialog.getByRole('button', { name: '重试', exact: true }).click()
    await expect(dialog.getByText('Request ID', { exact: true })).toBeVisible()
    await page.setViewportSize({ width: 390, height: 844 })
    await expect.poll(() => dialog.evaluate((el) => {
      const r = el.getBoundingClientRect()
      return r.left >= -1 && r.right <= innerWidth + 1 && r.top >= 0 && r.bottom <= innerHeight + 1
    })).toBe(true)
    const body = dialog.locator('.el-dialog__body')
    await expect.poll(() => body.evaluate(el => el.scrollHeight > el.clientHeight)).toBe(true)
    await body.evaluate((el) => { el.scrollTop = el.scrollHeight })
    await expect(dialog.getByText('User-Agent', { exact: true })).toBeInViewport()
    await expect(dialog.getByRole('button', { name: /关闭|close/i })).toBeInViewport()
    await body.evaluate((el) => { el.scrollTop = 0 })
    await page.screenshot({ path: test.info().outputPath(`${theme}-log-detail-mobile.png`) })
    await page.keyboard.press('Escape')
    await expect(dialog).not.toBeVisible()
    expect(attempt).toBe(2)
  })
}

test.describe('匿名登录双主题截图', () => {
  test.use({ storageState: 'e2e/.auth/anonymous.json' })
  for (const theme of ['light', 'dark']) {
    test(`${theme} 登录输入与错误提示不溢出`, async ({ page }) => {
      await page.route('**/api/v1/public/status/developer-mode', route => route.fulfill({ json: { code: 200, data: { enabled: false, accessMode: 'DISABLED' } } }))
      await page.addInitScript(theme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, enableMotion: false } })), theme)
      await page.goto('/login')
      await expect(page.locator('.login-shell')).toBeVisible()
      await page.screenshot({ path: test.info().outputPath(`${theme}-login.png`) })
      await page.setViewportSize({ width: 390, height: 844 })
      await page.getByRole('button', { name: '登录系统' }).click()
      await expect(page.getByText('请输入用户名', { exact: true })).toBeVisible()
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      await page.screenshot({ path: test.info().outputPath(`${theme}-login-validation-mobile.png`) })
    })
  }
})
