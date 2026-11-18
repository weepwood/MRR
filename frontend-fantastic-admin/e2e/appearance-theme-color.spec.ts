import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

/** 与 src/utils/theme-color.ts 的预设保持一致：改色板时这里要同步。 */
const PRESETS = [
  { label: '蓝色', color: '#2563eb' },
  { label: '青色', color: '#0b7ea6' },
  { label: '绿色', color: '#0a8642' },
  { label: '橙色', color: '#c2410c' },
  { label: '紫色', color: '#7c3aed' },
  { label: '玫红', color: '#db2777' },
]

/** 读取根节点上的 CSS 变量（统一小写），避免浏览器把十六进制原样回读造成断言抖动。 */
async function readVariable(page: Page, name: string) {
  return page.evaluate(variable => getComputedStyle(document.documentElement).getPropertyValue(variable).trim().toLowerCase(), name)
}

/** 在页面里按 WCAG 公式算相对亮度，用来判断某个色阶是"浅色块"还是"深色块"。 */
async function relativeLuminance(page: Page, color: string) {
  return page.evaluate((value) => {
    const hex = value.replace('#', '')
    const channels = [0, 2, 4]
      .map(index => Number.parseInt(hex.slice(index, index + 2), 16) / 255)
      .map(channel => (channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4))
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
  }, color)
}

async function openAppearance(page: Page) {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/settings?section=appearance', { waitUntil: 'domcontentloaded' })
  await expect(page.locator('.theme-colors')).toBeVisible({ timeout: 20_000 })
  // 外观分组本身没有异步读取，等主色已被 store 写入根节点即可，避免点击发生在 watcher 之前。
  await expect.poll(() => readVariable(page, '--color-primary')).toMatch(/^#[\da-f]{6}$/)
}

test.describe('主题主色配色', () => {
  test('每个预设都会写入主色，派生色阶随之更新', async ({ page }) => {
    await openAppearance(page)

    for (const preset of PRESETS) {
      const swatch = page.getByRole('button', { name: preset.label, exact: true })
      await swatch.click()
      await expect(swatch).toHaveAttribute('aria-pressed', 'true')
      await expect.poll(() => readVariable(page, '--color-primary')).toBe(preset.color)
      await expect.poll(() => readVariable(page, '--el-color-primary')).toBe(preset.color)

      // 派生档必须跟着主色走，不能残留上一个颜色的色阶。
      const hover = await readVariable(page, '--color-primary-hover')
      expect(hover, `${preset.label} 悬停档`).not.toBe(preset.color)
      expect(await relativeLuminance(page, hover)).toBeLessThan(await relativeLuminance(page, preset.color))
    }
  })

  test('浅色档随明暗模式改变方向，切换时不会残留浅色块', async ({ page }) => {
    await openAppearance(page)
    const themeTabs = page.locator('.theme-tabs')

    await themeTabs.getByRole('tab', { name: '明亮' }).click()
    await expect(page.locator('html')).not.toHaveClass(/dark/)
    const lightTint = await readVariable(page, '--el-color-primary-light-9')
    expect(await relativeLuminance(page, lightTint), '明亮模式 light-9 应是浅色底').toBeGreaterThan(0.8)

    await themeTabs.getByRole('tab', { name: '暗黑' }).click()
    await expect(page.locator('html')).toHaveClass(/dark/)
    const darkTint = await readVariable(page, '--el-color-primary-light-9')
    expect(await relativeLuminance(page, darkTint), '暗黑模式 light-9 应向底色靠拢').toBeLessThan(0.05)

    // 暗色下 hover 应当比主色更亮，才能在深底上体现"被点亮"。
    const primary = await readVariable(page, '--color-primary')
    const hover = await readVariable(page, '--color-primary-hover')
    expect(await relativeLuminance(page, hover)).toBeGreaterThan(await relativeLuminance(page, primary))

    // 切回明亮要重新推导，而不是停留在暗色档位。
    await themeTabs.getByRole('tab', { name: '明亮' }).click()
    await expect(page.locator('html')).not.toHaveClass(/dark/)
    await expect.poll(() => readVariable(page, '--el-color-primary-light-9')).toBe(lightTint)
  })
})
