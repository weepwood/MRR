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
    await expect(page.getByText('登录页文案', { exact: true })).toHaveCount(0)

    await expect(page.locator('.section-header').getByRole('heading', { name: '系统信息' })).toBeVisible()
    await expect(page.getByText('系统标识', { exact: true })).toBeVisible()

    await page.locator('.settings-nav-item').filter({ hasText: '登录与支持' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '登录与支持' })).toBeVisible()
    await expect(page.getByText('系统管理员与技术支持', { exact: true })).toBeVisible()
    await expect(page.getByText('在登录页公开显示', { exact: true })).toBeVisible()

    await page.locator('.settings-nav-item').filter({ hasText: '档案浏览' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '档案浏览' })).toBeVisible()
    await expect(page.getByText('默认图片来源', { exact: true })).toBeVisible()
    await expect(page.getByText('自动适应预览区域', { exact: true })).toBeVisible()

    await page.locator('.settings-nav-item').filter({ hasText: '访问安全' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '访问安全' })).toBeVisible()
    await expect(page.getByText('每日允许 IP 切换次数', { exact: true })).toBeVisible()

    await page.locator('.settings-nav-item').filter({ hasText: '开发者模式' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '开发者模式' })).toBeVisible()
    await expect(page.getByText('兼容旧版无登录接口调用', { exact: true })).toBeVisible()
    await expect(page.getByText('认证兼容', { exact: true })).toBeVisible()
    await expect(page.getByText('跨域调试', { exact: true })).toBeVisible()

    await page.locator('.settings-nav-item').filter({ hasText: '界面外观' }).click()
    await expect(page.locator('.section-header').getByRole('heading', { name: '界面外观' })).toBeVisible()
    await expect(page.getByText('主题与页面样式', { exact: true })).toBeVisible()
    await expect(page.getByText('导航与顶栏', { exact: true })).toBeVisible()
  })

  test('缩略图宽度和水印透明度滑块具有可见滑轨宽度', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/settings?section=archive', { waitUntil: 'domcontentloaded' })

    const thumbnailSlider = page.getByRole('slider', { name: '缩略图宽度' })
    await expect(thumbnailSlider).toBeVisible({ timeout: 20_000 })
    await expect.poll(async () => {
      const box = await thumbnailSlider.boundingBox()
      return box?.width ?? 0
    }).toBeGreaterThan(100)

    await page.locator('.settings-nav-item').filter({ hasText: '访问安全' }).click()
    const watermarkSlider = page.getByRole('slider', { name: '水印透明度' })
    await expect(watermarkSlider).toBeVisible()
    await expect.poll(async () => {
      const box = await watermarkSlider.boundingBox()
      return box?.width ?? 0
    }).toBeGreaterThan(100)
  })

  test('输入框和下拉框在各尺寸下保持一致并响应圆角变量', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/users', { waitUntil: 'domcontentloaded' })

    const filterBar = page.locator('.users-filter-bar')
    const input = filterBar.locator('.el-input__wrapper').first()
    const select = filterBar.locator('.el-select__wrapper').first()
    await expect(input).toBeVisible({ timeout: 20_000 })
    await expect(select).toBeVisible()

    for (const size of [
      { modifier: '', expectedHeight: 36 },
      { modifier: 'small', expectedHeight: 32 },
      { modifier: 'large', expectedHeight: 40 },
    ]) {
      await page.evaluate(({ modifier }) => {
        const inputRoot = document.querySelector('.users-filter-bar .el-input')
        const selectRoot = document.querySelector('.users-filter-bar .el-select')
        if (!inputRoot || !selectRoot) throw new Error('筛选控件未渲染')

        for (const root of [inputRoot, selectRoot]) {
          root.classList.remove('el-input--small', 'el-input--large', 'el-select--small', 'el-select--large')
        }
        if (modifier) {
          inputRoot.classList.add(`el-input--${modifier}`)
          selectRoot.classList.add(`el-select--${modifier}`)
        }
      }, { modifier: size.modifier })

      const styles = await page.evaluate(() => {
        const inputRoot = document.querySelector('.users-filter-bar .el-input')
        const selectRoot = document.querySelector('.users-filter-bar .el-select')
        const inputElement = document.querySelector('.users-filter-bar .el-input__wrapper')
        const selectElement = document.querySelector('.users-filter-bar .el-select__wrapper')
        if (!inputRoot || !selectRoot || !inputElement || !selectElement) throw new Error('筛选控件未渲染')
        return {
          inputHeight: inputElement.getBoundingClientRect().height,
          selectHeight: selectElement.getBoundingClientRect().height,
          inputRadius: getComputedStyle(inputElement).borderRadius,
          selectRadius: getComputedStyle(selectElement).borderRadius,
          inputSizeToken: getComputedStyle(inputRoot).getPropertyValue('--el-component-size').trim(),
          selectSizeToken: getComputedStyle(selectRoot).getPropertyValue('--el-component-size').trim(),
        }
      })

      expect(Math.abs(styles.inputHeight - styles.selectHeight)).toBeLessThanOrEqual(1)
      expect(Math.abs(styles.inputHeight - size.expectedHeight)).toBeLessThanOrEqual(1)
      expect(styles.inputRadius).toBe(styles.selectRadius)
      expect(styles.inputSizeToken).toBe(`${size.expectedHeight}px`)
      expect(styles.selectSizeToken).toBe(`${size.expectedHeight}px`)
    }

    await page.evaluate(() => {
      const inputRoot = document.querySelector('.users-filter-bar .el-input')
      const selectRoot = document.querySelector('.users-filter-bar .el-select')
      inputRoot?.classList.remove('el-input--small', 'el-input--large')
      selectRoot?.classList.remove('el-select--small', 'el-select--large')
    })

    const initialRadius = await input.evaluate(element => getComputedStyle(element).borderRadius)
    await page.evaluate(() => document.documentElement.style.setProperty('--radius', '18px'))

    const adjustedStyles = await page.evaluate(() => {
      const inputElement = document.querySelector('.users-filter-bar .el-input__wrapper')
      const selectElement = document.querySelector('.users-filter-bar .el-select__wrapper')
      if (!inputElement || !selectElement) throw new Error('筛选控件未渲染')
      return {
        inputRadius: getComputedStyle(inputElement).borderRadius,
        selectRadius: getComputedStyle(selectElement).borderRadius,
      }
    })

    expect(adjustedStyles.inputRadius).toBe(adjustedStyles.selectRadius)
    expect(adjustedStyles.inputRadius).not.toBe(initialRadius)
  })

  test('日期范围和数字输入框使用对应尺寸令牌', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/settings?section=archive', { waitUntil: 'domcontentloaded' })

    const inputNumber = page.locator('.settings-content .el-input-number').first()
    await expect(inputNumber).toBeVisible({ timeout: 20_000 })

    for (const size of [
      { modifier: '', expectedHeight: 36 },
      { modifier: 'small', expectedHeight: 32 },
      { modifier: 'large', expectedHeight: 40 },
    ]) {
      await inputNumber.evaluate((element, modifier) => {
        element.classList.remove('el-input-number--small', 'el-input-number--large')
        if (modifier) element.classList.add(`el-input-number--${modifier}`)
      }, size.modifier)
      const measurement = await inputNumber.evaluate(element => ({
        height: element.getBoundingClientRect().height,
        sizeToken: getComputedStyle(element).getPropertyValue('--el-component-size').trim(),
      }))
      expect(Math.abs(measurement.height - size.expectedHeight)).toBeLessThanOrEqual(1)
      expect(measurement.sizeToken).toBe(`${size.expectedHeight}px`)
    }

    await page.goto('/logs', { waitUntil: 'domcontentloaded' })
    const dateRange = page.locator('.el-range-editor').first()
    await expect(dateRange).toBeVisible({ timeout: 20_000 })

    for (const size of [
      { modifier: '', expectedHeight: 36 },
      { modifier: 'small', expectedHeight: 32 },
      { modifier: 'large', expectedHeight: 40 },
    ]) {
      await dateRange.evaluate((element, modifier) => {
        element.classList.remove('el-range-editor--small', 'el-range-editor--large')
        if (modifier) element.classList.add(`el-range-editor--${modifier}`)
      }, size.modifier)
      const measurement = await dateRange.evaluate(element => ({
        height: element.getBoundingClientRect().height,
        sizeToken: getComputedStyle(element).getPropertyValue('--el-component-size').trim(),
      }))
      expect(Math.abs(measurement.height - size.expectedHeight)).toBeLessThanOrEqual(1)
      expect(measurement.sizeToken).toBe(`${size.expectedHeight}px`)
    }
  })

  test('旧登录文案地址跳转到系统设置内部分类', async ({ page }) => {
    await page.goto('/login-settings', { waitUntil: 'domcontentloaded' })
    await expect(page).toHaveURL(/\/settings\?section=login-support/)
    await expect(page.locator('.section-header').getByRole('heading', { name: '登录与支持' })).toBeVisible()
  })

  test('桌面端聚焦设置工作区后保留视口边距且仅滚动右侧内容', async ({ page }) => {
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

    await expect.poll(async () => frame.evaluate(
      element => Math.abs(element.getBoundingClientRect().top),
    )).toBeLessThanOrEqual(2)
    await expect.poll(async () => frame.evaluate(
      element => Math.abs(element.getBoundingClientRect().height - window.innerHeight),
    )).toBeLessThanOrEqual(3)
    await expect.poll(async () => shell.evaluate(
      element => Math.abs(element.getBoundingClientRect().top - 10),
    )).toBeLessThanOrEqual(2)
    await expect.poll(async () => shell.evaluate(
      element => Math.abs(element.getBoundingClientRect().bottom - (window.innerHeight - 10)),
    )).toBeLessThanOrEqual(2)

    await page.getByText('工作区组件', { exact: true }).click()
    await page.getByText('主页、版权与其他', { exact: true }).click()

    await expect.poll(async () => content.evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true)

    const initialSidebarBox = await sidebar.boundingBox()
    expect(initialSidebarBox).not.toBeNull()

    await content.evaluate((element) => {
      element.scrollTo({ top: element.scrollHeight, behavior: 'auto' })
    })
    await expect.poll(() => content.evaluate(element => element.scrollTop)).toBeGreaterThan(100)

    const finalSidebarBox = await sidebar.boundingBox()
    expect(finalSidebarBox).not.toBeNull()
    expect(Math.abs(finalSidebarBox!.top - initialSidebarBox!.top)).toBeLessThanOrEqual(1)
    expect(Math.abs(finalSidebarBox!.bottom - initialSidebarBox!.bottom)).toBeLessThanOrEqual(1)

    await expect(sidebar).toBeInViewport()
    await expect(page.locator('.settings-nav-item').filter({ hasText: '系统信息' })).toBeInViewport()
    await expect(page.locator('.settings-nav-item').filter({ hasText: '界面外观' })).toBeInViewport()
  })
})
