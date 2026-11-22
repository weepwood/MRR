import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

/** 打开设置页并等首次读取完成，否则后续改写会被 loadSettings 的返回值覆盖。 */
async function openSettings(page: Page, section: string) {
  await page.goto(`/settings?section=${section}`, { waitUntil: 'domcontentloaded' })
  // 应用启动时也会请求同一个设置接口，不能用接口响应判断页面是否读完，改看内容区的忙碌状态。
  await expect(page.locator('.settings-content[aria-busy="false"]')).toBeVisible({ timeout: 20_000 })
}

test.describe('系统设置分类布局', () => {
  test('分区图标与两行标题说明等高', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await openSettings(page, 'system')

    const icon = page.locator('.section-icon')
    const copy = page.locator('.section-title > div').last()
    await expect.poll(async () => {
      const iconBox = await icon.boundingBox()
      const copyBox = await copy.boundingBox()
      return Math.abs((iconBox?.height ?? 0) - (copyBox?.height ?? 0))
    }).toBeLessThanOrEqual(1)
    await expect.poll(async () => {
      const iconBox = await icon.boundingBox()
      return Math.abs((iconBox?.width ?? 0) - (iconBox?.height ?? 0))
    }).toBeLessThanOrEqual(1)
  })

  test('按功能分类切换设置内容', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/settings', { waitUntil: 'domcontentloaded' })

    await expect(page.getByRole('navigation', { name: '设置分类' })).toBeVisible({ timeout: 20_000 })
    await expect(page.locator('.settings-tabs')).toHaveCount(0)

    for (const label of ['系统信息', '登录与支持', '档案浏览', '访问安全', '开发者模式', '科室档案袋配色', '界面外观']) {
      await expect(page.locator('.settings-nav-item').filter({ hasText: label })).toBeVisible()
    }
    // 左侧目录只显示分类名，不再有第二行说明。
    await expect(page.locator('.settings-nav-item small')).toHaveCount(0)

    await page.locator('.settings-nav-item').filter({ hasText: '登录与支持' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '登录与支持' })).toBeVisible()
    await expect(page.getByText('系统管理员与技术支持', { exact: true })).toBeVisible()
    // 左侧不再展示配置状态，未保存状态只由页头保存按钮的可用性表达。
    await expect(page.locator('.settings-sidebar')).not.toContainText('配置状态')
    await expect(page.locator('.page-header .header-actions').getByRole('button', { name: '保存设置' })).toBeDisabled()

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

  test('界面外观支持键盘切换分组、直接选择主色和查看圆角值', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/settings?section=appearance', { waitUntil: 'domcontentloaded' })

    const groups = page.getByRole('tablist', { name: '外观设置分类' })
    const theme = groups.getByRole('tab', { name: '主题与页面样式' })
    const navigation = groups.getByRole('tab', { name: '导航与顶栏' })
    await expect(theme).toHaveAttribute('aria-selected', 'true')

    const green = page.getByRole('button', { name: '绿色', exact: true })
    await green.click()
    await expect(green).toHaveAttribute('aria-pressed', 'true')
    await expect.poll(() => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--color-primary').trim().toLowerCase())).toBe('#0a8642')

    const radius = page.getByRole('slider', { name: '圆角系数' })
    await radius.press('Home')
    await expect(radius).toHaveAttribute('aria-valuenow', '0')
    await expect(page.locator('.radius-value')).toHaveText('0')

    await theme.press('ArrowRight')
    await expect(navigation).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByRole('button', { name: '顶部导航', exact: true })).toBeVisible()
    await expect(green).toBeHidden()

    await page.getByRole('button', { name: '打开应用外观设置' }).click()
    const drawer = page.getByRole('dialog', { name: '应用外观设置' })
    await expect(drawer.getByRole('tablist', { name: '外观设置分类' })).toBeVisible()
    await drawer.getByRole('tab', { name: '工作区组件' }).click()
    await expect(drawer.getByText('启用工具栏', { exact: true })).toBeVisible()
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
      if (!inputElement || !selectElement) { throw new Error('筛选控件未渲染') }
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
    // 旧地址是路由级 redirect，但要等守卫里的会话校验（GET /api/v1/auth/me）先完成，
    // 并行跑 dev server 时这一步可能超过断言默认的 5s，故显式放宽。
    await expect(page).toHaveURL(/\/settings\?section=login-support/, { timeout: 20_000 })
    await expect(page.locator('.section-header').getByRole('heading', { name: '登录与支持' })).toBeVisible({ timeout: 20_000 })
  })

  test('分类内容以独立设置组铺在页面中并使用页面滚动', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 720 })
    await page.goto('/settings?section=archive', { waitUntil: 'domcontentloaded' })

    const content = page.locator('.settings-content')
    await expect(page.locator('.settings-page > .page-header')).toBeInViewport()
    await expect(page.getByRole('navigation', { name: '设置分类' })).toBeInViewport()
    await expect(content).toHaveCSS('overflow-y', 'visible')
    await expect(content.getByText('默认图片来源', { exact: true })).toBeVisible()
    await expect(content.getByText('默认浏览方式', { exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: '目录路由规则' })).toBeVisible()

    // 保存入口固定在页头，排序为「重新加载 → 恢复默认 → 保存设置」，页面底部不再有保存行。
    await expect(page.locator('.page-header .header-actions').getByRole('button')).toHaveText(['重新加载', '恢复默认', '保存设置'])
    await expect(page.locator('.settings-save-action')).toHaveCount(0)

    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
    expect(await page.evaluate(() => window.scrollY)).toBeGreaterThan(100)
    expect(await content.evaluate(element => element.scrollTop)).toBe(0)
  })

  test('有未保存的设置时切换页面先提示保存', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await openSettings(page, 'system')

    const systemName = page.getByRole('textbox', { name: '系统名称', exact: true })
    await expect(systemName).toBeVisible({ timeout: 20_000 })
    const originalName = await systemName.inputValue()
    await systemName.fill(`${originalName}-临时`)
    await expect(page.locator('.page-header .header-actions').getByRole('button', { name: '保存设置' })).toBeEnabled()

    const userMenu = page.locator('.menu-item-container').filter({ hasText: '用户管理' }).first()
    const dialog = page.getByRole('dialog', { name: '有未保存的设置' })

    // 取消：留在设置页，修改仍在
    await userMenu.click()
    await expect(dialog).toBeVisible()
    await expect(dialog).toContainText('尚未保存')
    await dialog.getByRole('button', { name: '取消', exact: true }).click()
    await expect(dialog).toHaveCount(0)
    await expect(page).toHaveURL(/\/settings/)
    await expect(systemName).toHaveValue(`${originalName}-临时`)

    // 放弃修改：直接离开，且返回后表单必须真的回滚。
    await userMenu.click()
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: '放弃修改', exact: true }).click()
    await expect(page).toHaveURL(/\/users/)

    /*
     * 设置页被 KeepAlive 缓存，用 page.goto 重新打开会重建组件、绕开缓存，测不到
     * 「放弃是否真的回滚」。这里走菜单返回同一实例，断言被放弃的值没有留下。
     */
    await page.locator('.sidebar-container a[href="/settings"]').first().click()
    await expect(page).toHaveURL(/\/settings/)
    await expect(page.locator('.settings-content[aria-busy="false"]')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('textbox', { name: '系统名称', exact: true })).toHaveValue(originalName)
    await expect(page.locator('.page-header .header-actions').getByRole('button', { name: '保存设置' })).toBeDisabled()
  })

  test('保存设置先列出修改项，取消则不写入', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await openSettings(page, 'system')

    const systemName = page.getByRole('textbox', { name: '系统名称', exact: true })
    await expect(systemName).toBeVisible({ timeout: 20_000 })

    const originalName = await systemName.inputValue()
    const editedName = `${originalName}-保存确认`
    const saveButton = page.locator('.page-header .header-actions').getByRole('button', { name: '保存设置' })
    const dialog = page.getByRole('dialog', { name: '确认保存设置' })

    await systemName.fill(editedName)
    await saveButton.click()

    // 弹窗列出本次修改：字段名、旧值和新值同时可见
    await expect(dialog).toBeVisible()
    await expect(dialog).toContainText('共 1 项修改')
    await expect(dialog.locator('.action-dialog-detail-label')).toHaveText(['系统名称'])
    await expect(dialog.locator('.action-dialog-detail-before')).toHaveText([originalName])
    await expect(dialog.locator('.action-dialog-detail-after')).toHaveText([editedName])

    // 返回修改：不写入，修改仍留在页面上
    await dialog.getByRole('button', { name: '返回修改', exact: true }).click()
    await expect(dialog).toHaveCount(0)
    await expect(systemName).toHaveValue(editedName)
    await expect(saveButton).toBeEnabled()

    // 确认保存：写入后回到已保存状态
    await saveButton.click()
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: '确认保存', exact: true }).click()
    await expect(dialog).toHaveCount(0)
    await expect(saveButton).toBeDisabled()

    // 还原初始值，避免影响同一 Mock 服务上的后续用例
    await systemName.fill(originalName)
    await saveButton.click()
    await dialog.getByRole('button', { name: '确认保存', exact: true }).click()
    await expect(saveButton).toBeDisabled()
  })

  test('选择保存并离开会先写入服务器再跳转', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await openSettings(page, 'system')

    const systemName = page.getByRole('textbox', { name: '系统名称', exact: true })
    await expect(systemName).toBeVisible({ timeout: 20_000 })
    const originalName = await systemName.inputValue()
    const editedName = `${originalName}-离开保存`
    const saveButton = page.locator('.page-header .header-actions').getByRole('button', { name: '保存设置' })

    await systemName.fill(editedName)
    await page.locator('.menu-item-container').filter({ hasText: '用户管理' }).first().click()
    const dialog = page.getByRole('dialog', { name: '有未保存的设置' })
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: '保存并离开', exact: true }).click()
    await expect(page).toHaveURL(/\/users/)

    // 通过菜单回到同一缓存实例，值应已落库且不再处于待保存状态。
    await page.locator('.sidebar-container a[href="/settings"]').first().click()
    await expect(page.locator('.settings-content[aria-busy="false"]')).toBeVisible({ timeout: 20_000 })
    await expect(systemName).toHaveValue(editedName)
    await expect(saveButton).toBeDisabled()

    // 还原初始值，避免影响同一 Mock 服务上的后续用例
    await systemName.fill(originalName)
    await saveButton.click()
    await page.getByRole('dialog', { name: '确认保存设置' })
      .getByRole('button', { name: '确认保存', exact: true })
      .click()
    await expect(saveButton).toBeDisabled()
  })
})
