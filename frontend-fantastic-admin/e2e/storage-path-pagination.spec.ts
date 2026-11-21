import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

const ok = (data: unknown) => ({ json: { code: 200, data } })
const name = 'audit-target.bin'
const firstPage = Array.from({ length: 200 }, (_, i) => ({
  name: `sample-${String(i + 1).padStart(3, '0')}.bin`,
  directory: false,
  size: 4,
}))

async function storageRepro(page: Page, oss: boolean) {
  const root = oss ? 'audit-samples/' : ''
  const target = root + name
  const reads: number[] = []
  if (!oss) {
    await page.route('**/api/v1/nginx-browser/servers', route =>
      route.fulfill(ok([{ key: 'default', name: '审查样例节点', configured: true }])))
  }
  const endpoint = oss ? /\/api\/v1\/oss\/browser(?:\?|$)/ : /\/api\/v1\/nginx-browser(?:\?|$)/
  await page.route(endpoint, (route) => {
    const query = new URL(route.request().url()).searchParams
    const second = oss ? query.get('continuationToken') === 'audit-next' : Number(query.get('offset')) === 200
    reads.push(second ? 2 : 1)
    const files = second ? [{ name, directory: false, size: 4 }] : firstPage
    const entries = files.map(entry => ({ ...entry, [oss ? 'key' : 'path']: root + entry.name }))
    const base = { entries, truncated: !second, loadedDirectories: 0, loadedFiles: entries.length, loadedBytes: entries.length * 4 }
    return route.fulfill(ok(oss
      ? { ...base, configured: true, rootPrefix: root, prefix: root, nextContinuationToken: second ? null : 'audit-next', maxKeys: 200 }
      : { ...base, server: 'default', path: root, offset: second ? 200 : 0, limit: 200, totalEntries: 201 }))
  })
  await page.goto(oss ? '/oss-browser' : '/nginx-browser')
  const nextPage = page.getByRole('button', { name: '下一页', exact: true })
  await expect(nextPage).toBeEnabled()
  await nextPage.click()
  await expect(page.locator('.file-pane').getByText(name, { exact: true })).toBeVisible()
  const before = reads.length
  const address = page.getByRole('textbox', { name: '目录路径' })
  await address.fill(target)
  await address.press('Enter')
  await expect(page.getByRole('complementary', { name: '文件详情' }).getByRole('heading', { name, exact: true })).toBeVisible()
  expect(reads.slice(before)).toEqual([1, 2])
  await page.getByRole('button', { name: '上一页', exact: true }).click()
  await expect(page.locator('.file-pane').getByText('sample-001.bin', { exact: true })).toBeVisible()
}

for (const kind of ['OSS', 'Nginx']) {
  test(`${kind} 粘贴路径能定位到第二页，且可以向前翻页`, async ({ page }) => {
    await storageRepro(page, kind === 'OSS')
  })
}

test('Nginx 输入带点目录名后仍可返回上一目录', async ({ page }) => {
  await page.route('**/api/v1/nginx-browser/servers', route =>
    route.fulfill(ok([{ key: 'default', name: '审查样例节点', configured: true }])))
  await page.route(/\/api\/v1\/nginx-browser(?:\?|$)/, (route) => {
    const path = new URL(route.request().url()).searchParams.get('path') ?? ''
    const entries = path
      ? [{ name: 'sample.bin', path: `${path}sample.bin`, directory: false, size: 4 }]
      : [{ name: 'v1.2', path: 'v1.2/', directory: true, size: 0 }]
    return route.fulfill(ok({ server: 'default', path, offset: 0, limit: 200, totalEntries: entries.length, entries }))
  })

  await page.goto('/nginx-browser')
  await expect(page.locator('.file-pane').getByText('v1.2', { exact: true })).toBeVisible()
  const address = page.getByRole('textbox', { name: '目录路径' })
  await address.fill('v1.2')
  await address.press('Enter')
  await expect(page.locator('.file-pane').getByText('sample.bin', { exact: true })).toBeVisible()
  const back = page.getByRole('button', { name: '返回', exact: true })
  await expect(back).toBeEnabled()
  await back.click()
  await expect(page.locator('.file-pane').getByText('v1.2', { exact: true })).toBeVisible()
})
