import { Buffer } from 'node:buffer'
import { expect, test } from '@playwright/test'

const reply = (data: unknown) => ({ code: 200, message: 'success', data })

const TINY_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
)

async function mockArchive(page: import('@playwright/test').Page, history: unknown[] = []) {
  await page.route('**/mock/scan-*.png', route => route.fulfill({ status: 200, contentType: 'image/png', body: TINY_PNG }))
  await page.route('**/api/v1/img/search?**', route => route.fulfill({
    json: reply([1, 2].map(page => ({
      id: 94000 + page,
      brxh: 'SYNTH-94000',
      bah: '00009401',
      sjh: '',
      filename: `synthetic-${page}.png`,
      btype: 3,
      pages: 1,
      uploadFlag: 1,
      img_url: `/mock/scan-${page}.png`,
    }))),
  }))
  await page.route(/\/api\/v1\/search\/patient\/[^/?]+/, route => route.fulfill({
    json: reply({ bah: '00009401', name: '合成患者', department: '合成科室' }),
  }))
  await page.route('**/api/v1/archive-search-history**', route => route.fulfill({
    json: reply(route.request().method() === 'GET' ? history : {}),
  }))
}

test('未查询时：字段有可见标签与规则说明，不显示刷新，最近查询可直接打开', async ({ page }) => {
  const searchedAt = new Date(Date.now() - 60_000).toISOString()
  await mockArchive(page, [
    { id: 1, bah: '00009401', sjh: '', success: true, imageCount: 2, queryCount: 3, favorite: false, searchedAt },
    { id: 2, bah: '00009402', sjh: '', success: false, imageCount: 0, queryCount: 0, favorite: false, searchedAt },
  ])
  await page.goto('/archive')

  await expect(page.getByRole('heading', { name: '住院病案' })).toBeVisible({ timeout: 20_000 })
  const searchCard = page.locator('.search-card')
  await expect(searchCard).toHaveClass(/is-labelled/)
  await expect(searchCard.locator('.search-field__label')).toHaveText(['病案号', '上架号', '身份证号'])
  await expect(searchCard.locator('.search-hint')).toContainText('10000000')
  await expect(page.getByRole('button', { name: '刷新', exact: true })).toHaveCount(0)

  // 病案号 ≥ 10000000 时上架号标签提示必填。
  await page.locator('input[name="archive-bah"]').fill('10000001')
  await expect(searchCard.locator('.search-field__label').nth(1)).toContainText('（必填）')
  await page.locator('input[name="archive-bah"]').fill('')

  const recent = page.locator('.archive-empty-recent')
  await expect(recent.getByRole('heading', { name: '最近查询', level: 3 })).toBeVisible()
  const rows = recent.locator('.recent-lookups__item')
  await expect(rows).toHaveCount(1)
  await expect(rows.first()).toContainText('00009401')

  await rows.first().click()
  await expect.poll(() => new URL(page.url()).searchParams.get('bah')).toBe('00009401')
  await expect(page.locator('.preview-stage img.preview-image')).toBeVisible()

  // 查询后回到紧凑侧栏：标签与说明收起，刷新与最近查询弹层入口恢复。
  await expect(searchCard).not.toHaveClass(/is-labelled/)
  await expect(searchCard.locator('.search-hint')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '刷新', exact: true })).toBeVisible()
})

test('底部操作区：选中数量只显示一次，选择前三项导出禁用，整份下载始终可用', async ({ page }) => {
  await mockArchive(page)
  await page.goto('/archive?bah=00009401')

  const actions = page.locator('.archive-bottom-actions')
  await expect(actions).toBeVisible({ timeout: 20_000 })
  await expect(actions.locator('.archive-selection-count')).toHaveText(/已选\s*0\s*张/)

  const selection = actions.getByRole('group', { name: /对已选 0 张影像的操作/ })
  for (const name of [/导出选中 ZIP（0）/, /打印选中页面（0）/, /导出选中 PDF（0）/]) {
    await expect(selection.getByRole('button', { name })).toBeDisabled()
  }
  await expect(actions.getByRole('button', { name: /下载病案/ })).toBeEnabled()

  await page.locator('.thumb-check').first().click()
  await expect(actions.locator('.archive-selection-count')).toHaveText(/已选\s*1\s*张/)
  await expect(actions.getByRole('button', { name: /导出选中 ZIP（1）/ })).toBeEnabled()
  await expect(actions.getByRole('button', { name: /打印选中页面（1）/ })).toBeEnabled()
  await expect(actions.getByRole('button', { name: /导出选中 PDF（1）/ })).toBeEnabled()

  // 三个按钮与整份下载都在 320px 侧栏内，不溢出。
  const overflow = await actions.evaluate((element) => {
    const box = element.getBoundingClientRect()
    return [...element.querySelectorAll('.el-button')].some((button) => {
      const rect = button.getBoundingClientRect()
      return rect.left < box.left - 1 || rect.right > box.right + 1
    })
  })
  expect(overflow).toBe(false)
})
