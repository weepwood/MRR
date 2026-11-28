import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const [palette, theme] of [['mrr', 'light'], ['mrr', 'dark'], ['gr8r', 'light'], ['gr8r', 'dark']] as const) {
  test(`${palette}/${theme} 无影像占位、布局与正常结果恢复`, async ({ page }, info) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { themePalette: palette, colorScheme: theme, enableMotion: false }, layout: { enableMobileAdaptation: true } })), { palette, theme })
    let result = false
    await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: { code: 200, data: result ? [{ id: 94001, bah: '94001', sjh: '', filename: 'synthetic.png', btype: 3, pages: 1, uploadFlag: 1, img_url: '/mock/scan-synthetic.png' }] : [], meta: { requestId: 'synthetic-empty', reasonCode: result ? undefined : 'NO_MATCHING_IMAGES' } } }))
    await page.route(/\/api\/v1\/search\/patient\/[^/?]+/, route => route.fulfill({ json: { code: 200, data: [] } }))
    await page.route(/\/mock\/scan-synthetic\.png(?:\?|$)/, route => route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="40" height="60"><rect width="40" height="60" fill="white"/></svg>' }))
    await page.goto('/archive/embed?bah=94001')
    const preview = page.locator('.archive-preview-empty')
    const thumbs = page.locator('.thumb-strip.is-empty')
    await expect(preview.getByText('未查询到影像', { exact: true })).toBeVisible()
    await expect(preview.locator('svg.archive-empty-art')).toBeVisible()
    await expect(thumbs.locator('svg.archive-empty-art--thumb')).toBeVisible()
    await expect(page.getByRole('button', { name: '排查本次查询' })).toBeVisible()
    await expect(page.getByRole('textbox', { name: '本次请求追踪编号' })).toHaveValue('synthetic-empty')
    await expect(page.locator('.preview-stage img,.thumb-item,.archive-bottom-actions')).toHaveCount(0)
    for (const width of [1920, 1440, 1024, 375]) {
      await page.setViewportSize({ width, height: 900 })
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width + 1)
      const bounds = await preview.evaluate((el) => {
        const box = el.getBoundingClientRect()
        const art = el.querySelector('.app-empty')!.getBoundingClientRect()
        return { dx: Math.abs(box.x + box.width / 2 - art.x - art.width / 2), dy: Math.abs(box.y + box.height / 2 - art.y - art.height / 2), width: box.width, height: box.height }
      })
      expect(bounds.dx).toBeLessThanOrEqual(2)
      expect(bounds.dy).toBeLessThanOrEqual(2)
      expect(bounds.height).toBeGreaterThan(200)
      await page.screenshot({ path: info.outputPath(`empty-${width}.png`), fullPage: true })
    }
    await page.setViewportSize({ width: 1440, height: 900 })
    result = true
    await page.getByRole('button', { name: '重新查询', exact: true }).click()
    await expect(page.locator('.preview-stage img.preview-image')).toBeVisible()
    await expect(page.locator('.archive-empty-art')).toHaveCount(0)
    await expect(page.locator('.thumb-item')).toHaveCount(1)
  })
}

test('请求异常不显示无匹配插画，加载中不提前宣称无影像', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/api/v1/img/search?**', async (route) => {
    await gate
    await route.fulfill({ status: 500, json: { code: 500, message: '合成查询异常', meta: { reasonCode: 'QUERY_FAILED' } } })
  })
  try {
    await page.goto('/archive/embed?bah=94001', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.archive-preview-empty')).toHaveAttribute('aria-busy', 'true')
    await expect(page.locator('.archive-empty-art')).toHaveCount(0)
    release()
    await expect(page.locator('.archive-preview-empty').getByText('查询未完成', { exact: true })).toBeVisible()
    await expect(page.locator('.archive-empty-art')).toHaveCount(0)
    await expect(page.locator('.query-failure')).toHaveAttribute('data-tone', 'danger')
  }
  finally { release() }
})
