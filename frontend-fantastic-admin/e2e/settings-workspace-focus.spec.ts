import { expect, test } from '@playwright/test'

test.describe('系统设置工作区滚动', () => {
  test('重复搜索定位时只滚动表单，页头和搜索框位置稳定', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 720 })
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto('/settings', { waitUntil: 'domcontentloaded' })

    const title = page.getByRole('heading', { name: '系统设置', exact: true })
    const search = page.getByRole('textbox', { name: '搜索设置' })
    const content = page.locator('.settings-content')
    await expect(title).toBeInViewport()
    await expect(search).toBeInViewport()
    const initialScrollY = await page.evaluate(() => window.scrollY)
    const titleTop = (await title.boundingBox())!.y
    const searchTop = (await search.boundingBox())!.y

    await search.fill('记住选择状态')
    await page.getByRole('option', { name: /记住选择状态.*档案浏览/ }).click()
    const result = page.getByText('记住选择状态', { exact: true })
    await expect(result).toBeFocused()
    await expect(result).toBeInViewport()
    await expect.poll(() => content.evaluate(element => element.scrollTop)).toBeGreaterThan(100)
    expect(await page.evaluate(() => window.scrollY)).toBe(initialScrollY)
    expect((await title.boundingBox())!.y).toBeCloseTo(titleTop, 0)
    expect((await search.boundingBox())!.y).toBeCloseTo(searchTop, 0)
    await expect(page.getByRole('button', { name: '保存设置', exact: true })).toBeInViewport()

    await search.fill('访问安全')
    await expect(page.getByRole('option', { name: '访问安全', exact: true })).toBeVisible()
    await search.press('Enter')
    await expect(page.getByRole('heading', { name: '访问安全', exact: true })).toBeFocused()
    await expect.poll(() => content.evaluate(element => element.scrollTop)).toBe(0)
    expect(await page.evaluate(() => window.scrollY)).toBe(initialScrollY)
  })

  test('横屏短视口保留表单高度和保存入口', async ({ page }) => {
    await page.setViewportSize({ width: 844, height: 390 })
    await page.addInitScript(() => {
      localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ layout: { enableMobileAdaptation: true } }))
    })
    await page.goto('/settings?section=archive', { waitUntil: 'domcontentloaded' })

    const content = page.locator('.settings-content')
    await expect(page.getByRole('navigation', { name: '设置分类' })).toBeInViewport()
    await expect(page.getByRole('button', { name: '保存设置', exact: true })).toBeInViewport()
    await expect.poll(async () => (await content.boundingBox())?.height ?? 0).toBeGreaterThan(100)
  })
})
