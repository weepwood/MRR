import { expect, test } from '@playwright/test'

test.use({ storageState: 'e2e/.auth/anonymous.json', hasTouch: true })

test('多次中途松手后，箭头和边框恢复原位且无残影', async ({ page }, testInfo) => {
  await page.goto('/login')
  const slider = page.getByRole('slider', { name: '滑动验证' })
  await expect(slider).toBeVisible()
  await page.locator('.login-shell').evaluate(async element => Promise.all(element.getAnimations().map(animation => animation.finished)))
  const track = page.locator('.slide-check__track')
  const box = (await track.boundingBox())!
  const thumb = (await slider.boundingBox())!
  const originX = thumb.x + thumb.width / 2
  const y = thumb.y + thumb.height / 2
  await slider.click()
  await page.mouse.move(box.x, box.y - 12)
  const initial = await track.screenshot({ path: testInfo.outputPath('initial.png') })
  for (const fraction of [0.25, 0.55, 0.85]) {
    await page.mouse.move(originX, y)
    await page.mouse.down()
    await page.mouse.move(originX + (box.width - thumb.width - 8) * fraction, y, { steps: 6 })
    await page.mouse.up()
    await page.mouse.move(box.x, box.y - 12)
    await expect(slider).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 0, 0)')
    await expect(page.locator('.slide-check__fill')).toHaveCSS('transform', 'matrix(0, 0, 0, 1, 0, 0)')
    const returned = await track.screenshot({ path: testInfo.outputPath(`returned-${fraction}.png`) })
    // 比较解码像素；合成层边缘抗锯齿允许最多 2/255 色阶差异。
    const identical = await page.evaluate(async (sources) => {
      const frames = await Promise.all(sources.map(async (source) => {
        const image = new Image()
        image.src = `data:image/png;base64,${source}`
        await image.decode()
        const canvas = document.createElement('canvas')
        canvas.width = image.width
        canvas.height = image.height
        const context = canvas.getContext('2d')!
        context.drawImage(image, 0, 0)
        return context.getImageData(0, 0, canvas.width, canvas.height)
      }))
      return frames[0].width === frames[1].width && frames[0].height === frames[1].height
        && frames[0].data.every((value, index) => Math.abs(value - frames[1].data[index]) <= 2)
    }, [initial.toString('base64'), returned.toString('base64')])
    expect(identical, '回弹后轨道、箭头及边框应恢复原始画面').toBe(true)
  }
})

test('鼠标拖动需到达右端，未完成时不提交，登录失败后重置', async ({ page }) => {
  let loginRequests = 0
  let releaseResponse!: () => void
  const pendingResponse = new Promise<void>((resolve) => { releaseResponse = resolve })
  await page.route('**/api/v1/auth/login', async (route) => {
    loginRequests++
    await pendingResponse
    await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 401, message: '测试登录失败' }) })
  })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/login')
  await page.locator('#login-account').fill('slider.demo')
  await page.locator('#login-password').fill('demo-password')
  const slider = page.getByRole('slider', { name: '滑动验证' })
  const card = page.locator('.login-fields-card')
  const height = (await card.boundingBox())!.height
  await page.getByRole('button', { name: '登录系统' }).click()
  await expect(page.getByRole('status').filter({ hasText: '请先向右拖动滑块至尽头' })).toBeVisible()
  expect(loginRequests).toBe(0)
  // 点击轨道与只拖一半都不能完成。
  const track = (await page.locator('.slide-check__track').boundingBox())!
  await page.mouse.click(track.x + track.width - 10, track.y + track.height / 2)
  await expect(slider).toHaveAttribute('aria-valuenow', '0')
  const thumb = (await slider.boundingBox())!
  await page.mouse.move(thumb.x + thumb.width / 2, thumb.y + thumb.height / 2)
  await page.mouse.down()
  await page.mouse.move(track.x + track.width / 2, track.y + track.height / 2, { steps: 5 })
  await expect(slider).not.toHaveAttribute('aria-valuenow', '0')
  await page.mouse.up()
  await expect(slider).toHaveAttribute('aria-valuenow', '0')
  await expect(slider).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 0, 0)')
  await page.mouse.move(thumb.x + thumb.width / 2, thumb.y + thumb.height / 2)
  await page.mouse.down()
  await page.mouse.move(track.x + track.width - 4, track.y + track.height / 2, { steps: 10 })
  await page.mouse.up()
  await expect(slider).toHaveAttribute('aria-valuenow', '100')
  await expect(slider).toBeDisabled()
  expect(Math.abs((await card.boundingBox())!.height - height)).toBeLessThanOrEqual(1)
  await page.getByRole('button', { name: '登录系统' }).click()
  await expect.poll(() => loginRequests).toBe(1)
  await expect(page.getByRole('button', { name: '正在验证...' })).toBeVisible()
  await expect(slider).toBeDisabled()
  releaseResponse()
  await expect(page.getByText('测试登录失败', { exact: true })).toBeVisible()
  await expect(slider).toBeEnabled()
  await expect(slider).toHaveAttribute('aria-valuenow', '0')
  await page.getByRole('button', { name: '登录系统' }).click()
  await expect(page.getByRole('status').filter({ hasText: '请先向右拖动滑块至尽头' })).toBeVisible()
  expect(loginRequests).toBe(1)
})

test('键盘支持向右完成，重新进入登录时重置', async ({ page }) => {
  await page.goto('/login')
  const slider = page.getByRole('slider', { name: '滑动验证' })
  await slider.focus()
  await page.keyboard.press('ArrowRight')
  await expect(slider).toHaveAttribute('aria-valuenow', '10')
  await page.keyboard.press('ArrowLeft')
  await expect(slider).toHaveAttribute('aria-valuenow', '0')
  for (let step = 0; step < 10; step++) { await page.keyboard.press('ArrowRight') }
  await expect(slider).toHaveAttribute('aria-valuenow', '100')
  await page.getByRole('tab', { name: '注册申请' }).click()
  await expect(page.locator('.login-form')).toHaveCount(0)
  await page.getByRole('tab', { name: '账号登录' }).click()
  await expect(slider).toHaveAttribute('aria-valuenow', '0')
  await expect(slider).toBeEnabled()
})

test('手机触摸中断后回弹，并可重新向右拖动完成', async ({ page, context }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 1000 })
  await page.goto('/login')
  const slider = page.getByRole('slider', { name: '滑动验证' })
  await slider.scrollIntoViewIfNeeded()
  const thumb = (await slider.boundingBox())!
  const track = (await page.locator('.slide-check__track').boundingBox())!
  const client = await context.newCDPSession(page)
  const start = { x: thumb.x + thumb.width / 2, y: thumb.y + thumb.height / 2 }
  await client.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [start] })
  await client.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ ...start, x: track.x + track.width / 2 }] })
  await client.send('Input.dispatchTouchEvent', { type: 'touchCancel', touchPoints: [] })
  await expect(slider).toHaveAttribute('aria-valuenow', '0')
  await expect(slider).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 0, 0)')
  await client.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [start] })
  await client.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ ...start, x: track.x + track.width - 4 }] })
  await client.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
  await expect(slider).toHaveAttribute('aria-valuenow', '100')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('slide-complete-mobile.png'), fullPage: true })
})
