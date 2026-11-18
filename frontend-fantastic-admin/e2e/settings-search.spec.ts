import { expect, test } from '@playwright/test'

test.describe('系统设置搜索', () => {
  test('搜索分类和设置文案，选择结果后定位到设置组', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 720 })
    await page.goto('/settings?section=system', { waitUntil: 'domcontentloaded' })

    const search = page.getByRole('textbox', { name: '搜索设置' })
    await search.fill('  oss  ')
    await expect(page.getByRole('option', { name: /默认图片来源.*档案浏览/ })).toBeVisible()
    await expect(page.getByRole('heading', { name: '系统信息' })).toBeVisible()
    await search.press('Enter')
    await expect(page).toHaveURL(/section=archive/)
    const source = page.getByText('默认图片来源', { exact: true })
    await expect(source).toBeInViewport()
    await expect(source).toBeFocused()
    await expect(search).toHaveValue('')

    await search.fill('档案 缩略图')
    await page.getByRole('option', { name: /默认浏览方式.*档案浏览/ }).click()
    await expect(page.getByText('默认浏览方式', { exact: true })).toBeFocused()

    await search.fill('访问安全')
    await page.getByRole('option', { name: '访问安全', exact: true }).click()
    await expect(page.getByRole('heading', { name: '访问安全' })).toBeFocused()
  })

  test('窄屏下提示无匹配结果，并打开界面外观中折叠的设置组', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 720 })
    await page.addInitScript(() => {
      localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ layout: { enableMobileAdaptation: true } }))
    })
    await page.goto('/settings', { waitUntil: 'domcontentloaded' })

    const search = page.getByRole('textbox', { name: '搜索设置' })
    await search.fill('不存在的设置')
    const emptyState = page.getByRole('complementary', { name: '设置目录' }).getByRole('status')
    const categories = page.getByRole('combobox', { name: '设置分类' })
    await expect(emptyState).toHaveText('未找到匹配的设置')
    await expect(categories).toBeInViewport()
    await search.press('Enter')
    await expect(page).toHaveURL(/\/settings$/)

    await search.fill('色弱模式')
    await expect(page.getByRole('option', { name: /主页、版权与其他.*界面外观/ })).toBeVisible()
    await search.press('Escape')
    await expect(page.getByRole('option')).toHaveCount(0)
    await search.fill('色弱')
    await expect(emptyState).toBeEmpty()
    await expect(page.getByRole('option', { name: /主页、版权与其他.*界面外观/ })).toBeVisible()
    await search.press('Enter')
    await expect(page).toHaveURL(/section=appearance/)
    await expect(page.getByText('色弱模式', { exact: true })).toBeVisible()
    await expect(page.getByText('主页、版权与其他', { exact: true })).toBeInViewport()

    await page.locator('.settings-category-select').click()
    await page.getByRole('option', { name: '系统信息', exact: true }).click()
    await expect(page.getByRole('heading', { name: '系统信息', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '恢复默认', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '保存设置', exact: true })).toBeVisible()
  })

  test.describe('匿名访问', () => {
    test.use({ storageState: { cookies: [], origins: [] } })

    test('沿用系统设置的登录守卫', async ({ page }) => {
      await page.goto('/settings?section=archive', { waitUntil: 'domcontentloaded' })
      await expect(page).toHaveURL(/\/login(?:\?|$)/)
      await expect(page.getByRole('textbox', { name: '搜索设置' })).toHaveCount(0)
    })
  })
})
