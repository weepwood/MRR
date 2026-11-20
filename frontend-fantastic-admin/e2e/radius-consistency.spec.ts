import { expect, test } from '@playwright/test'

async function openFixture(page: import('@playwright/test').Page) {
  await page.route('**/__radius-review', route => route.fulfill({
    contentType: 'text/html',
    body: '<!doctype html><html lang="zh-CN"><meta name="viewport" content="width=device-width,initial-scale=1"><div id="app"></div><script type="module" src="/e2e/fixtures/radius-review.ts"></script></html>',
  }))
  await page.goto('/__radius-review')
  await expect(page.locator('.case-default .el-input__wrapper').first()).toBeVisible()
}

for (const theme of ['light', 'dark']) {
  test(`${theme} 模式下各尺寸控件及组合边缘随全局圆角变化`, async ({ page }) => {
    test.setTimeout(120_000)
    await openFixture(page)
    await page.evaluate((dark) => { document.documentElement.classList.toggle('dark', dark) }, theme === 'dark')
    for (const radius of [0, 12, 20]) {
      await page.evaluate(value => document.documentElement.style.setProperty('--radius', `${value}px`), radius)
      for (const [size, offset] of [['small', 6], ['default', 4], ['large', 2]] as const) {
        const expected = `${Math.max(0, radius - offset)}px`
        const row = page.locator(`.case-${size}`)
        await expect(row.locator('.el-input:not(.el-input-number .el-input) > .el-input__wrapper').first()).toHaveCSS('border-top-left-radius', expected)
        await expect(row.locator('.el-select__wrapper').first()).toHaveCSS('border-top-left-radius', expected)
        await expect(row.locator('.el-range-editor').first()).toHaveCSS('border-top-left-radius', expected)
        const numbers = row.locator('.el-input-number')
        await expect(numbers.nth(0)).toHaveCSS('border-top-left-radius', expected)
        await expect(numbers.nth(1)).toHaveCSS('border-top-right-radius', expected)
        await expect(numbers.nth(0).locator('.el-input-number__decrease')).toHaveCSS('border-top-left-radius', expected)
        await expect(numbers.nth(0).locator('.el-input-number__increase')).toHaveCSS('border-bottom-right-radius', expected)
        await expect(numbers.nth(1).locator('.el-input-number__increase')).toHaveCSS('border-top-right-radius', expected)
        await expect(numbers.nth(1).locator('.el-input-number__decrease')).toHaveCSS('border-bottom-right-radius', expected)
        const segments = row.locator('.el-radio-button__inner')
        await expect(segments.nth(0)).toHaveCSS('border-top-left-radius', expected)
        await expect(segments.nth(1)).toHaveCSS('border-top-left-radius', '0px')
        await expect(segments.nth(2)).toHaveCSS('border-top-right-radius', expected)
      }
      const group = page.locator('.case-group')
      await expect(group.locator('.el-input__wrapper')).toHaveCSS('border-top-left-radius', '0px')
      await expect(group.locator('.el-input__wrapper')).toHaveCSS('border-top-right-radius', '0px')
      await expect(group.locator('.el-input-group__prepend')).toHaveCSS('border-top-left-radius', `${Math.max(0, radius - 4)}px`)
      await expect(group.locator('.el-radio-button__inner')).toHaveCSS('border-top-left-radius', `${Math.max(0, radius - 4)}px`)
      await expect(page.locator('.mrr-status-tag')).toHaveCSS('border-top-left-radius', `${Math.max(0, radius - 6)}px`)
      await expect(page.locator('.case-tags .el-tag').first()).toHaveCSS('border-top-left-radius', `${Math.max(0, radius - 6)}px`)
      await expect(page.locator('.case-tags .el-tag.is-round')).toHaveCSS('border-top-left-radius', '9999px')
      await expect(page.locator('.case-tags .el-checkbox__inner')).toHaveCSS('border-top-left-radius', `${Math.min(4, Math.max(0, radius - 6))}px`)
      await expect(page.getByRole('button', { name: '圆形按钮' })).toHaveCSS('border-top-left-radius', '50%')
      const smallRadius = `${Math.max(0, radius - 6)}px`
      const controlRadius = `${Math.max(0, radius - 4)}px`
      await expect(page.locator('.case-other .btn-prev')).toHaveCSS('border-top-left-radius', smallRadius)
      await expect(page.locator('.case-other .el-pager li').first()).toHaveCSS('border-top-left-radius', smallRadius)
      await expect(page.locator('.tabs-top .el-tabs__nav')).toHaveCSS('border-top-left-radius', controlRadius)
      await expect(page.locator('.tabs-left .el-tabs__nav')).toHaveCSS('border-bottom-left-radius', controlRadius)
      await expect(page.locator('.tabs-right .el-tabs__nav')).toHaveCSS('border-bottom-right-radius', controlRadius)
      await expect(page.locator('.tabs-bottom .el-tabs__nav')).toHaveCSS('border-bottom-left-radius', controlRadius)
      const buttons = page.locator('.case-other .el-button-group .el-button')
      await expect(buttons.nth(0)).toHaveCSS('border-top-right-radius', '0px')
      await expect(buttons.nth(1)).toHaveCSS('border-top-left-radius', '0px')
      await expect(buttons.nth(2)).toHaveCSS('border-top-left-radius', '0px')
    }
    await page.locator('.case-small .el-input-number__increase').first().click()
    await expect(page.locator('.case-small .el-input-number input').first()).toHaveValue('2')
    await page.locator('.case-small .el-radio-button__inner').nth(1).click()
    await expect(page.getByRole('radio', { name: '选项 B' }).first()).toBeChecked()
    await page.locator('.case-tags .el-tag__close').click()
    await expect(page.getByText('可关闭标签')).toHaveCount(0)
  })
}
