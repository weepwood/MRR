import { expect, test } from '@playwright/test'

test('影像逐张跨组、故障重试、键盘焦点与普通翻组保持独立', async ({ page }) => {
  let fail = true
  const first = { id: 1, archiveId: 1, pages: 8, uploadFlag: 1, btype: 1 }
  const second = { id: 2, archiveId: 1, pages: 15, uploadFlag: 1, btype: 2 }
  await page.route('**/api/v1/system/data-relations/archives/1/scans?*', async (route) => {
    const after = new URL(route.request().url()).searchParams.get('afterId')
    if (after && fail) {
      await route.fulfill({ status: 503, json: { code: 503, message: 'synthetic scan page outage' } })
      return
    }
    await route.fulfill({ json: { code: 200, data: {
      archiveId: 1,
      rows: after ? [second] : [first],
      nextCursor: after ? null : { pages: first.pages, id: first.id },
    } } })
  })
  await page.goto('/archive-workbench')
  const search = page.getByRole('textbox', { name: '病案查询值' })
  await search.fill('26070000')
  await search.press('Enter')
  const samples = page.getByRole('group', { name: '有效影像列表' })
  const firstRow = samples.getByRole('button').filter({ hasText: '影像 #1' })
  const secondRow = samples.getByRole('button').filter({ hasText: '影像 #2' })
  const image = page.getByAltText('待核对影像')
  await expect(image).toHaveCount(0)
  await firstRow.click()
  await expect(image).toBeVisible()
  await page.getByRole('button', { name: '专注原图' }).click()
  await page.getByRole('button', { name: '下一张', exact: true }).click()
  const error = page.getByRole('alert').filter({ hasText: '影像列表读取失败' })
  await expect(error).toBeVisible()
  await expect(image).toHaveCount(0)
  fail = false
  await error.getByRole('button', { name: '重试', exact: true }).click()
  await expect(secondRow).toHaveAttribute('aria-pressed', 'true')
  await expect(secondRow).toBeFocused()
  await expect(image).toBeVisible()
  await expect.poll(() => image.evaluate((el: HTMLImageElement) => el.complete && el.naturalWidth > 0)).toBe(true)
  await expect(page.getByRole('button', { name: '恢复布局' })).toBeVisible()
  await expect(page.getByRole('button', { name: '下一张', exact: true })).toBeDisabled()
  await page.keyboard.press('ArrowUp')
  await expect(firstRow).toBeFocused()
  await expect(firstRow).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByRole('button', { name: '恢复布局' })).toBeVisible()
  await page.keyboard.press('ArrowDown')
  await expect(secondRow).toBeFocused()
  await expect(secondRow).toHaveAttribute('aria-pressed', 'true')
  await page.getByRole('button', { name: '上一页', exact: true }).click()
  await expect(firstRow).toHaveAttribute('aria-pressed', 'false')
  await expect(image).toHaveCount(0)
})
