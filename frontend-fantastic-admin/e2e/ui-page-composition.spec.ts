import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { pageTitleStyle: 'card', colorScheme: 'light', enableMotion: false },
    menu: { mode: 'single' },
    layout: { enableMobileAdaptation: true },
  })))
  await page.setViewportSize({ width: 1440, height: 900 })
})

test('运维当前问题优先于历史趋势，窄屏不丢失状态和操作', async ({ page }) => {
  await page.goto('/operations-center')
  const priority = page.getByRole('region', { name: '当前问题与运行状态' })
  const history = page.getByRole('heading', { name: '运行历史与容量趋势' })
  await expect(priority.getByRole('heading', { name: '待处理问题' })).toBeVisible()
  await expect(history).toBeVisible()
  for (const width of [1440, 375]) {
    await page.setViewportSize({ width, height: 900 })
    await expect.poll(async () => (await priority.boundingBox())!.y < (await history.boundingBox())!.y).toBe(true)
    await expect.poll(() => priority.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
    await expect(page.getByRole('button', { name: '刷新历史' })).toBeVisible()
  }
})

test('用户创建表单双列与窄屏单列、校验消息不遮挡下一字段', async ({ page }) => {
  let writes = 0
  await page.route('**/api/v1/auth/users*', async (route) => {
    if (route.request().method() === 'POST') {
      writes++
      await route.abort()
      return
    }
    await route.fallback()
  })
  await page.goto('/users')
  await page.getByRole('button', { name: '创建用户', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '创建用户' })
  const form = dialog.locator('.mrr-edit-form')
  const user = dialog.getByRole('textbox', { name: '用户名' })
  await expect(user).toBeVisible()
  await expect.poll(() => form.evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length)).toBe(2)
  await dialog.getByRole('button', { name: '创建用户', exact: true }).click()
  await expect(form.locator('.el-form-item__error').first()).toBeVisible()
  expect(writes).toBe(0)
  await page.setViewportSize({ width: 375, height: 812 })
  await expect.poll(() => form.evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length)).toBe(1)
  await expect.poll(() => form.evaluate((el) => {
    const fields = Array.from(el.querySelectorAll('.el-form-item'))
    return fields.every((field, i) => !fields[i + 1] || field.getBoundingClientRect().bottom <= fields[i + 1].getBoundingClientRect().top + 1)
      && el.scrollWidth <= el.clientWidth + 1
  })).toBe(true)
  await dialog.getByRole('button', { name: '取消' }).click()
  await expect(dialog).not.toBeVisible()
})

test('患者编辑按三组组织，验证失败不发送更新', async ({ page }) => {
  let writes = 0
  await page.route('**/api/v1/patients/*', async (route) => {
    if (route.request().method() === 'PUT') {
      writes++
      await route.abort()
      return
    }
    await route.fallback()
  })
  await page.goto('/patients')
  await page.getByRole('button', { name: '编辑患者', exact: true }).first().click()
  const dialog = page.getByRole('dialog', { name: '编辑患者信息' })
  const form = dialog.locator('.mrr-edit-form')
  await expect(form.getByRole('heading')).toHaveText(['患者基本信息', '入院信息', '科室与床位'])
  await expect.poll(() => form.evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length)).toBe(2)
  await dialog.getByRole('textbox', { name: /病案号$/ }).fill('')
  await dialog.getByRole('button', { name: '保存修改' }).click()
  await expect(dialog.getByText('请输入病案号', { exact: true })).toBeVisible()
  expect(writes).toBe(0)
  await page.setViewportSize({ width: 375, height: 812 })
  await expect.poll(() => form.evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length)).toBe(1)
  await expect.poll(() => form.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await expect(dialog.getByRole('button', { name: '取消' })).toBeVisible()
  await dialog.getByRole('button', { name: '取消' }).click()
  await expect(dialog).not.toBeVisible()
})

test('关系概览与统计图表在宽窄屏保留真实统计口径', async ({ page }) => {
  await page.goto('/data-relations')
  const relation = page.getByRole('region', { name: '各数据表关联覆盖率' })
  await expect(relation.locator('.relation-card').first()).toBeVisible()
  const status = relation.locator('.mrr-status-tag').first()
  await expect(status).toHaveAttribute('title', /HEALTHY|WARNING|CRITICAL|LEGACY|UNKNOWN/)
  await expect(status).not.toHaveText(/^(?:HEALTHY|WARNING|CRITICAL|LEGACY|UNKNOWN)$/)
  await page.goto('/statistics')
  const trend = page.locator('.mrr-chart-card').first()
  await expect(trend.locator('canvas').first()).toBeVisible()
  await expect(trend.locator('[aria-label="全部已返回统计日摘要"]')).toBeVisible()
  await expect(page.locator('.statistics-range-metric .mrr-metric-card__value')).toHaveCSS('white-space', 'pre-line')
  for (const width of [1440, 768, 375]) {
    await page.setViewportSize({ width, height: 900 })
    await expect.poll(() => trend.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  }
})
