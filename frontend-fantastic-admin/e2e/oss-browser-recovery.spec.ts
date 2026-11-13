import type { Route } from '@playwright/test'
import { expect, test } from '@playwright/test'

const root = 'medical-records/'
const file = (name: string) => ({ name, key: root + name, directory: false, size: 12 })

test('翻页/返回失败仍可重试，明确标识旧目录', async ({ page }) => {
  test.setTimeout(60_000)
  let failNext = true
  let failBack = true
  await page.route(/\/api\/v1\/oss\/browser(?:\?|$)/, async (route) => {
    const query = new URL(route.request().url()).searchParams
    const token = query.get('continuationToken')
    if ((token === 'second' && failNext) || (token === null && !failNext && failBack)) {
      if (token) { failNext = false }
      else { failBack = false }
      await route.fulfill({ status: 503, json: { code: 503, message: '合成故障' } })
      return
    }
    await route.fulfill({ json: { code: 200, data: { configured: true, rootPrefix: root, prefix: root, entries: [file(token ? '第二页.png' : '第一页.png')], truncated: !token, nextContinuationToken: token ? null : 'second', maxKeys: 200, loadedDirectories: 0, loadedFiles: 1, loadedBytes: 12 } } })
  })
  await page.goto('/oss-browser')
  await expect(page.locator('.file-tile-name')).toHaveText('第一页.png')
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page.getByText('读取失败，当前仍显示上次成功读取的目录。请重试原操作或刷新。')).toBeVisible()
  await expect(page.getByRole('button', { name: '上一页' })).toBeDisabled()
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page.locator('.file-tile-name')).toHaveText('第二页.png')
  await page.getByRole('button', { name: '上一页' }).click()
  await expect(page.getByText('读取失败，当前仍显示上次成功读取的目录。请重试原操作或刷新。')).toBeVisible()
  await expect(page.locator('.file-tile-name')).toHaveText('第二页.png')
  await expect(page.getByRole('button', { name: '上一页' })).toBeEnabled()
  await page.getByRole('button', { name: '上一页' }).click()
  await expect(page.locator('.file-tile-name')).toHaveText('第一页.png')
  await expect(page.getByRole('button', { name: '上一页' })).toBeDisabled()
})

test('关闭旧预览后，迟到签名不能替换新图片', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  let pending: Route | undefined
  await page.route(/\/api\/v1\/oss\/browser(?:\?|$)/, route => route.fulfill({ json: { code: 200, data: { configured: true, rootPrefix: root, prefix: root, entries: [file('a.png'), file('b.png')], truncated: false, maxKeys: 200, loadedDirectories: 0, loadedFiles: 2, loadedBytes: 24 } } }))
  await page.route(/\/api\/v1\/oss\/browser\/url(?:\?|$)/, async (route) => {
    const key = new URL(route.request().url()).searchParams.get('key')
    if (key?.endsWith('a.png')) {
      pending = route
      return
    }
    await route.fulfill({ json: { code: 200, data: { ossUrl: '/synthetic-b.svg' } } })
  })
  await page.route('**/synthetic-*.svg', route => route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="blue"/></svg>' }))
  await page.goto('/oss-browser')
  await page.locator('.file-tile').filter({ hasText: 'a.png' }).click()
  await page.locator('.details-actions').getByRole('button', { name: '预览', exact: true }).click()
  await expect.poll(() => Boolean(pending)).toBe(true)
  await page.locator('.el-dialog__headerbtn').click()
  await expect(page.getByRole('dialog')).toBeHidden()
  await page.locator('.file-tile').filter({ hasText: 'b.png' }).click()
  await page.locator('.details-actions').getByRole('button', { name: '预览', exact: true }).click()
  await expect(page.locator('.preview-content img')).toHaveAttribute('src', '/synthetic-b.svg')
  const finished = page.waitForEvent('requestfinished', request => request.url().includes('/oss/browser/url') && request.url().includes('a.png'))
  await pending!.fulfill({ json: { code: 200, data: { ossUrl: '/synthetic-a.svg' } } })
  await finished
  await page.evaluate(() => new Promise(requestAnimationFrame))
  await expect(page.locator('.preview-content img')).toHaveAttribute('src', '/synthetic-b.svg')
})
