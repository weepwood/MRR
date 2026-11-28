import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { colorScheme: 'dark', enableMotion: false, pageTitleStyle: 'card' },
    menu: { mode: 'single' },
    layout: { enableMobileAdaptation: true },
  })))
  await page.setViewportSize({ width: 1440, height: 900 })
})

test('展厅编辑与批量操作使用实际组件，校验失败保留对话框', async ({ page }) => {
  await page.goto('/ui-components')
  await page.getByLabel('查找组件', { exact: true }).fill('业务编辑与批量操作')
  const trigger = page.getByRole('button', { name: '编辑业务示例', exact: true })
  await trigger.click()
  const dialog = page.getByRole('dialog', { name: '编辑业务示例' })
  await dialog.getByRole('button', { name: '保存示例' }).click()
  await expect(dialog.getByText('请输入演示名称')).toBeVisible()
  await dialog.getByRole('textbox', { name: /演示名称/ }).fill('演示名称 A')
  await dialog.getByRole('button', { name: '保存示例' }).click()
  await expect(dialog).not.toBeVisible()
  await expect(trigger).toBeFocused()
  await expect(page.getByText('已保存 演示名称 A（仅示例）')).toBeVisible()
  const batch = page.getByRole('button', { name: /批量处理/ })
  await expect(batch).toBeDisabled()
  await page.getByRole('checkbox', { name: '演示记录 A' }).focus()
  await page.getByRole('checkbox', { name: '演示记录 A' }).press('Space')
  await expect(batch).toBeEnabled()
  await batch.click()
  await expect(page.getByText('已处理 1 项（仅示例）')).toBeVisible()
  await expect(batch).toBeDisabled()
})

test('展厅图表真实渲染，支持范围与空态切换', async ({ page }) => {
  await page.goto('/ui-components')
  await page.getByLabel('查找组件', { exact: true }).fill('图表与时间范围')
  await expect(page.locator('.gallery-preview canvas').first()).toBeVisible()
  await page.locator('.demo-trend-range .el-select__wrapper').click()
  await page.getByRole('option', { name: '全部记录日' }).click()
  await page.getByRole('switch', { name: '模拟无图表数据' }).focus()
  await page.getByRole('switch', { name: '模拟无图表数据' }).press('Space')
  await expect(page.locator('.gallery-preview .mrr-chart.is-empty')).toHaveCount(2)
  await page.setViewportSize({ width: 375, height: 900 })
  await expect.poll(() => page.locator('.gallery-preview').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
})

test('响应分析范围选择和设置开关键盘访问', async ({ page }) => {
  await page.goto('/response-analysis')
  const range = page.locator('.trend-range .el-select__wrapper')
  await range.click()
  await page.getByRole('option', { name: '全部记录日' }).click()
  await expect(page.locator('.trend-range')).toContainText('全部记录日')
  await page.goto('/settings?section=security')
  const toggle = page.getByRole('switch', { name: '显示访问水印', exact: true })
  await expect(page.locator('.switch-row').filter({ hasText: '显示访问水印' }).locator('.el-switch')).toBeVisible()
  await toggle.focus()
  await expect(toggle).toBeFocused()
  await page.setViewportSize({ width: 375, height: 900 })
  await expect.poll(() => page.locator('.settings-layout').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
})
