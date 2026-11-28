import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const [palette, theme] of [['mrr', 'light'], ['mrr', 'dark'], ['gr8r', 'light'], ['gr8r', 'dark']] as const) {
  test(`${palette}/${theme} 医疗工作台矢量空态及布局预览`, async ({ page }, info) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { themePalette: palette, colorScheme: theme, enableMotion: false }, layout: { enableMobileAdaptation: true } })), { palette, theme })
    await page.route('**/api/v1/system/data-quality/repair-orders**', route => route.fulfill({ json: { code: 200, data: { enabled: false, actorId: 12, orders: [] } } }))
    await page.route('**/api/v1/oss/browser?*', route => route.fulfill({ json: { code: 200, data: { configured: true, bucket: 'mrr-test', endpoint: 'https://oss.example.test', rootPrefix: 'medical-records/', prefix: 'medical-records/', truncated: false, maxKeys: 200, loadedDirectories: 0, loadedFiles: 0, loadedBytes: 0, entries: [] } } }))
    const art = (variant: string) => page.locator(`svg[data-illustration="${variant}"]`)
    const check = async (variant: string, label: string) => {
      await expect(art(variant)).toBeVisible()
      await expect(art(variant)).toHaveAttribute('aria-hidden', 'true')
      await expect(art(variant)).toHaveAttribute('focusable', 'false')
      await expect(page.getByText(label, { exact: true }).first()).toBeVisible()
      for (const width of [1440, 390]) {
        await page.setViewportSize({ width, height: 900 })
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width + 1)
        const box = (await art(variant).boundingBox())!
        expect(box.width).toBeGreaterThan(20)
        expect(box.x).toBeGreaterThanOrEqual(0)
        expect(box.x + box.width).toBeLessThanOrEqual(width + 1)
        await page.screenshot({ path: info.outputPath(`${variant}-${width}.png`), fullPage: true })
      }
      await page.setViewportSize({ width: 1440, height: 900 })
    }
    await page.goto('/archive-workbench')
    await check('archive', '开始病案核对')
    const query = page.getByRole('textbox', { name: '病案查询值' })
    await query.fill('26070000')
    await query.press('Enter')
    await expect(page.getByText('病案主档 #1', { exact: true })).toBeVisible()
    await expect(art('archive')).toHaveCount(0)
    await page.goto('/data-relations')
    await page.getByRole('tab', { name: '病案工作台', exact: true }).click()
    await check('relations', '查询并选择一份病案后查看跨表关系')
    await page.getByRole('tab', { name: '修复工单', exact: true }).click()
    await check('repair', '暂无修复工单')
    await expect(page.getByText('修复写入不可用，现有工单仅供查看')).toBeVisible()
    await expect(page.getByRole('button', { name: '提交工单', exact: true })).toHaveCount(0)
    await page.goto('/oss-browser')
    await check('folder', '此目录为空')
    await page.getByRole('textbox', { name: '筛选当前页' }).fill('unmatched')
    await check('search', '当前页没有匹配的条目')
    await page.getByRole('button', { name: '清除筛选' }).click()
    await expect(art('folder')).toBeVisible()
    await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: { code: 200, data: [{ id: 93001, bah: '00009301', sjh: '', filename: 'synthetic.png', btype: 3, pages: 1, uploadFlag: 1, img_url: '/mock/scan-svg-workbench.png' }] } }))
    await page.route(/\/mock\/scan-svg-workbench\.png(?:\?|$)/, route => route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="40" height="60"><rect width="40" height="60" fill="white"/></svg>' }))
    await page.route(/\/api\/v1\/search\/patient\/[^/?]+/, route => route.fulfill({ json: { code: 200, data: [] } }))
    await page.goto('/archive?bah=00009301')
    await expect(page.locator('.thumb-item').first()).toBeVisible()
    await page.getByRole('button', { name: '更多显示设置' }).click()
    const panel = page.locator('.archive-more-settings')
    await expect(art('standard')).toBeVisible()
    await expect(art('wall')).toBeVisible()
    const wall = panel.getByRole('radio', { name: '全景平铺', exact: true })
    await wall.focus()
    await wall.press('Space')
    await expect(wall).toBeChecked()
    await expect(page.locator('.archive-wall')).toBeVisible()
    const standard = panel.getByRole('radio', { name: '标准浏览', exact: true })
    await standard.focus()
    await standard.press('Space')
    await expect(standard).toBeChecked()
    await expect(page.locator('.preview-panel')).toBeVisible()
    await expect(page.locator('.thumb-item').first()).toBeVisible()
    await page.screenshot({ path: info.outputPath('layout-preview.png') })
  })
}

test('存储读取失败与未配置不展示成功空目录插画', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.route('**/api/v1/oss/browser?*', route => route.fulfill({ status: 403, json: { code: 403, message: '没有权限' } }))
  await page.goto('/oss-browser')
  await expect(page.locator('.browser-notice')).toBeVisible()
  await expect(page.locator('svg[data-illustration]')).toHaveCount(0)
})

test('Nginx 空目录、加载和失败分离', async ({ page }, info) => {
  await isolateLocalVisualSettings(page)
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/api/v1/nginx-browser/servers', route => route.fulfill({ json: { code: 200, data: [{ key: 'default', name: '默认节点', configured: true }] } }))
  await page.route(/\/api\/v1\/nginx-browser(?:\?|$)/, async (route) => {
    await gate
    await route.fulfill({ json: { code: 200, data: { server: 'default', path: '', offset: 0, limit: 200, totalEntries: 0, loadedDirectories: 0, loadedFiles: 0, loadedBytes: 0, entries: [] } } })
  })
  try {
    await page.goto('/nginx-browser', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.file-pane')).toHaveAttribute('aria-busy', 'true')
    await expect(page.locator('svg[data-illustration="folder"]')).toHaveCount(0)
    release()
    await expect(page.locator('svg[data-illustration="folder"]')).toBeVisible()
    await expect(page.getByText('此目录为空', { exact: true })).toBeVisible()
    await page.screenshot({ path: info.outputPath('nginx-empty.png'), fullPage: true })
  }
  finally { release() }
})
