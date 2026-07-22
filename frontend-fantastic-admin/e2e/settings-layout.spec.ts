import { expect, test } from '@playwright/test'

test.describe('系统设置分类布局', () => {
  test('按功能分类切换设置内容', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/settings', { waitUntil: 'domcontentloaded' })

    await expect(page.locator('.settings-shell')).toBeVisible({ timeout: 20_000 })
    await expect(page.locator('.settings-tabs')).toHaveCount(0)

    for (const label of ['系统信息', '登录与支持', '档案浏览', '访问安全', '开发者模式', '科室配色', '界面外观']) {
      await expect(page.locator('.settings-nav-item').filter({ hasText: label })).toBeVisible()
    }

    await page.locator('.settings-nav-item').filter({ hasText: '登录与支持' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '登录与支持' })).toBeVisible()
    await expect(page.getByText('系统管理员与技术支持', { exact: true })).toBeVisible()

    await page.locator('.settings-nav-item').filter({ hasText: '档案浏览' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '档案浏览' })).toBeVisible()
    await expect(page.getByText('默认图片来源', { exact: true })).toBeVisible()

    await page.locator('.settings-nav-item').filter({ hasText: '访问安全' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '访问安全' })).toBeVisible()
    await expect(page.getByText('每日允许 IP 切换次数', { exact: true })).toBeVisible()

    await page.locator('.settings-nav-item').filter({ hasText: '开发者模式' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '开发者模式' })).toBeVisible()
    await expect(page.getByText('兼容旧版影像档案袋调用', { exact: true })).toBeVisible()
    await expect(page.getByText('允许访问的客户端 IP / 网段', { exact: true })).toBeVisible()
    await expect(page.getByText('匿名只读范围', { exact: true })).toBeVisible()
    await expect(page.getByText('真实账号审计', { exact: true })).toBeVisible()

    await page.locator('.settings-nav-item').filter({ hasText: '界面外观' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '界面外观' })).toBeVisible()
    await expect(page.getByText('主题与页面样式', { exact: true })).toBeVisible()
  })

  test('缩略图宽度和水印透明度滑块具有可操作滑轨', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/settings?section=archive', { waitUntil: 'domcontentloaded' })

    const thumbnailSlider = page.getByRole('slider', { name: '缩略图宽度' })
    await expect(thumbnailSlider).toBeVisible({ timeout: 20_000 })
    const thumbnailRail = thumbnailSlider.locator('xpath=ancestor::*[contains(concat(" ", normalize-space(@class), " "), " el-slider ")][1]')
    await expect.poll(async () => (await thumbnailRail.boundingBox())?.width ?? 0).toBeGreaterThan(100)

    await page.locator('.settings-nav-item').filter({ hasText: '访问安全' }).click()
    const watermarkSlider = page.getByRole('slider', { name: '水印透明度' })
    await expect(watermarkSlider).toBeVisible()
    const watermarkRail = watermarkSlider.locator('xpath=ancestor::*[contains(concat(" ", normalize-space(@class), " "), " el-slider ")][1]')
    await expect.poll(async () => (await watermarkRail.boundingBox())?.width ?? 0).toBeGreaterThan(100)
  })

  test('输入框和下拉框高度一致并响应统一控件圆角令牌', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/users', { waitUntil: 'domcontentloaded' })

    const filterBar = page.locator('.users-filter-bar')
    const input = filterBar.locator('.el-input__wrapper').first()
    const select = filterBar.locator('.el-select__wrapper').first()
    await expect(input).toBeVisible({ timeout: 20_000 })
    await expect(select).toBeVisible()

    const initial = await page.evaluate(() => {
      const inputElement = document.querySelector('.users-filter-bar .el-input__wrapper')
      const selectElement = document.querySelector('.users-filter-bar .el-select__wrapper')
      if (!inputElement || !selectElement) throw new Error('筛选控件未渲染')
      return {
        inputHeight: inputElement.getBoundingClientRect().height,
        selectHeight: selectElement.getBoundingClientRect().height,
        inputRadius: Number.parseFloat(getComputedStyle(inputElement).borderRadius),
        selectRadius: Number.parseFloat(getComputedStyle(selectElement).borderRadius),
      }
    })
    expect(Math.abs(initial.inputHeight - initial.selectHeight)).toBeLessThanOrEqual(1)
    expect(initial.inputRadius).toBeGreaterThan(0)
    expect(initial.selectRadius).toBeGreaterThan(0)

    await page.evaluate(() => document.documentElement.style.setProperty('--mrr-control-radius', '18px'))
    await expect.poll(async () => Number.parseFloat(await input.evaluate(element => getComputedStyle(element).borderRadius))).toBe(18)
    await expect.poll(async () => Number.parseFloat(await select.evaluate(element => getComputedStyle(element).borderRadius))).toBe(18)
  })

  test('日期范围和数字输入框使用对应尺寸令牌', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/settings?section=archive', { waitUntil: 'domcontentloaded' })

    const inputNumber = page.locator('.settings-content .el-input-number').first()
    await expect(inputNumber).toBeVisible({ timeout: 20_000 })
    expect((await inputNumber.boundingBox())?.height ?? 0).toBeGreaterThan(30)

    await page.goto('/logs', { waitUntil: 'domcontentloaded' })
    const dateRange = page.locator('.el-range-editor').first()
    await expect(dateRange).toBeVisible({ timeout: 20_000 })
    expect((await dateRange.boundingBox())?.height ?? 0).toBeGreaterThan(30)
  })

  test('旧登录文案地址跳转到系统设置内部分类', async ({ page }) => {
    await page.goto('/login-settings', { waitUntil: 'domcontentloaded' })
    await expect(page).toHaveURL(/\/settings\?section=login-support/)
    await expect(page.locator('.section-header').getByRole('heading', { name: '登录与支持' })).toBeVisible()
  })

  test('桌面端仅滚动右侧内容并保持左侧分类导航可见', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 720 })
    await page.goto('/settings', { waitUntil: 'domcontentloaded' })

    const frame = page.locator('.settings-workspace-frame')
    const shell = page.locator('.settings-shell')
    const sidebar = page.locator('.settings-sidebar')
    const content = page.locator('.settings-content')
    await expect(frame).toBeVisible({ timeout: 20_000 })
    await expect(shell).toBeVisible()
    await expect(sidebar).toBeVisible()
    await expect(content).toHaveCSS('overflow-y', 'auto')

    await page.locator('.settings-nav-item').filter({ hasText: '界面外观' }).click()
    await page.getByText('工作区组件', { exact: true }).click()
    await page.getByText('主页、版权与其他', { exact: true }).click()
    await expect.poll(async () => content.evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true)

    const initialSidebarBox = await sidebar.boundingBox()
    expect(initialSidebarBox).not.toBeNull()
    await content.evaluate(element => element.scrollTo({ top: element.scrollHeight, behavior: 'auto' }))
    await expect.poll(() => content.evaluate(element => element.scrollTop)).toBeGreaterThan(100)

    const finalSidebarBox = await sidebar.boundingBox()
    expect(finalSidebarBox).not.toBeNull()
    expect(Math.abs(finalSidebarBox!.y - initialSidebarBox!.y)).toBeLessThanOrEqual(1)
    expect(Math.abs(
      (finalSidebarBox!.y + finalSidebarBox!.height)
      - (initialSidebarBox!.y + initialSidebarBox!.height),
    )).toBeLessThanOrEqual(1)
    await expect(sidebar).toBeInViewport()
  })
})
