import { expect, test } from '@playwright/test'

test.describe('系统设置分类布局', () => {
  test('按功能分类切换设置内容', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/settings', { waitUntil: 'domcontentloaded' })

    await expect(page.locator('.settings-shell')).toBeVisible({ timeout: 20_000 })
    await expect(page.locator('.settings-tabs')).toHaveCount(0)

    for (const label of ['基础设置', '档案浏览', '访问安全', '科室配色', '界面外观']) {
      await expect(page.locator('.settings-nav-item').filter({ hasText: label })).toBeVisible()
    }

    await expect(page.locator('.section-header').getByRole('heading', { name: '基础设置' })).toBeVisible()
    await expect(page.getByText('默认图片来源', { exact: true })).toBeVisible()

    await page.locator('.settings-nav-item').filter({ hasText: '档案浏览' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '档案浏览' })).toBeVisible()
    await expect(page.getByText('自动适应预览区域', { exact: true })).toBeVisible()

    await page.locator('.settings-nav-item').filter({ hasText: '访问安全' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '访问安全' })).toBeVisible()
    await expect(page.getByText('每日允许 IP 切换次数', { exact: true })).toBeVisible()

    await page.locator('.settings-nav-item').filter({ hasText: '界面外观' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '界面外观' })).toBeVisible()
    await expect(page.getByText('主题与页面样式', { exact: true })).toBeVisible()
    await expect(page.getByText('导航与顶栏', { exact: true })).toBeVisible()
  })

  test('桌面端滚动右侧内容时分类导航保持在视口内', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 720 })
    await page.goto('/settings', { waitUntil: 'domcontentloaded' })

    const sidebar = page.locator('.settings-sidebar')
    await expect(sidebar).toBeVisible({ timeout: 20_000 })
    await expect(sidebar).toHaveCSS('position', 'sticky')

    await page.locator('.settings-nav-item').filter({ hasText: '界面外观' }).click()
    await page.getByText('工作区组件', { exact: true }).click()
    await page.getByText('主页、版权与其他', { exact: true }).click()

    const initialBox = await sidebar.boundingBox()
    expect(initialBox).not.toBeNull()

    await page.evaluate(() => window.scrollTo({ top: 700, behavior: 'auto' }))
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(300)

    await expect(sidebar).toBeInViewport()
    await expect(page.locator('.settings-nav-item').filter({ hasText: '基础设置' })).toBeInViewport()
    await expect(page.locator('.settings-nav-item').filter({ hasText: '界面外观' })).toBeInViewport()

    const stickyBox = await sidebar.boundingBox()
    expect(stickyBox).not.toBeNull()
    expect(stickyBox!.top).toBeGreaterThanOrEqual(0)
    expect(stickyBox!.bottom).toBeLessThanOrEqual(720)
    expect(stickyBox!.top).toBeLessThan(initialBox!.top)
  })
})
