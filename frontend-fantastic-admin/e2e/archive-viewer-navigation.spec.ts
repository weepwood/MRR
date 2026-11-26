import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => { await isolateLocalVisualSettings(page) })

test('原图局部缩放锚点、鼠标拖动和键盘复位保持显示边界', async ({ page }) => {
  await page.goto('/archive-workbench')
  const search = page.getByRole('textbox', { name: '病案查询值' })
  await search.fill('26070000')
  await search.press('Enter')
  await page.getByRole('group', { name: '有效影像列表' }).getByRole('button').first().click()
  await page.getByRole('button', { name: '专注原图' }).click()
  const region = page.getByRole('region', { name: '原图滚动查看区' })
  const controls = page.getByRole('group', { name: '原图显示控制' })
  const image = page.getByAltText('待核对影像')
  await expect.poll(() => image.evaluate((el: HTMLImageElement) => el.complete && el.naturalWidth > 0)).toBe(true)
  for (let i = 0; i < 5; i++) {
    await controls.getByRole('button', { name: '放大', exact: true }).click()
  }
  await expect(controls.getByText('225%', { exact: true })).toBeVisible()
  await region.scrollIntoViewIfNeeded()
  await expect.poll(() => region.evaluate(el => el.scrollHeight - el.clientHeight)).toBeGreaterThan(80)
  const point = await region.evaluate((el) => {
    const r = el.getBoundingClientRect()
    return { x: r.left + el.clientWidth / 2, y: r.top + el.clientHeight / 2 + 45 }
  })
  const relative = () => region.locator('.scan-image-box').evaluate((el, p) => {
    const r = el.getBoundingClientRect()
    return { x: (p.x - r.left) / r.width, y: (p.y - r.top) / r.height }
  }, point)
  const before = await relative()
  expect(before.y).toBeGreaterThan(0.1)
  expect(before.y).toBeLessThan(0.9)
  await page.mouse.move(point.x, point.y)
  await page.keyboard.down('Control')
  try {
    await page.mouse.wheel(0, -100)
  }
  finally {
    await page.keyboard.up('Control')
  }
  await expect(controls.getByText('250%', { exact: true })).toBeVisible()
  await expect.poll(async () => Math.abs((await relative()).y - before.y)).toBeLessThan(0.015)
  const start = await region.evaluate(el => el.scrollTop)
  await page.mouse.move(point.x, point.y)
  await page.mouse.down()
  try {
    await page.mouse.move(point.x, point.y - 65, { steps: 5 })
    await expect(region).toHaveClass(/is-dragging/)
  }
  finally {
    await page.mouse.up()
  }
  await expect(region).not.toHaveClass(/is-dragging/)
  await expect.poll(() => region.evaluate(el => el.scrollTop)).toBeGreaterThan(start + 40)
  await expect(region).toBeFocused()
  await page.keyboard.press('0')
  await expect(controls.getByText('100%', { exact: true })).toBeVisible()
  await expect.poll(() => region.evaluate(el => el.scrollTop)).toBeLessThan(2)
  await page.keyboard.press('+')
  await expect(controls.getByText('125%', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '下一张', exact: true }).click()
  await expect(controls.getByText('100%', { exact: true })).toBeVisible()
  await expect(region).not.toHaveClass(/is-dragging/)
})

test('原图适宽、实际像素与反向旋转仅改变显示，换图恢复默认', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: 'dark', enableMotion: false }, menu: { mode: 'single' } })))
  await page.goto('/archive-workbench')
  const search = page.getByRole('textbox', { name: '病案查询值' })
  await search.fill('26070000')
  await search.press('Enter')
  await page.getByRole('group', { name: '有效影像列表' }).getByRole('button').first().click()
  await page.getByRole('button', { name: '专注原图' }).click()
  const region = page.getByRole('region', { name: '原图滚动查看区' })
  const controls = page.getByRole('group', { name: '原图显示控制' })
  const image = page.getByAltText('待核对影像')
  await expect.poll(() => image.evaluate((el: HTMLImageElement) => el.complete && el.naturalWidth > 0)).toBe(true)
  await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(true)
  const requests: string[] = []
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.includes('/img/content/')) { requests.push(request.url()) }
  })
  await controls.getByRole('button', { name: '实际大小' }).click()
  await expect(controls.getByRole('button', { name: '实际大小' })).toHaveAttribute('aria-pressed', 'true')
  await expect.poll(() => image.evaluate((el: HTMLImageElement) => Math.abs(el.offsetWidth - el.naturalWidth))).toBeLessThan(2)
  await controls.getByRole('button', { name: '逆时针旋转' }).click()
  await expect(controls.getByText('270°', { exact: true })).toBeVisible()
  await region.focus()
  await region.press('w')
  await expect(controls.getByRole('button', { name: '适应宽度' })).toHaveAttribute('aria-pressed', 'true')
  await region.press('1')
  await expect(controls.getByRole('button', { name: '实际大小' })).toHaveAttribute('aria-pressed', 'true')
  await region.press('0')
  await expect(controls.getByRole('button', { name: '适应窗口' })).toHaveAttribute('aria-pressed', 'true')
  await expect(controls.getByText('0°', { exact: true })).toBeVisible()
  expect(requests).toHaveLength(0)
  await page.setViewportSize({ width: 390, height: 844 })
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  await page.screenshot({ path: test.info().outputPath('v12-viewer-dark-mobile.png'), fullPage: true })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.screenshot({ path: test.info().outputPath('v12-viewer-dark-desktop.png'), fullPage: true })
  await controls.getByRole('button', { name: '实际大小' }).click()
  await page.getByRole('button', { name: '下一张', exact: true }).click()
  await expect(controls.getByRole('button', { name: '适应窗口' })).toHaveAttribute('aria-pressed', 'true')
  await expect(controls.getByText('100%', { exact: true })).toBeVisible()
})
