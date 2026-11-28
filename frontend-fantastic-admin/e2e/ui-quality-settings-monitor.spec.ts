import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { colorScheme: 'light', pageTitleStyle: 'card', enableMotion: false },
    menu: { mode: 'single' },
    layout: { enableMobileAdaptation: true },
  })))
  await page.setViewportSize({ width: 1440, height: 900 })
})

test('设置分组与真实未保存状态，窄屏保持表单及导航', async ({ page }) => {
  await page.goto('/settings?section=system')
  const summary = page.locator('.section-save-summary')
  await expect(summary).toContainText('已与服务器同步')
  await expect(page.locator('.settings-nav-heading')).toHaveText(['常规', '业务与档案', '安全与运行', '个性化'])
  const input = page.getByRole('textbox', { name: '系统名称', exact: true })
  const original = await input.inputValue()
  await input.fill('合成布局测试')
  await expect(summary).toContainText('有未保存的修改')
  await expect(summary).toContainText('1 项字段待保存')
  await page.setViewportSize({ width: 375, height: 812 })
  await expect(page.getByRole('combobox', { name: '设置分类' })).toBeVisible()
  await expect.poll(() => page.locator('.settings-layout').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await input.fill(original)
  await expect(summary).toContainText('已与服务器同步')
})

test('监控首次失败不冒充正常零值，恢复后显示真实结果', async ({ page }) => {
  let fail = true
  await page.route(/\/api\/v1\/system\/(?:database\/overview|data-quality\/(?:summary|issues))(?:\?|$)/, async (route) => {
    if (fail) {
      await route.fulfill({ status: 503, json: { code: 503, message: '合成监控故障' } })
    }
    else {
      await route.fallback()
    }
  })
  await page.goto('/monitoring')
  await expect(page.getByText('尚未取得数据库概览', { exact: true })).toBeVisible()
  await expect(page.getByText('尚未取得数据质量摘要', { exact: true })).toBeVisible()
  const card = page.locator('.mrr-metric-card').filter({ hasText: '严重数据异常' })
  await expect(card.locator('.mrr-metric-card__value')).toHaveText('—')
  await expect(page.getByRole('alert').filter({ hasText: '本次监控刷新未完整完成' })).toBeVisible()
  fail = false
  await page.getByRole('button', { name: '立即刷新' }).click()
  await expect(page.getByText('尚未取得数据库概览', { exact: true })).toHaveCount(0)
  await expect(page.getByText('尚未取得数据质量摘要', { exact: true })).toHaveCount(0)
})

test('响应分析各宽度无页面溢出，刷新失败保留上次数据', async ({ page }) => {
  let fail = false
  await page.route('**/api/v1/response-metrics/analysis?*', async (route) => {
    if (fail) {
      await route.fulfill({ status: 503, json: { code: 503, message: '合成分析故障' } })
    }
    else {
      await route.fallback()
    }
  })
  await page.goto('/response-analysis')
  const stack = page.locator('.analysis-stack')
  await expect(stack.locator('canvas').first()).toBeVisible()
  const previous = await page.locator('.mrr-metric-card__value').first().textContent()
  for (const width of [1920, 1440, 1024, 768, 375]) {
    await page.setViewportSize({ width, height: 900 })
    await expect.poll(() => page.locator('.mrr-page-shell').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
    await expect.poll(() => stack.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  }
  fail = true
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.getByText('刷新失败，保留上次响应分析结果。', { exact: true })).toBeVisible()
  await expect(page.locator('.mrr-metric-card__value').first()).toHaveText(previous!)
  fail = false
  await page.getByRole('button', { name: '重试分析' }).click()
  await expect(page.getByText('刷新失败，保留上次响应分析结果。', { exact: true })).toHaveCount(0)
})

test('科室配色服务器保存失败只标记本地保存，可显式重试', async ({ page }) => {
  let failed = true
  let writes = 0
  await page.route(/\/api\/v1\/settings(?:\?.*)?$/, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: { code: 200, data: { archiveDepartmentThemes: JSON.stringify([{ department: '合成测试科室', folderColor: '#123456', stripColor: '#654321' }]) } } })
    }
    else {
      await route.fallback()
    }
  })
  await page.route('**/api/v1/settings/archiveDepartmentThemes', async (route) => {
    if (route.request().method() !== 'PUT') {
      await route.fallback()
      return
    }
    writes++
    await route.fulfill(failed
      ? { status: 503, json: { code: 503, message: '合成保存失败' } }
      : { json: { code: 200, data: null } })
  })
  await page.goto('/settings?section=department')
  const status = page.locator('.settings-actions .save-status')
  await expect(status).toContainText('科室配色已与服务器同步')
  await page.getByRole('textbox', { name: '档案袋主色值' }).first().fill('#abcdef')
  const save = page.getByRole('button', { name: '保存科室配色' })
  await save.click()
  await expect(status).toContainText('服务器保存失败 · 已保留本地配色')
  await expect(save).toBeEnabled()
  expect(writes).toBe(1)
  failed = false
  await save.click()
  await expect(status).toContainText('科室配色已与服务器同步')
  expect(writes).toBe(2)
})

test('装箱编辑共用双列排版，窄屏保持单列和完整备注', async ({ page }) => {
  await page.goto('/archive-boxes')
  await page.getByRole('button', { name: '新增装箱记录' }).click()
  const dialog = page.getByRole('dialog', { name: '新增装箱记录' })
  const form = dialog.locator('.mrr-edit-form')
  await expect.poll(() => form.evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length)).toBe(2)
  await page.setViewportSize({ width: 375, height: 812 })
  await expect.poll(() => form.evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length)).toBe(1)
  await expect.poll(() => form.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await expect(dialog.getByRole('textbox', { name: '备注' })).toBeVisible()
  await dialog.getByRole('button', { name: '取消' }).click()
  await expect(dialog).not.toBeVisible()
})
