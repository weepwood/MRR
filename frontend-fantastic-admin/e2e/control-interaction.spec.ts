import { expect, test } from '@playwright/test'

test.use({ storageState: { cookies: [], origins: [] } })

test.beforeEach(async ({ page }) => {
  await page.route('**/__control-review', route => route.fulfill({
    contentType: 'text/html',
    body: '<!doctype html><html lang="zh-CN"><meta name="viewport" content="width=device-width,initial-scale=1"><title>合成控件验收</title><div id="app"></div><script type="module" src="/e2e/fixtures/control-review.ts"></script></html>',
  }))
  await page.goto('/__control-review')
  await expect(page.getByRole('button', { name: '确认查询' })).toBeVisible()
})

test('共享按钮焦点、加载语义和重复操作', async ({ page }) => {
  const button = page.getByRole('button', { name: '确认查询' })
  await button.focus()
  await expect(button).toHaveCSS('outline-style', 'solid')
  await button.press('Enter')
  await button.press('Space')
  await expect(page.getByRole('status')).toHaveText('已操作 2 次')
  await expect(page.getByRole('button', { name: '不可操作' })).toBeDisabled()
  const loading = page.getByRole('button', { name: '处理中' })
  await expect(loading).toBeDisabled()
  await expect(loading).toHaveAttribute('aria-busy', 'true')
})

for (const preference of ['normal', 'reduced', 'off'] as const) {
  test(`按压与展开状态：${preference}`, async ({ page }) => {
    await page.emulateMedia({ reducedMotion: preference === 'reduced' ? 'reduce' : 'no-preference' })
    await page.evaluate((off) => { document.documentElement.dataset.motion = off ? 'off' : 'on' }, preference === 'off')
    for (const name of ['确认查询', 'Element 操作']) {
      const button = page.getByRole('button', { name })
      await button.hover()
      await page.mouse.down()
      await expect(button).toHaveCSS('transform', name === 'Element 操作' && preference === 'normal' ? 'matrix(1, 0, 0, 1, 0, 1)' : 'none')
      await page.mouse.up()
      await expect(button).toHaveCSS('transform', 'none')
    }
    await page.locator('.el-select__wrapper').click()
    await page.getByRole('option', { name: '待处理' }).click()
    await expect(page.locator('.el-select__selected-item').filter({ hasText: '待处理' })).toBeVisible()
    const structural = page.getByRole('button', { name: '重置条件' })
    await structural.evaluate(el => el.style.transform = 'translateY(-50%) rotate(180deg)')
    const originalTransform = await structural.evaluate(el => getComputedStyle(el).transform)
    await structural.hover()
    await page.mouse.down()
    await expect(structural).toHaveCSS('transform', originalTransform)
    await page.mouse.up()
    for (let count = 1; count <= 2; count++) {
      await page.getByRole('button', { name: '打开抽屉' }).click()
      await expect(page.getByRole('dialog')).toBeVisible()
      await page.keyboard.press('Escape')
      await expect(page.getByRole('dialog')).toBeHidden()
      await expect(page.getByTestId('drawer-closed')).toHaveText(`关闭完成 ${count} 次`)
    }
  })
}

for (const dark of [false, true]) {
  test(`视觉验收：${dark ? '深色' : '浅色'}与窄屏`, async ({ page }) => {
    await page.evaluate(dark => document.documentElement.classList.toggle('dark', dark), dark)
    await expect(page.getByRole('button', { name: '确认查询' })).toHaveCSS('color', 'rgb(255, 255, 255)')
    await page.setViewportSize({ width: 1100, height: 700 })
    await page.screenshot({ path: test.info().outputPath(`${dark ? 'dark' : 'light'}.png`) })
    await page.setViewportSize({ width: 390, height: 844 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await expect(page.getByRole('button', { name: '确认查询' })).toBeVisible()
    await page.screenshot({ path: test.info().outputPath(`${dark ? 'dark' : 'light'}-mobile.png`) })
  })
}
