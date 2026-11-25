import { expect, test } from '@playwright/test'

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
