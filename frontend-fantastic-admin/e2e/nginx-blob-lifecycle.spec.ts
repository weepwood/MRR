import type { BrowserContext } from '@playwright/test'
import { expect, test } from '@playwright/test'

const ok = (data: unknown) => ({ json: { code: 200, data } })
async function nginxBlobRepro(ctx: BrowserContext) {
  const page = await ctx.newPage()
  const text = 'synthetic-audit-text'
  await page.route('**/api/v1/nginx-browser/servers', route =>
    route.fulfill(ok([{ key: 'default', name: '审查样例节点', configured: true }])))
  await page.route(/\/api\/v1\/nginx-browser(?:\?|$)/, route => route.fulfill(ok({
    path: '',
    server: 'default',
    offset: 0,
    limit: 200,
    totalEntries: 1,
    loadedFiles: 1,
    loadedDirectories: 0,
    loadedBytes: text.length,
    entries: [{ name: 'sample.txt', path: 'sample.txt', directory: false, size: text.length }],
  })))
  await page.route(/\/api\/v1\/nginx-browser\/file(?:\?|$)/, route =>
    route.fulfill({ contentType: 'text/plain', body: text }))
  await page.goto('/nginx-browser')
  const urls: string[] = []
  const entry = page.locator('.file-pane').getByText('sample.txt', { exact: true })
  for (let i = 0; i < 3; i++) {
    const popupReady = ctx.waitForEvent('page')
    await entry.dblclick()
    const popup = await popupReady
    await popup.waitForURL(/^blob:/)
    urls.push(popup.url())
    await popup.close()
  }
  expect(new Set(urls).size).toBe(3)
  await expect.poll(() => page.evaluate(async addresses =>
    Promise.all(addresses.map(url => fetch(url).then(() => true, () => false))), urls)).toEqual([false, false, false])

  const popupReady = ctx.waitForEvent('page')
  await entry.dblclick()
  const popup = await popupReady
  await popup.waitForURL(/^blob:/)
  await popup.reload()
  await expect(popup.locator('body')).toContainText(text)
  await popup.close()
}

test('Nginx 回收已关闭标签页的 Blob，仍打开的标签页可刷新', async ({ context }) => {
  await nginxBlobRepro(context)
})

test('超过浏览器预算的 Nginx 文件在发起读取前提示', async ({ page }) => {
  const name = 'large-synthetic.txt'
  let fileReads = 0
  await page.route('**/api/v1/nginx-browser/servers', route =>
    route.fulfill(ok([{ key: 'default', name: '审查样例节点', configured: true }])))
  await page.route(/\/api\/v1\/nginx-browser(?:\?|$)/, route => route.fulfill(ok({
    path: '',
    server: 'default',
    offset: 0,
    limit: 200,
    totalEntries: 1,
    entries: [{ name, path: name, directory: false, size: 129 * 1024 * 1024 }],
  })))
  await page.route(/\/api\/v1\/nginx-browser\/file(?:\?|$)/, (route) => {
    fileReads++
    return route.fulfill({ contentType: 'text/plain', body: '' })
  })
  await page.goto('/nginx-browser')
  await page.locator('.file-pane').getByText(name, { exact: true }).dblclick()
  await expect(page.getByText('浏览器文件读取上限为 128 MiB。', { exact: false })).toBeVisible()
  expect(fileReads).toBe(0)
})
