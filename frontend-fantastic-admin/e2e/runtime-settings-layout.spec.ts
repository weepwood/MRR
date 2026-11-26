import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('运行参数筛选不丢草稿，当前生效值与待保存值明确区分', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  let writes = 0
  page.on('request', (request) => {
    if (request.method() !== 'GET' && /\/api\/v1\/settings$/.test(request.url())) { writes++ }
  })
  await page.goto('/settings?section=runtime')
  const search = page.getByRole('textbox', { name: '搜索运行参数' })
  const row = page.locator('[data-setting-key="loginMaxAttempts"]')
  await expect(row).toBeVisible()
  const input = row.getByRole('spinbutton')
  const original = await input.inputValue()
  const changed = original === '10' ? '11' : '10'
  await input.fill(changed)
  await expect(row).toContainText('待保存')
  await expect(row).toContainText(`当前生效值 ${original}`)
  await search.fill('不存在的合成参数')
  await expect(page.getByText('没有匹配的运行参数', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '保存运行参数', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '清除筛选' }).click()
  await expect(input).toHaveValue(changed)
  await page.locator('.runtime-filter .el-segmented').getByText('待保存', { exact: true }).click()
  await expect(page.locator('.runtime-row')).toHaveCount(1)
  await input.fill(original)
  await expect(page.getByRole('button', { name: '保存运行参数', exact: true })).toBeDisabled()
  expect(writes).toBe(0)
})

for (const colorScheme of ['light', 'dark']) {
  test(`运行参数 ${colorScheme} 分区边缘、控件及宽窄屏布局`, async ({ page }) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(colorScheme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme, enableMotion: false, pageTitleStyle: 'card' }, layout: { enableMobileAdaptation: true } })), colorScheme)
    await page.goto('/settings?section=runtime')
    await expect(page.locator('.runtime-row').first()).toBeVisible()
    for (const width of [1920, 1440, 1024, 375]) {
      await page.setViewportSize({ width, height: 900 })
      await expect.poll(() => page.locator('.runtime-settings').evaluate((el) => {
        const header = document.querySelector('.settings-panel-header')!.getBoundingClientRect()
        return [...el.querySelectorAll('.setting-group,.runtime-row,.runtime-value,.runtime-filter')].every((node) => {
          const r = node.getBoundingClientRect()
          return r.left >= header.left - 1 && r.right <= header.right + 1 && node.scrollWidth <= node.clientWidth + 1
        })
      })).toBe(true)
      await expect(page.locator('.runtime-settings > .el-loading-mask')).toHaveCount(0)
      await page.screenshot({ path: test.info().outputPath(`${colorScheme}-${width}.png`) })
    }
  })
}
