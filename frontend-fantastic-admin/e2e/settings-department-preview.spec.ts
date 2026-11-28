import { expect, test } from '@playwright/test'
import { resolveArchiveDepartmentTheme } from '../src/utils/archive-department-theme'

test('默认展示实际自动配色，主动自定义才产生草稿，不自动保存', async ({ page }) => {
  const names = ['内科', '外科', '儿科']
  await page.route('**/api/v1/statistics/departments', route => route.fulfill({ json: { code: 200, data: names } }))
  let writes = 0
  page.on('request', (request) => {
    if (request.method() !== 'GET' && /\/api\/v1\/settings/.test(request.url())) { writes++ }
  })
  await page.goto('/settings?section=department')
  const save = page.getByRole('button', { name: '保存科室配色', exact: true })
  await expect(page.locator('.department-preview-item')).toHaveCount(names.length)
  await expect(save).toBeDisabled()
  for (const name of names) {
    const card = page.locator('.department-preview-item').filter({ hasText: name })
    const theme = resolveArchiveDepartmentTheme(name, [])
    await expect(card).toContainText(theme.folderColor)
    await expect(card).toContainText(theme.stripColor)
    await expect(card).toContainText('自动配色')
    const rgb = (hex: string) => `rgb(${[1, 3, 5].map(i => Number.parseInt(hex.slice(i, i + 2), 16)).join(', ')})`
    await expect(card.locator('.archive-theme-preview > i')).toHaveCSS('background-color', rgb(theme.stripColor))
    await expect(card.locator('.folder-color-swatch')).toHaveCSS('background-color', rgb(theme.folderColor))
  }
  const first = page.locator('.department-preview-item').first()
  await first.getByRole('button', { name: '自定义 内科' }).click()
  await expect(page.locator('.theme-editor-row')).toHaveCount(1)
  await expect(page.getByText('待保存配色预览', { exact: true })).toBeVisible()
  await expect(save).toBeEnabled()
  await page.locator('.theme-editor-row').getByRole('button', { name: '删除' }).click()
  await expect(save).toBeDisabled()
  expect(writes).toBe(0)
})

test('科室接口失败明确显示参考色板，不生成虚假科室记录', async ({ page }) => {
  await page.route('**/api/v1/statistics/departments', route => route.fulfill({ status: 500, json: { code: 500, message: '合成失败' } }))
  await page.goto('/settings?section=department')
  await expect(page.getByText('科室列表读取失败，已配置的科室仍可预览。')).toBeVisible()
  await expect(page.locator('.department-preview-grid.is-example .department-preview-item')).toHaveCount(8)
  await expect(page.locator('.theme-editor-row')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '保存科室配色' })).toBeDisabled()
})
