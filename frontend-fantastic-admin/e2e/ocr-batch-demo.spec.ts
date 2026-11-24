import { expect, test } from '@playwright/test'

test('真实 Mock 历史批次预览、创建与共享子任务', async ({ page }) => {
  await page.goto('/ocr-batches')
  await expect(page.getByRole('heading', { name: '历史未分类影像识别' })).toBeVisible()
  await page.getByRole('button', { name: '预览范围', exact: true }).click()
  await expect(page.getByText(/扫描上界 64/)).toBeVisible()
  await expect(page.getByRole('checkbox', { name: /授权本批次/ })).toBeDisabled()
  await page.getByRole('button', { name: '创建此范围的识别批次' }).click()
  const row = page.getByRole('row').filter({ has: page.getByRole('cell', { name: 'demo-batch-1', exact: true }) })
  await expect(row).toBeVisible()
  await expect(row.getByText(/4\s*\/\s*4/)).toBeVisible({ timeout: 30000 })
  await row.getByRole('button', { name: '查看明细与审核' }).click()
  await expect(page.getByRole('heading', { name: '批次任务与人工审核' })).toBeVisible()
  await expect(page.getByText('demo-ocr-1', { exact: true }).first()).toBeVisible()
})
