import { expect, test } from '@playwright/test'

async function readVariable(page: import('@playwright/test').Page, name: string) {
  return page.evaluate(key => getComputedStyle(document.documentElement).getPropertyValue(key).trim().toLowerCase(), name)
}

test('暖色扁平风格可切换明暗、持久保存，并安全返回 MRR 配色', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/settings?section=appearance', { waitUntil: 'domcontentloaded' })
  const gr8r = page.getByRole('button', { name: /暖色扁平风格/ })
  const classic = page.getByRole('button', { name: /MRR 经典/ })
  await expect(gr8r).toBeVisible()

  await gr8r.click()
  await expect(gr8r).toHaveAttribute('aria-pressed', 'true')
  await expect.poll(() => readVariable(page, '--g-main-area-bg')).toBe('#faf9f7')
  await expect.poll(() => readVariable(page, '--mrr-navigation-bg-solid')).toBe('#f3f2ef')
  await expect.poll(() => readVariable(page, '--color-primary')).toBe('#5362ce')

  const themeTabs = page.locator('.theme-tabs')
  await themeTabs.getByRole('tab', { name: '暗黑' }).click()
  await expect.poll(() => readVariable(page, '--g-main-area-bg')).toBe('#1b1b1a')
  await expect.poll(() => readVariable(page, '--color-primary')).toBe('#9da8f7')

  await themeTabs.getByRole('tab', { name: '明亮' }).click()
  await expect.poll(() => readVariable(page, '--g-main-area-bg')).toBe('#faf9f7')
  await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem('MRR-ADMIN:app-settings') || '{}').app?.themePalette)).toBe('gr8r')

  await page.reload({ waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('button', { name: /暖色扁平风格/ })).toHaveAttribute('aria-pressed', 'true')
  await expect.poll(() => readVariable(page, '--color-primary')).toBe('#5362ce')

  await page.getByRole('button', { name: /MRR 经典/ }).click()
  await expect.poll(() => readVariable(page, '--color-primary')).toBe('#2563eb')
  await expect.poll(() => page.evaluate(() => document.documentElement.style.getPropertyValue('--g-main-area-bg'))).toBe('')
  await expect(page.locator('.theme-colors')).toBeVisible()
  await expect(classic).toBeVisible()
})

test('暖色扁平风格同时收敛控件密度与圆角，切回后完全还原', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/settings?section=appearance', { waitUntil: 'domcontentloaded' })
  const gr8r = page.getByRole('button', { name: /暖色扁平风格/ })
  await expect(gr8r).toBeVisible()

  /** 读实际渲染尺寸：令牌写对但选择器没命中时，这里会立刻暴露。 */
  const geometry = () => page.evaluate(() => {
    const button = document.querySelector('.el-button')
    const plain = document.querySelector('.el-button:not(.el-button--primary):not(.el-button--success):not(.el-button--warning):not(.el-button--danger):not(.el-button--info):not(.is-text):not(.is-link)')
    const input = document.querySelector('.el-input__wrapper, .fa-input-control')
    const navItem = document.querySelector('.menu-item-container')
    const style = getComputedStyle(document.documentElement)
    return {
      inputHeight: style.getPropertyValue('--mrr-input-height').trim(),
      rowHeight: style.getPropertyValue('--mrr-table-row-height').trim(),
      radiusCard: style.getPropertyValue('--mrr-radius-card').trim(),
      navActive: style.getPropertyValue('--mrr-navigation-active').trim(),
      buttonHeight: button ? Math.round(button.getBoundingClientRect().height) : null,
      inputBox: input ? Math.round(input.getBoundingClientRect().height) : null,
      navBox: navItem ? Math.round(navItem.getBoundingClientRect().height) : null,
      // 令牌漏定义时 border-color 会退化成 currentColor（墨色），这里直接量算出来的颜色。
      plainBorder: plain ? getComputedStyle(plain).borderTopColor : null,
    }
  })

  const before = await geometry()
  expect(before.inputHeight).toBe('33px')

  await gr8r.click()
  await expect.poll(async () => (await geometry()).inputHeight).toBe('30px')
  const flat = await geometry()
  // 密度：控件 30px、表格行 40px；导航项也收紧一档。
  expect(flat.rowHeight).toBe('40px')
  expect(flat.buttonHeight).toBe(30)
  expect(flat.inputBox === null || flat.inputBox === 30).toBe(true)
  expect(flat.navBox === null || flat.navBox <= 34).toBe(true)
  // 圆角：跟随滑块但封顶，不再使用 clamp 的原始口径。
  expect(flat.radiusCard).toContain('min(calc(')
  // 导航选中态改成表面色药丸（不再用主色浅填充）。
  expect(flat.navActive.toLowerCase()).toBe('#ffffff')
  // 默认按钮用的是 --mrr-border-strong（暖灰 #D9D6CE）；令牌缺失会退回墨色 1px 实线。
  // 边框有 120ms 过渡，切换瞬间读到的是插值色，因此用 poll 等它落到目标值。
  if (flat.plainBorder) {
    await expect.poll(async () => (await geometry()).plainBorder).toBe('rgb(217, 214, 206)')
  }

  await page.getByRole('button', { name: /MRR 经典/ }).click()
  await expect.poll(async () => (await geometry()).inputHeight).toBe('33px')
  const restored = await geometry()
  expect(restored.rowHeight).toBe('46px')
  expect(restored.buttonHeight).toBe(before.buttonHeight)
  expect(restored.radiusCard).toBe(before.radiusCard)
  expect(restored.navActive).toBe(before.navActive)
})
