import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const [palette, theme] of [['mrr', 'light'], ['mrr', 'dark'], ['gr8r', 'light'], ['gr8r', 'dark']] as const) {
  test(`${palette}/${theme} 侧栏等高与个人设置弹窗`, async ({ page }, info) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { themePalette: palette, colorScheme: theme, enableMotion: false }, menu: { mode: 'single' }, layout: { enableMobileAdaptation: true } })), { palette, theme })
    await page.goto('/')
    const account = page.locator('.account-area .account-trigger')
    await expect(account).toBeVisible()
    const height = await page.locator('#mrr-sub-navigation').evaluate(el => ({
      top: el.querySelector('.sidebar-logo')!.getBoundingClientRect().height,
      bottom: el.querySelector('.account-area')!.getBoundingClientRect().height,
    }))
    expect(height.bottom).toBe(height.top)
    await account.click()
    await page.getByRole('menuitem', { name: '个人设置' }).click()
    const dialog = page.getByRole('dialog', { name: '个人设置' })
    await expect(dialog).toBeVisible()
    await expect(dialog.locator('input[type="password"]')).toHaveCount(3)
    await expect(dialog.locator('.password-settings__title')).toHaveCSS('font-size', '16px')
    await dialog.getByRole('button', { name: '保存', exact: true }).click()
    await expect(dialog.getByText('请输入原密码', { exact: true })).toBeVisible()
    for (const width of [1440, 375]) {
      await page.setViewportSize({ width, height: 850 })
      const rect = await dialog.locator('.el-dialog').count() ? await dialog.locator('.el-dialog').boundingBox() : await dialog.boundingBox()
      expect(rect!.x).toBeGreaterThanOrEqual(0)
      expect(rect!.x + rect!.width).toBeLessThanOrEqual(width + 1)
      await page.screenshot({ path: info.outputPath(`profile-${width}.png`) })
    }
    await dialog.locator('.el-dialog__headerbtn').click()
    await expect(dialog).toBeHidden()
  })
}

test('查询记录独立标题、筛选条、关闭按钮及历史隐私', async ({ page }, info) => {
  await isolateLocalVisualSettings(page)
  await page.route('**/api/v1/archive-search-history**', route => route.fulfill({ json: { code: 200, data: [{ id: 1, bah: '94001', sjh: '1234', imageCount: 0, queryCount: 0, success: false, searchedAt: new Date(Date.now() - 1000).toISOString(), favorite: false }] } }))
  await page.goto('/archive/embed')
  const all = page.getByRole('button', { name: '全部记录', exact: true })
  await expect(all).toBeVisible()
  await all.click()
  const dialog = page.getByRole('dialog', { name: '查询记录' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText('暂无成功查询记录')).toBeVisible()
  const header = dialog.locator('.el-dialog__header')
  const filters = dialog.getByRole('group', { name: '筛选搜索记录' })
  const close = dialog.locator('.el-dialog__headerbtn')
  for (const width of [1440, 375]) {
    await page.setViewportSize({ width, height: 850 })
    const h = (await header.boundingBox())!
    const f = (await filters.boundingBox())!
    const c = (await close.boundingBox())!
    expect(f.y).toBeGreaterThanOrEqual(h.y + h.height)
    expect(f.y).toBeGreaterThanOrEqual(c.y + c.height)
    await dialog.getByRole('button', { name: /失败查询/ }).click()
    await expect(dialog.getByText('病案号 94001')).toBeVisible()
    await filters.getByRole('button', { name: /收藏病案/ }).click()
    await expect(dialog.getByText('暂无收藏病案')).toBeVisible()
    await page.screenshot({ path: info.outputPath(`history-${width}.png`) })
  }
  await close.click()
  await expect(dialog).toBeHidden()
})
