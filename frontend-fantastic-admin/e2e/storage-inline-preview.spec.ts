import type { Route } from '@playwright/test'
import { expect, test } from '@playwright/test'

const cases = [
  { route: '/nginx-browser', path: '样例目录/', image: '示例图标.png', text: '存储说明.txt', excerpt: 'MRR 文件浏览演示数据' },
  { route: '/oss-browser', path: '0012/00123456-00789124/', image: '0001.jpg', text: '病案目录说明.txt', excerpt: 'OSS 文件浏览演示数据' },
]

for (const fixture of cases) {
  test(`${fixture.route} 选中文件即在详情内预览图片和文本`, async ({ page }) => {
    await page.goto(fixture.route)
    const address = page.getByRole('textbox', { name: '目录路径' })
    await address.fill(fixture.path)
    await address.press('Enter')

    const files = page.getByRole('table', { name: '文件列表' })
    await files.getByRole('button', { name: fixture.image, exact: true }).click()
    const details = page.getByRole('complementary', { name: '文件详情' })
    const preview = details.getByRole('region', { name: '内容预览' })
    const image = preview.getByRole('img', { name: fixture.image })
    await expect(image).toBeVisible()
    await expect.poll(() => image.evaluate((node: HTMLImageElement) => node.naturalWidth)).toBeGreaterThan(0)
    await preview.getByRole('button', { name: /查看大图/ }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('img', { name: fixture.image })).toBeVisible()
    await dialog.locator('.el-dialog__headerbtn').click()
    await expect(dialog).toBeHidden()

    await files.getByRole('button', { name: fixture.text, exact: true }).click()
    await expect(preview).toContainText(fixture.excerpt)
    await expect(preview.locator('pre > *')).toHaveCount(0)
    await expect(preview.getByRole('img')).toHaveCount(0)
    await expect(page.getByRole('dialog')).toHaveCount(0)
  })
}

test('切换到文本后，迟到的 OSS 图片签名不会覆盖当前预览', async ({ page }) => {
  let pending: Route | undefined
  await page.route('**/api/v1/oss/browser/url?*', async (route) => {
    const key = new URL(route.request().url()).searchParams.get('key')
    if (key?.endsWith('0001.jpg')) {
      pending = route
    }
    else {
      await route.fallback()
    }
  })
  await page.goto('/oss-browser')
  const address = page.getByRole('textbox', { name: '目录路径' })
  await address.fill(cases[1].path)
  await address.press('Enter')
  await page.getByRole('button', { name: cases[1].image, exact: true }).click()
  await expect.poll(() => Boolean(pending)).toBe(true)
  const cancelled = page.waitForEvent('requestfailed', { predicate: request => request.url().includes('0001.jpg') })
  await page.getByRole('button', { name: cases[1].text, exact: true }).click()
  await cancelled
  await pending!.fulfill({ json: { code: 200, data: { ossUrl: '/pwa/icon-512.png' } } }).catch(() => {})
  const preview = page.getByRole('region', { name: '内容预览' })
  await expect(preview).toContainText('<示例>')
  await expect(preview.getByRole('img')).toHaveCount(0)
})

test('Nginx 实际响应超过文本预览预算时显示提示', async ({ page }) => {
  await page.route('**/api/v1/nginx-browser/file?*', route => route.fulfill({
    contentType: 'text/plain; charset=utf-8',
    body: 'a'.repeat(128 * 1024 + 1),
  }))
  await page.goto('/nginx-browser')
  const address = page.getByRole('textbox', { name: '目录路径' })
  await address.fill(cases[0].path)
  await address.press('Enter')
  await page.getByRole('button', { name: cases[0].text, exact: true }).click()
  const preview = page.getByRole('region', { name: '内容预览' })
  await expect(preview).toContainText('文件超出预览上限')
  await expect(preview.locator('pre')).toHaveCount(0)
})

test('图片加载完成前后预览区与详情栏高度不变', async ({ page }) => {
  let release: (() => void) | undefined
  const gate = new Promise<void>(resolve => release = resolve)
  await page.route('**/api/v1/nginx-browser/file?*', async (route) => {
    await gate
    await route.fallback()
  })

  await page.goto('/nginx-browser')
  const address = page.getByRole('textbox', { name: '目录路径' })
  await address.fill(cases[0].path)
  await address.press('Enter')
  await page.getByRole('button', { name: cases[0].image, exact: true }).click()

  const details = page.getByRole('complementary', { name: '文件详情' })
  const preview = details.getByRole('region', { name: '内容预览' })
  await expect(preview).toContainText('正在读取文件')
  const loading = { preview: await preview.boundingBox(), details: await details.boundingBox() }
  expect(loading.preview).not.toBeNull()
  expect(loading.details).not.toBeNull()

  release!()
  const image = preview.getByRole('img', { name: cases[0].image })
  await expect(image).toBeVisible()
  await expect.poll(() => image.evaluate((node: HTMLImageElement) => node.naturalWidth)).toBeGreaterThan(0)
  const loaded = { preview: await preview.boundingBox(), details: await details.boundingBox() }
  expect(loaded.preview).not.toBeNull()
  expect(loaded.details).not.toBeNull()

  // 占位态与图片态必须共用同一个盒子高度，否则整页会被顶起来再落回，
  // 用户看到的就是「加载完成后高度闪烁」。
  expect(Math.abs(loaded.preview!.height - loading.preview!.height)).toBeLessThan(1)
  expect(Math.abs(loaded.details!.height - loading.details!.height)).toBeLessThan(1)
})

test('文本预览与图片预览共用同一高度，切换文件不带动详情栏抖动', async ({ page }) => {
  await page.goto('/nginx-browser')
  const address = page.getByRole('textbox', { name: '目录路径' })
  await address.fill(cases[0].path)
  await address.press('Enter')
  const files = page.getByRole('table', { name: '文件列表' })
  const details = page.getByRole('complementary', { name: '文件详情' })

  await files.getByRole('button', { name: cases[0].image, exact: true }).click()
  const preview = details.getByRole('region', { name: '内容预览' })
  await expect(preview.getByRole('img', { name: cases[0].image })).toBeVisible()
  const imageHeight = (await preview.boundingBox())!.height

  await files.getByRole('button', { name: cases[0].text, exact: true }).click()
  await expect(preview).toContainText(cases[0].excerpt)
  const textHeight = (await preview.boundingBox())!.height

  expect(Math.abs(textHeight - imageHeight)).toBeLessThan(1)
})
