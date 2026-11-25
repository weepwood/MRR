import { expect, test } from '@playwright/test'

test('检索与详情独立加载，修改条件取消旧结果且清空恢复焦点', async ({ page }) => {
  let releaseDetail!: () => void
  const gate = new Promise<void>((resolve) => { releaseDetail = resolve })
  let detailRequests = 0
  await page.route('**/api/v1/system/data-relations/archives/1', async (route) => {
    detailRequests++
    if (detailRequests === 1) { await gate }
    await route.fallback().catch(() => {})
  })
  await page.goto('/archive-workbench')
  const search = page.getByRole('textbox', { name: '病案查询值' })
  await search.fill('26070000')
  await search.press('Enter')
  const candidate = page.getByRole('button', { name: '查看病案主档 1 的关系' })
  try {
    await expect(candidate).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByText('正在加载病案主档 #1 的关系…')).toBeVisible()
    await expect(page.getByRole('button', { name: '查询', exact: true })).toBeEnabled()
    await candidate.click()
    expect(detailRequests).toBe(1)
    await search.fill('26070001')
    await expect(candidate).toHaveCount(0)
    await expect(page.getByText('正在加载病案主档 #1 的关系…')).toHaveCount(0)
    await search.press('Enter')
    await expect(page.getByText('病案主档 #2', { exact: true })).toBeVisible()
  }
  finally {
    releaseDetail()
  }
  await expect(page.getByText('病案主档 #1', { exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '清空检索' }).click()
  await expect(search).toHaveValue('')
  await expect(search).toBeFocused()
  await expect(page.getByText('病案主档 #2', { exact: true })).toHaveCount(0)
  await expect(page.getByText('开始病案核对')).toBeVisible()
})
