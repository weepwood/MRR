import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

/*
 * 两套按钮的同视觉契约（浏览器侧）。
 *
 * 项目并存 Element Plus 的 `el-button` 与界面封装的 `FaButton`（`.fa-button-control`）。
 * 静态契约测试只能守住「都引用了同一组令牌」，真正会不会读成同一种控件，
 * 必须比对渲染后的计算样式——尤其在主题层把圆角/密度整体换掉之后。
 *
 * 展示页把两族按钮按同一角色逐一对位摆了四组：
 * 静置、尺寸、禁用、加载。这里逐对读取 height / font-size / padding / radius /
 * 边框与底色，任何一项不同都说明某一侧又写回了自己的数值。
 */

// 单个用例要在两套调色板之间来回切换（设置页 + 展示页各两次整页导航），
// 并行 6 worker 命中 Vite dev server 时首次编译会吃掉默认 30s 预算的绝大部分。
// 这里比对的是渲染结果而非耗时，因此放宽到 90s；预热清单已覆盖这两条路由，
// 单独运行时通常在 10s 内完成。
test.describe.configure({ timeout: 90_000 })

interface ButtonStyle {
  height: string
  font: string
  weight: string
  lineHeight: string
  letterSpacing: string
  pad: string
  radius: string
  borderWidth: string
  borderColor: string
  background: string
  color: string
}

/** 静置态逐对对位；右列是展示页里的 Element Plus 版本。 */
const VARIANTS = [
  ['fa-primary', 'el-primary'],
  ['fa-outline', 'el-outline'],
  ['fa-ghost', 'el-ghost'],
  ['fa-destructive', 'el-destructive'],
  ['fa-link', 'el-link'],
  ['fa-sm', 'el-sm'],
  ['fa-lg', 'el-lg'],
] as const

function readStyle(page: Page, testId: string) {
  return page.getByTestId(testId).evaluate((element): ButtonStyle => {
    const style = getComputedStyle(element)
    return {
      height: style.height,
      font: style.fontSize,
      weight: style.fontWeight,
      lineHeight: style.lineHeight,
      letterSpacing: style.letterSpacing,
      pad: style.padding,
      radius: style.borderRadius,
      borderWidth: style.borderTopWidth,
      borderColor: style.borderTopColor,
      background: style.backgroundColor,
      color: style.color,
    }
  })
}

async function selectPalette(page: Page, name: RegExp, expected: 'mrr' | 'gr8r') {
  await page.goto('/settings?section=appearance', { waitUntil: 'domcontentloaded' })
  const option = page.getByRole('button', { name })
  await expect(option).toBeVisible()
  await option.click()
  await expect(option).toHaveAttribute('aria-pressed', 'true')

  // 配色写入 store 后由持久化 watcher 落到 localStorage，紧接着整页跳转可能赶在写入之前，
  // 新页面启动时又会按旧值还原。这里等它真的落盘，再去别的路由验证。
  // 默认值（MRR 经典）可能不入库，缺省即视为 mrr。
  await expect
    .poll(() => page.evaluate(() => {
      const stored = JSON.parse(localStorage.getItem('MRR-ADMIN:app-settings') || '{}').app?.themePalette
      return stored ?? 'mrr'
    }))
    .toBe(expected)
}

async function openGallery(page: Page) {
  await page.goto('/ui-components', { waitUntil: 'domcontentloaded' })
  await expect(page.getByTestId('fa-primary')).toBeVisible()
  // 圆角/内边距有 120ms 过渡，等一轮动画再读，避免读到中间值。
  await page.waitForTimeout(400)
}

async function expectParity(page: Page) {
  // 读计算样式前先让 120ms 的颜色/圆角过渡走完：过渡中读到的是插值，两侧会「看起来不同」。
  await page.waitForTimeout(300)
  for (const [faId, elId] of VARIANTS) {
    const [packaged, native] = await Promise.all([readStyle(page, faId), readStyle(page, elId)])
    expect(packaged, `${faId} 与 ${elId} 的尺寸应完全一致`).toEqual(native)
  }
}

test('界面封装按钮与 Element Plus 按钮在两套调色板下外观一致', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })

  await selectPalette(page, /MRR 经典/, 'mrr')
  await openGallery(page)
  await expect(page.locator('html')).not.toHaveAttribute('data-mrr-palette', 'gr8r')
  await expectParity(page)

  await selectPalette(page, /暖色扁平风格/, 'gr8r')
  await openGallery(page)
  await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r')
  await expectParity(page)

  // 还原默认调色板，避免影响同批次的其它用例。
  await selectPalette(page, /MRR 经典/, 'mrr')
})

test('禁用与加载状态同样由同一份状态层驱动', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await selectPalette(page, /暖色扁平风格/, 'gr8r')
  await openGallery(page)

  const stateSwitch = page.getByRole('radiogroup', { name: '按钮示例状态' })

  await stateSwitch.getByText('禁用', { exact: true }).click()
  await expect.poll(async () => (await readStyle(page, 'fa-outline')).background).not.toBe('rgb(255, 255, 255)')
  await expectParity(page)
  // 禁用的默认按钮必须退出可用的白底，否则看起来仍可点击。
  const disabled = await readStyle(page, 'el-outline')
  expect(disabled.color).toBe('rgb(102, 99, 92)')
  expect(disabled.background).toBe('rgb(247, 246, 243)')

  await stateSwitch.getByText('加载', { exact: true }).click()
  await expectParity(page)
  // 加载态保留主按钮配色（与 el-button 的处理一致），只降透明度。
  const loading = await readStyle(page, 'fa-primary')
  expect(loading.background).toBe('rgb(83, 98, 206)')

  await stateSwitch.getByText('默认', { exact: true }).click()
  await selectPalette(page, /MRR 经典/, 'mrr')
})
