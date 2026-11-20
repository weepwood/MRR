import { Buffer } from 'node:buffer'
import { expect, test } from '@playwright/test'

const reply = (data: unknown) => ({ code: 200, message: 'success', data })

const TINY_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
)

/** 影像地址由门闸控制放行：模拟慢速网络下图片迟迟未返回。 */
async function gateImageRoute(page: import('@playwright/test').Page, image = 'scan-1.png') {
  let release: (() => void) | undefined
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route(`**/mock/${image}`, async (route) => {
    await gate
    await route.fulfill({ status: 200, contentType: 'image/png', body: TINY_PNG })
  })
  return () => release!()
}

async function mockArchivePage(page: import('@playwright/test').Page, count = 1) {
  const images = Array.from({ length: count }, (_, index) => ({
    id: 93001 + index,
    brxh: 'BR-93001',
    bah: '00009301',
    sjh: '',
    filename: `synthetic-${index + 1}.png`,
    btype: 3,
    pages: index + 1,
    uploadFlag: 1,
    img_url: `/mock/scan-${index + 1}.png`,
  }))
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: reply(images) }))
  await page.route(/\/api\/v1\/search\/patient\/[^/?]+/, route => route.fulfill({
    json: reply({ bah: '00009301', name: '合成患者', department: '合成科室' }),
  }))
  await page.route('**/api/v1/archive-search-history**', route => route.fulfill({ json: reply([]) }))
}

test('慢速网络：预览图未就绪时显示加载占位与提示，就绪后自动退场', async ({ page }) => {
  const release = await gateImageRoute(page)
  await mockArchivePage(page)
  await page.goto('/archive?bah=00009301')

  const img = page.locator('.preview-stage img.preview-image').first()
  const tip = page.locator('.img-loading-tip').first()
  await expect(tip).toBeVisible()
  await expect(tip).toContainText('影像加载中')
  await expect(img).not.toHaveClass(/is-loaded/)

  release()
  await expect(img).toHaveClass(/is-loaded/)
  await expect(tip).toBeHidden()
})

test('慢速网络：缩略图加载中带微光占位，就绪后打标退场', async ({ page }) => {
  const release = await gateImageRoute(page)
  await mockArchivePage(page)
  await page.goto('/archive?bah=00009301')

  const thumbImg = page.locator('.thumb-item img').first()
  await expect(thumbImg).toBeVisible()
  await expect(thumbImg).not.toHaveClass(/is-loaded/)
  // 微光占位在 img 自身背景上：加载中能读到渐变
  const shimmering = await thumbImg.evaluate(img => getComputedStyle(img).backgroundImage.includes('linear-gradient'))
  expect(shimmering, '加载中的缩略图应有微光渐变背景').toBe(true)

  release()
  await expect(thumbImg).toHaveClass(/is-loaded/)
})

test('慢速网络：从已加载影像切到下一张时恢复加载提示', async ({ page }) => {
  await page.route('**/mock/scan-1.png', route => route.fulfill({ status: 200, contentType: 'image/png', body: TINY_PNG }))
  const release = await gateImageRoute(page, 'scan-2.png')
  await mockArchivePage(page, 2)

  try {
    await page.goto('/archive?bah=00009301')
    const img = page.locator('.single-stage img.preview-image')
    const tip = page.locator('.single-stage .img-loading-tip')
    await expect(img).toHaveClass(/is-loaded/)
    await expect(tip).toBeHidden()

    await page.locator('.thumb-item').nth(1).click()
    await expect(img).toHaveAttribute('src', /scan-2\.png/)
    await expect(img).not.toHaveClass(/is-loaded/, { timeout: 3000 })
    await expect(tip).toBeVisible()

    release()
    await expect(img).toHaveClass(/is-loaded/)
    await expect(tip).toBeHidden()
  }
  finally {
    release()
  }
})
