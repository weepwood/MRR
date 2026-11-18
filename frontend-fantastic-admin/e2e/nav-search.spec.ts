import { expect, test } from '@playwright/test'

test('使用拼音首字母搜索导航并打开影像档案袋', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('button', { name: /^搜索/ })).toBeVisible()
  await page.keyboard.press('Control+k')

  const dialog = page.getByRole('dialog')
  const search = dialog.getByRole('textbox', { name: '搜索页面' })
  const archive = dialog.getByText('影像档案袋', { exact: true })

  await expect(search).toBeFocused()
  await search.fill('yx')
  await expect(archive).toBeVisible()

  await search.fill(' YXDAD ')
  await expect(archive).toBeVisible()

  await search.fill('lsyx')
  await expect(dialog.getByText('历史影像识别', { exact: true })).toBeVisible()
  await expect(archive).toHaveCount(0)

  for (const keyword of ['档案袋', '/archive/embed']) {
    await search.fill(keyword)
    await expect(archive).toBeVisible()
  }

  await search.fill('zzzzzz')
  await expect(dialog.getByText('没有找到你想要的')).toBeVisible()

  await search.fill('yx')
  await search.press('Enter')
  await expect(page).toHaveURL(/\/archive\/embed$/)
})

test('拼音搜索仅显示当前账号有权访问的菜单', async ({ page }) => {
  await page.route('**/api/v1/auth/me', route => route.fulfill({
    json: {
      code: 200,
      data: { id: 20, username: 'navigation-viewer', status: 'active', permissions: ['system:read'] },
    },
  }))
  await page.goto('/')
  await page.getByRole('button', { name: /^搜索/ }).click()

  const dialog = page.getByRole('dialog')
  const search = dialog.getByRole('textbox', { name: '搜索页面' })
  await search.fill('yx')
  await expect(dialog.getByText('没有找到你想要的')).toBeVisible()
})
