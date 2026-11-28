import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { themePalette: 'gr8r', colorScheme: 'dark', enableMotion: false },
    menu: { mode: 'single' },
    layout: { enableMobileAdaptation: true },
  })))
  await page.setViewportSize({ width: 1440, height: 900 })
})

test('经典局部明暗配色与状态矩阵不污染全站', async ({ page }, testInfo) => {
  await page.goto('/ui-components')
  await page.getByLabel('查找组件').fill('按钮与操作')
  const preview = page.getByRole('region', { name: '局部主题与尺寸对照' })
  const surface = preview.locator('.local-theme-preview__surface')
  await expect(surface).toBeVisible()
  const before = await page.evaluate(() => ({
    settings: localStorage.getItem('MRR-ADMIN:app-settings'),
    card: getComputedStyle(document.documentElement).getPropertyValue('--mrr-card'),
  }))
  await preview.getByText('经典亮', { exact: true }).click()
  await expect(surface).toHaveCSS('background-color', 'rgb(255, 255, 255)')
  await expect(surface.locator('.el-button--primary').first()).toHaveCSS('background-color', 'rgb(37, 99, 235)')
  await preview.getByLabel('局部预览输入').fill('保留状态')
  await preview.getByText('经典暗', { exact: true }).click()
  await expect(surface).toHaveAttribute('data-preview-scheme', 'classic-dark')
  await expect(surface).not.toHaveCSS('background-color', 'rgb(255, 255, 255)')
  await expect(preview.getByLabel('局部预览输入')).toHaveValue('保留状态')
  const matrix = preview.locator('.local-theme-preview__matrix')
  await matrix.getByText('禁用', { exact: true }).click()
  await expect(matrix.getByRole('button', { name: '执行示例' })).toBeDisabled()
  await matrix.getByText('默认', { exact: true }).click()
  await expect(matrix.getByRole('button', { name: '执行示例' })).toBeEnabled()
  await matrix.getByLabel('局部校验输入').fill('通过')
  await expect(matrix.getByText('请输入示例值')).toHaveCount(0)
  await expect(matrix.getByRole('switch', { name: '局部禁用开关' })).toBeDisabled()
  const number = matrix.getByRole('spinbutton', { name: '局部数字输入' })
  await number.focus()
  await number.press('ArrowUp')
  await expect(number).toHaveValue('4')
  await matrix.locator('.el-select__wrapper').click()
  const popper = surface.locator('.el-select__popper:visible')
  await expect(popper).toBeVisible()
  await expect(popper).toHaveCSS('background-color', await surface.evaluate((el) => {
    const probe = document.createElement('span')
    probe.style.backgroundColor = 'var(--mrr-popover)'
    el.appendChild(probe)
    const color = getComputedStyle(probe).backgroundColor
    probe.remove()
    return color
  }))
  await popper.getByRole('option', { name: '归档', exact: true }).click()
  await expect(matrix.locator('.el-select')).toContainText('归档')

  expect(await page.evaluate(() => ({
    settings: localStorage.getItem('MRR-ADMIN:app-settings'),
    card: getComputedStyle(document.documentElement).getPropertyValue('--mrr-card'),
  }))).toEqual(before)
  await preview.screenshot({ path: testInfo.outputPath('classic-dark.png'), animations: 'disabled' })
  await page.setViewportSize({ width: 375, height: 900 })
  await expect.poll(() => preview.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
})

test('管理员权限全宽分组且其他角色可编辑', async ({ page }, testInfo) => {
  await page.goto('/permissions')
  const grid = page.locator('.roles-grid')
  const admin = grid.locator('.role-card.admin')
  await expect(admin).toBeVisible()
  await expect.poll(() => admin.evaluate(el => Math.abs(el.getBoundingClientRect().width - el.parentElement!.clientWidth))).toBeLessThan(2)
  await expect(admin.locator('.permission-groups section').first()).toBeVisible()
  await expect(grid.locator('.role-card:not(.admin)').first().getByRole('button', { name: '编辑' })).toBeEnabled()
  await page.screenshot({ path: testInfo.outputPath('permissions.png'), animations: 'disabled' })
  await page.setViewportSize({ width: 375, height: 900 })
  await expect.poll(() => grid.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
})

test('热力图月份与格子对齐、键盘焦点可见', async ({ page }, testInfo) => {
  await page.goto('/response-analysis')
  const map = page.locator('.request-heatmap')
  await expect(map).toBeVisible()
  const offset = await map.evaluate(el => Math.abs(el.querySelector('.month-labels')!.getBoundingClientRect().left - el.querySelector('.heatmap-grid')!.getBoundingClientRect().left))
  expect(offset).toBeLessThan(1)
  const cell = map.locator('button.heatmap-cell').first()
  await page.keyboard.press('Tab')
  await cell.focus()
  await expect(cell).toHaveCSS('outline-style', 'solid')
  await expect(page.locator('.heatmap-tooltip')).toBeVisible()
  await map.screenshot({ path: testInfo.outputPath('heatmap.png'), animations: 'disabled' })
})
