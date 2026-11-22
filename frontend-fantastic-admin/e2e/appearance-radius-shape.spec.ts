import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

/** 读计算后的曲线形状；Chromium 会把关键字规范化成 superellipse() 形式。 */
async function cornerShapeOf(page: Page, selector: string): Promise<string> {
  const raw = await page.evaluate((target) => {
    const element = document.querySelector(target)
    return element ? getComputedStyle(element).getPropertyValue('corner-shape').trim() : 'missing'
  }, selector)
  if (raw === '' || raw === 'round' || raw === 'superellipse(1)') {
    return 'round'
  }
  if (raw === 'squircle' || raw === 'superellipse(2)') {
    return 'squircle'
  }
  return raw
}

test.describe('界面外观 · 圆角曲线', () => {
  test('切换到 G2 连续曲线后写入根节点并可切回标准圆弧', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/settings?section=appearance', { waitUntil: 'domcontentloaded' })

    const options = page.locator('.corner-options')
    await expect(options).toBeVisible({ timeout: 20_000 })
    const roundOption = options.getByRole('button', { name: '标准圆弧' })
    const squircleOption = options.getByRole('button', { name: 'G2 连续曲线' })
    await expect(roundOption).toHaveAttribute('aria-pressed', 'true')

    await squircleOption.click()
    await expect(squircleOption).toHaveAttribute('aria-pressed', 'true')
    await expect(page.locator('html')).toHaveAttribute('data-radius-shape', 'squircle')

    // 支持 corner-shape 的浏览器上，圆角元素应真的按超椭圆绘制；不支持的浏览器只校验降级不报错。
    // 计算值会被规范化：Chromium 把 `squircle` 回读成 `superellipse(2)`（两者等价）。
    const supported = await page.evaluate(() => CSS.supports('corner-shape', 'squircle'))
    if (supported) {
      expect(await cornerShapeOf(page, '.config-section')).toBe('squircle')
    }

    await roundOption.click()
    await expect(page.locator('html')).toHaveAttribute('data-radius-shape', 'round')
    await expect(roundOption).toHaveAttribute('aria-pressed', 'true')
  })

  test('两种曲线预览互不影响，切回圆弧后全部回归圆弧', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/settings?section=appearance', { waitUntil: 'domcontentloaded' })

    const options = page.locator('.corner-options')
    await expect(options).toBeVisible({ timeout: 20_000 })
    const supported = await page.evaluate(() => CSS.supports('corner-shape', 'squircle'))
    if (!supported) {
      test.skip(true, '当前浏览器不支持 corner-shape，无法校验曲线差异')
    }

    // 标准圆弧模式下，G2 预览块也要显示超椭圆，才能与左侧并排对比。
    expect(await cornerShapeOf(page, '.corner-preview--squircle')).toBe('squircle')
    expect(await cornerShapeOf(page, '.corner-preview--round')).toBe('round')

    await options.getByRole('button', { name: 'G2 连续曲线' }).click()
    await expect(page.locator('html')).toHaveAttribute('data-radius-shape', 'squircle')

    // 全局切到超椭圆后，预览里的“标准圆弧”仍要按圆弧绘制。
    expect(await cornerShapeOf(page, '.corner-preview--squircle')).toBe('squircle')
    expect(await cornerShapeOf(page, '.corner-preview--round')).toBe('round')

    await options.getByRole('button', { name: '标准圆弧' }).click()
    await expect(page.locator('html')).toHaveAttribute('data-radius-shape', 'round')
    expect(await cornerShapeOf(page, '.config-section')).toBe('round')
  })

  /*
   * 开关、滑块、正圆与胶囊的几何本身不是圆角矩形：套上超椭圆会把轨道端头削平、
   * 把手柄压成圆角方块（见 components/corner-shape.css 的分组说明）。
   *
   * 项目里有两套控件：Element Plus 的 .el-switch / .el-slider，以及界面外观页
   * 自己在用的 reka-ui 封装 .fa-switch / .fa-slider。两套都必须退出 G2，否则
   * 用户就在“圆角曲线”旁边看到开关变形。这里同时锁住计算值与像素：
   * 两种曲线下同一个开关/滑块截图的字节必须完全一致。
   */
  test('开关与滑块在 G2 模式下保持标准圆弧（两套控件体系）', async ({ page }) => {
    // 这条用例要跑两轮「切页 + 等设置加载」，默认 30s 容易被冷启动的 dev server 吃掉。
    test.setTimeout(90_000)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/settings?section=appearance', { waitUntil: 'domcontentloaded' })

    const options = page.locator('.corner-options')
    await expect(options).toBeVisible({ timeout: 20_000 })

    const supported = await page.evaluate(() => CSS.supports('corner-shape', 'squircle'))
    if (!supported) {
      test.skip(true, '当前浏览器不支持 corner-shape，降级场景无需校验')
    }

    await options.getByRole('button', { name: 'G2 连续曲线' }).click()
    await expect(page.locator('html')).toHaveAttribute('data-radius-shape', 'squircle')

    // 卡片等圆角矩形照常走超椭圆。
    expect(await cornerShapeOf(page, '.config-section')).toBe('squircle')

    // 主题色块是 50% 正圆。
    expect(await cornerShapeOf(page, '.theme-swatch')).toBe('round')

    // reka-ui 版开关与滑块：根节点、轨道、手柄逐层都要是圆弧。
    for (const selector of ['.fa-switch', '.fa-switch__thumb', '.fa-slider', '.fa-slider__track', '.fa-slider__thumb']) {
      expect(await cornerShapeOf(page, selector), `${selector} 应为标准圆弧`).toBe('round')
    }

    const switchEl = page.locator('.fa-switch').first()
    const sliderEl = page.locator('.fa-slider').first()
    // Ancestor corners contribute background pixels to these crops. Keep their
    // shape fixed while the controls and their descendants retain their styles.
    const captureOptions = { style: '* :has(.fa-switch, .fa-slider) { corner-shape: round !important; }' }
    const sqSwitch = await switchEl.screenshot(captureOptions)
    const sqSlider = await sliderEl.screenshot(captureOptions)

    // 切回标准圆弧，同一元素的截图必须与 G2 模式下完全一致（无变形、无 1px 偏移）。
    await options.getByRole('button', { name: '标准圆弧' }).click()
    await expect(page.locator('html')).toHaveAttribute('data-radius-shape', 'round')
    expect((await switchEl.screenshot(captureOptions)).equals(sqSwitch), 'reka-ui 开关在两种曲线下渲染应完全一致').toBe(true)
    expect((await sliderEl.screenshot(captureOptions)).equals(sqSlider), 'reka-ui 滑块在两种曲线下渲染应完全一致').toBe(true)

    // Element Plus 版开关与滑块：直接跳到该页会重新拉取设置（未保存的修改被丢弃），
    // 所以这里直接写根节点属性，只校验 CSS 的生效范围本身。
    await page.goto('/settings?section=security', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.settings-content[aria-busy="false"]')).toBeVisible({ timeout: 20_000 })
    await page.evaluate(() => document.documentElement.setAttribute('data-radius-shape', 'squircle'))
    await expect(page.locator('html')).toHaveAttribute('data-radius-shape', 'squircle')
    for (const selector of ['.el-switch', '.el-switch__core', '.el-switch__action', '.el-slider', '.el-slider__runway', '.el-slider__button']) {
      expect(await cornerShapeOf(page, selector), `${selector} 应为标准圆弧`).toBe('round')
    }
    // 同一页面的卡片仍是超椭圆，说明退出规则只作用于控件本身。
    expect(await cornerShapeOf(page, '.switch-list')).toBe('squircle')
  })
})
