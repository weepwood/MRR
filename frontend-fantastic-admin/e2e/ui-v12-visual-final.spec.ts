import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const palette of ['mrr', 'gr8r']) {
  test(`热力图自适应宽度与错误详情层次 ${palette}`, async ({ page }, testInfo) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(palette => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { themePalette: palette, colorScheme: 'dark', enableMotion: false },
      menu: { mode: 'single' },
      layout: { enableMobileAdaptation: true },
    })), palette)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/response-analysis')
    const map = page.locator('.request-heatmap')
    const cell = map.locator('button.heatmap-cell').first()
    await expect.poll(() => cell.evaluate(el => el.getBoundingClientRect().width)).toBeGreaterThan(11)
    await expect.poll(() => map.locator('.heatmap-scroll').evaluate(el => el.scrollWidth <= el.clientWidth + 2)).toBe(true)
    const days = await map.locator('button.heatmap-cell').count()
    expect(days).toBe(365)
    await map.screenshot({ path: testInfo.outputPath('heatmap-wide.png'), animations: 'disabled' })
    await page.setViewportSize({ width: 375, height: 900 })
    await expect.poll(() => cell.evaluate(el => el.getBoundingClientRect().width)).toBe(11)
    await expect(map.locator('button.heatmap-cell')).toHaveCount(days)
    await expect.poll(() => map.evaluate(el => el.scrollWidth <= el.clientWidth + 2)).toBe(true)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/runtime-errors')
    await page.getByRole('row').filter({ hasText: 'DEMO-ERR-001' }).getByRole('button', { name: '详情', exact: true }).click()
    const detail = page.getByRole('dialog', { name: '运行错误详情' })
    for (const title of ['事件概况', '请求与技术信息', '错误摘要', '异常堆栈']) {
      await expect(detail.getByRole('heading', { name: title, exact: true })).toBeVisible()
    }
    await expect(detail.getByText('DEMO-ERR-001', { exact: true })).toBeVisible()
    await detail.screenshot({ path: testInfo.outputPath('error-detail.png'), animations: 'disabled' })
    await page.setViewportSize({ width: 375, height: 900 })
    await expect.poll(() => detail.evaluate(el => el.scrollWidth <= el.clientWidth + 2)).toBe(true)
  })
}

test('局部日期与多行输入保留状态且不影响全站主题', async ({ page }, testInfo) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { themePalette: 'mrr', colorScheme: 'light', enableMotion: false },
    menu: { mode: 'single' },
    layout: { enableMobileAdaptation: true },
  })))
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/ui-components')
  await page.getByLabel('查找组件').fill('按钮与操作')
  const preview = page.getByRole('region', { name: '局部主题与尺寸对照' })
  const surface = preview.locator('.local-theme-preview__surface')
  await preview.getByText('暖色暗', { exact: true }).click()
  const settings = await page.evaluate(() => localStorage.getItem('MRR-ADMIN:app-settings'))
  await preview.getByLabel('局部备注').fill('局部备注保留')
  const date = preview.getByLabel('局部日期', { exact: true })
  await date.click()
  const popper = surface.locator('.el-picker__popper:visible')
  await expect(popper).toBeVisible()
  await expect(popper).toHaveCSS('background-color', 'rgb(34, 34, 32)')
  await date.press('Escape')
  await expect(popper).not.toBeVisible()
  await preview.getByText('经典亮', { exact: true }).click()
  await expect(preview.getByLabel('局部备注')).toHaveValue('局部备注保留')
  expect(await page.evaluate(() => localStorage.getItem('MRR-ADMIN:app-settings'))).toBe(settings)
  await preview.screenshot({ path: testInfo.outputPath('local-fields.png'), animations: 'disabled' })
})

test('局部暗色状态矩阵不污染全局对话框和抽屉', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { themePalette: 'mrr', colorScheme: 'light', enableMotion: false },
    menu: { mode: 'single' },
    layout: { enableMobileAdaptation: true },
  })))
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/ui-components')
  const preview = page.getByRole('region', { name: '局部主题与尺寸对照' })
  await preview.getByText('暖色暗', { exact: true }).click()
  const surface = preview.locator('.local-theme-preview__surface')
  await expect(surface).toHaveCSS('background-color', 'rgb(34, 34, 32)')
  const state = preview.getByRole('radiogroup', { name: '局部操作状态' })
  await state.getByText('加载', { exact: true }).click()
  await expect(preview.getByRole('button', { name: '执行示例' })).toBeDisabled()
  await state.getByText('默认', { exact: true }).click()
  await expect(preview.getByRole('button', { name: '执行示例' })).toBeEnabled()
  const mixed = preview.getByRole('checkbox', { name: '半选示例' })
  await expect(mixed).toHaveJSProperty('indeterminate', true)
  await mixed.focus()
  await mixed.press('Space')
  await expect(mixed).toHaveJSProperty('indeterminate', false)
  await expect(mixed).toBeChecked()
  const before = await page.evaluate(() => {
    const probe = document.createElement('span')
    probe.style.backgroundColor = 'var(--mrr-card)'
    document.body.appendChild(probe)
    const card = getComputedStyle(probe).backgroundColor
    probe.style.backgroundColor = 'hsl(var(--background))'
    const result = { card, drawer: getComputedStyle(probe).backgroundColor, settings: localStorage.getItem('MRR-ADMIN:app-settings') }
    probe.remove()
    return result
  })
  for (const [trigger, title, panelClass] of [['打开对话框', '对话框示例', '.gallery-demo-dialog'], ['打开抽屉', '抽屉示例', '.gallery-demo-drawer']]) {
    await page.getByRole('button', { name: trigger, exact: true }).click()
    const overlay = page.getByRole('dialog', { name: title, exact: true })
    await expect(overlay).toBeVisible()
    const panel = page.locator(panelClass)
    await expect(panel).toBeVisible()
    await expect(panel).toHaveCSS('background-color', panelClass === '.gallery-demo-dialog' ? before.card : before.drawer)
    expect(await panel.evaluate(el => el.closest('.local-theme-preview__surface'))).toBeNull()
    await page.keyboard.press('Escape')
    await expect(overlay).not.toBeVisible()
  }
  await expect(surface).toHaveCSS('background-color', 'rgb(34, 34, 32)')
  await expect(mixed).toBeChecked()
  expect(await page.evaluate(() => localStorage.getItem('MRR-ADMIN:app-settings'))).toBe(before.settings)
})
