import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

async function prepare(page: Page, width: number) {
  await page.setViewportSize({ width, height: 900 })
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: 'light', radius: 0.75 },
      layout: { enableMobileAdaptation: true },
    }))
  })
}

async function expectNoOverflow(page: Page, selector: string) {
  await expect(page.locator(selector).first()).toBeVisible()
  await expect.poll(() => page.locator(selector).evaluateAll(elements => elements
    .filter(element => element.getBoundingClientRect().width > 0 && element.scrollWidth > element.clientWidth + 1)
    .map(element => ({ class: element.className, width: element.clientWidth, scroll: element.scrollWidth }))), { timeout: 3000 }).toEqual([])
}

async function expectControlsInside(page: Page, selector: string, container = '.main') {
  await expect.poll(() => page.locator(selector).evaluateAll((elements, container) => elements.flatMap((element) => {
    if (element.closest('.el-table, .el-popper')) {
      return []
    }
    const rect = element.getBoundingClientRect()
    const parent = element.closest(container)?.getBoundingClientRect()
    return rect.width > 0 && parent && (rect.left < parent.left - 1 || rect.right > parent.right + 1)
      ? [{ class: element.className, left: rect.left, right: rect.right, parent: { left: parent.left, right: parent.right } }]
      : []
  }), container), { timeout: 3000 }).toEqual([])
}

for (const path of ['/logs', '/runtime-logs', '/patients', '/records', '/data-exchange', '/statistics-detail', '/auth-test']) {
  test(`${path} 窄屏控件与分页不被工作区裁切`, async ({ page }) => {
    await prepare(page, 375)
    await page.goto(path)
    await expect(page.locator('.page-header')).toBeVisible()
    await expectControlsInside(page, '.el-input, .el-select, .el-date-editor, .el-input-number, .page-header .el-button')
    if (await page.locator('.el-pagination').count()) {
      await expectNoOverflow(page, '.el-pagination')
      await expectControlsInside(page, '.el-pagination')
    }
  })
}

test('记录分页各控件保持可见间距并垂直对齐', async ({ page }) => {
  await prepare(page, 1366)
  await page.goto('/records')
  const pager = page.locator('.pager')
  await expect(pager).toBeVisible()
  const boxes = await pager.locator(':scope > .el-select, .pager__navigation > *').evaluateAll(elements => elements.map((element) => {
    const rect = element.getBoundingClientRect()
    return { left: rect.left, right: rect.right, middle: rect.top + rect.height / 2 }
  }))
  for (let index = 1; index < boxes.length; index++) {
    expect(boxes[index].left - boxes[index - 1].right).toBeGreaterThanOrEqual(7)
    expect(Math.abs(boxes[index].middle - boxes[index - 1].middle)).toBeLessThanOrEqual(1)
  }
})

for (const section of ['archive', 'security', 'ocr', 'department', 'external-links', 'appearance']) {
  test(`设置 ${section} 在双侧栏和窄屏下控件不溢出`, async ({ page }) => {
    test.setTimeout(90_000)
    await prepare(page, 1024)
    await page.goto(`/settings?section=${section}`)
    await expect(page.locator('.settings-content')).toBeVisible()
    if (section === 'department') {
      await page.getByRole('button', { name: '添加科室配色', exact: true }).click()
      await expect(page.locator('.theme-editor-row')).toBeVisible()
    }
    for (const width of [1024, 1366, 375]) {
      await page.setViewportSize({ width, height: 900 })
      await expectNoOverflow(page, '.settings-content')
      await expectControlsInside(page, '.settings-content .el-input, .settings-content .el-input-number, .settings-content .el-select, .settings-content .el-button', '.settings-content')
      if (['archive', 'security', 'ocr'].includes(section)) {
        await expectNoOverflow(page, '.control-grid .el-form-item__content')
      }
      if (section === 'department') {
        await expect(page.locator('.color-field__label').filter({ hasText: '档案袋主色' })).toBeVisible()
        await expect(page.locator('.color-field__label').filter({ hasText: '顶部色条' })).toBeVisible()
      }
      if (width === 375 && ['department', 'appearance'].includes(section)) {
        const copy = page.locator(section === 'department' ? '.department-theme-header > div:first-child' : '.config-summary > div:first-child')
        await expect.poll(async () => (await copy.boundingBox())!.height).toBeLessThan(120)
      }
      if (section === 'appearance') {
        for (const header of await page.locator('.el-collapse-item__header').all()) {
          await header.click()
          await expectNoOverflow(page, '.config-summary, .setting-item')
        }
      }
    }
  })
}

test('表格数字输入完整容纳于单元格', async ({ page }) => {
  await prepare(page, 1366)
  await page.goto('/settings?section=ocr')
  await page.getByRole('button', { name: '添加分类规则', exact: true }).click()
  const weights = page.locator('.classification-rules .el-table .el-input-number')
  await expect(weights.first()).toBeAttached()
  const widths = await weights.evaluateAll(elements => elements.map(element => ({
    control: element.getBoundingClientRect().width,
    cell: element.closest('.cell')!.clientWidth,
  })))
  for (const width of widths) {
    expect(width.control).toBeLessThanOrEqual(width.cell)
  }
})

for (const path of ['/logs', '/statistics-detail']) {
  test(`${path} 窄屏日期范围弹出层可完整选择日期`, async ({ page }) => {
    await prepare(page, 375)
    await page.goto(path)
    const editor = page.locator('.el-date-editor').first()
    await editor.click()
    const popup = page.locator('.el-picker__popper:visible')
    await expect(popup).toBeVisible()
    await expect.poll(() => popup.evaluate((element) => {
      const rect = element.getBoundingClientRect()
      return rect.left >= -1 && rect.right <= innerWidth + 1 && rect.top >= -1 && rect.bottom <= innerHeight + 1
    }), { timeout: 3000 }).toBe(true)
    await expectNoOverflow(page, '.el-date-range-picker')
    const start = popup.locator('.is-left td.available:not(.prev-month):not(.next-month)').nth(3)
    const end = popup.locator('.is-right td.available:not(.prev-month):not(.next-month)').nth(7)
    await start.click()
    await end.click()
    if (path === '/logs') {
      await popup.getByRole('button', { name: '确定', exact: true }).click()
    }
    await expect(popup).toBeHidden()
    for (const input of await editor.locator('.el-range-input').all()) {
      await expect(input).not.toHaveValue('')
    }
  })
}

for (const viewport of [{ width: 375, height: 480 }, { width: 320, height: 550 }]) {
  test(`${viewport.width}×${viewport.height} 日期时间嵌套面板可见且能确认`, async ({ page }) => {
    await prepare(page, viewport.width)
    await page.setViewportSize(viewport)
    await page.goto('/logs')
    const editor = page.locator('.el-date-editor')
    await editor.click()
    const popup = page.locator('.el-picker__popper:visible')
    await popup.locator('.is-left td.available:not(.prev-month):not(.next-month)').nth(3).click()
    await popup.locator('.is-right td.available:not(.prev-month):not(.next-month)').nth(7).click()
    const endTime = popup.locator('.el-date-range-picker__editors-wrap.is-right input').nth(1)
    await endTime.click()
    const timePanel = popup.locator('.el-time-panel:visible')
    await expect(timePanel).toBeVisible()
    await expect.poll(() => timePanel.evaluate((element) => {
      const rect = element.getBoundingClientRect()
      const parent = element.closest('.el-date-range-picker')!.getBoundingClientRect()
      return rect.left >= parent.left - 1 && rect.right <= parent.right + 1 && rect.top >= -1 && rect.bottom <= innerHeight + 1
    })).toBe(true)
    await endTime.fill('13:45:30')
    await timePanel.getByRole('button', { name: '确定', exact: true }).click()
    await expect(timePanel).toBeHidden()
    await popup.locator('.el-picker-panel__footer').getByRole('button', { name: '确定', exact: true }).click()
    await expect(popup).toBeHidden()
    await expect(editor.locator('.el-range-input').nth(1)).toHaveValue(/13:45:30/)
  })
}
