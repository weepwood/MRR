import type { Route } from '@playwright/test'
import { expect, test } from '@playwright/test'

const server = { key: 'default', name: '默认节点', configured: true }

function directory(path: string, offset = 0, serverKey = server.key) {
  const name = path === '' ? '样例目录' : offset ? 'page-2.png' : 'page-1.png'
  return {
    code: 200,
    data: {
      server: serverKey,
      path,
      offset,
      limit: 200,
      totalEntries: path ? 201 : 1,
      loadedDirectories: path ? 0 : 1,
      loadedFiles: path ? 1 : 0,
      loadedBytes: 0,
      entries: [{ name, directory: !path, path: path ? `${path}${name}` : `${name}/`, size: 12 }],
    },
  }
}

test('返回目录失败后保留原位置和历史，并重试失败的目标', async ({ page }) => {
  let failReturn = false
  await page.route('**/api/v1/nginx-browser/servers', route => route.fulfill({ json: { code: 200, data: [server] } }))
  await page.route(/\/api\/v1\/nginx-browser(?:\?|$)/, async (route) => {
    const path = new URL(route.request().url()).searchParams.get('path') ?? ''
    if (!path && failReturn) {
      failReturn = false
      await route.fulfill({ status: 503, json: { code: 503, message: '模拟目录请求失败' } })
      return
    }
    await route.fulfill({ json: directory(path) })
  })

  await page.goto('/nginx-browser')
  await page.locator('.file-pane').getByText('样例目录', { exact: true }).dblclick()
  await expect(page.locator('.file-pane').getByText('page-1.png')).toBeVisible()
  failReturn = true
  const back = page.getByRole('button', { name: '返回', exact: true })
  await back.click()
  await expect(page.getByText('目录读取失败，仍显示上次成功读取的内容。')).toBeVisible()
  await expect(page.locator('.file-pane').getByText('page-1.png')).toBeVisible()
  await expect(back).toBeEnabled()
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.locator('.file-pane').getByText('样例目录', { exact: true })).toBeVisible()
  await expect(back).toBeDisabled()
})

test('翻页失败保留当前页，较慢的旧目录不能覆盖新目录', async ({ page }) => {
  let failNextPage = true
  let slow: Route | undefined
  await page.route('**/api/v1/nginx-browser/servers', route => route.fulfill({ json: { code: 200, data: [server] } }))
  await page.route(/\/api\/v1\/nginx-browser(?:\?|$)/, async (route) => {
    const query = new URL(route.request().url()).searchParams
    const path = query.get('path') ?? ''
    const offset = Number(query.get('offset'))
    if (offset && failNextPage) {
      failNextPage = false
      await route.fulfill({ status: 503, json: { code: 503 } })
      return
    }
    if (path === 'slow/') {
      slow = route
      return
    }
    await route.fulfill({ json: directory(path, offset) })
  })
  await page.goto('/nginx-browser')
  const address = page.getByRole('textbox', { name: '目录路径' })
  await address.fill('slow')
  await address.press('Enter')
  await expect.poll(() => Boolean(slow)).toBe(true)
  await address.fill('new')
  await address.press('Enter')
  await expect(page.getByLabel('当前位置')).toContainText('new')
  await slow!.fulfill({ json: directory('slow/') }).catch(() => {})
  await expect(page.getByLabel('当前位置')).not.toContainText('slow')

  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page.getByText('目录读取失败，仍显示上次成功读取的内容。')).toBeVisible()
  await expect(page.locator('.file-pane')).toContainText('page-1.png')
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.locator('.file-pane')).toContainText('page-2.png')
  await page.getByRole('button', { name: '上一页' }).click()
  await expect(page.locator('.file-pane')).toContainText('page-1.png')
})

test('窄屏使用键盘打开目录，关闭预览会取消旧文件请求', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ layout: { enableMobileAdaptation: true } }))
  })
  await page.route('**/api/v1/nginx-browser/servers', route => route.fulfill({ json: { code: 200, data: [server] } }))
  await page.route(/\/api\/v1\/nginx-browser(?:\?|$)/, (route) => {
    const path = new URL(route.request().url()).searchParams.get('path') ?? ''
    return route.fulfill({ json: directory(path) })
  })
  let pending: Route | undefined
  await page.route('**/api/v1/nginx-browser/file?*', (route) => {
    pending = route
  })
  await page.goto('/nginx-browser')
  await page.getByRole('button', { name: '列表视图' }).click()
  await page.getByRole('table', { name: '文件列表' }).getByRole('button', { name: '样例目录' }).press('Enter')
  await page.getByRole('table', { name: '文件列表' }).getByRole('button', { name: 'page-1.png' }).click()
  const details = page.getByRole('complementary', { name: '文件详情' })
  await expect(details.getByRole('button', { name: '下载' })).toBeVisible()
  await expect(details.getByRole('button', { name: '复制路径' })).toBeVisible()
  await details.getByRole('button', { name: '预览' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect.poll(() => Boolean(pending)).toBe(true)
  const aborted = page.waitForEvent('requestfailed', { predicate: request => request.url().includes('/nginx-browser/file?'), timeout: 5000 })
  await page.locator('.el-dialog__headerbtn').click()
  await aborted
  await pending!.fulfill({ contentType: 'image/png', body: 'synthetic' }).catch(() => {})
  await expect(page.getByRole('dialog')).toBeHidden()
})

test('节点切换失败保留原目录，权限被撤销后清空旧结果', async ({ page }) => {
  const alternate = { key: 'BA01', name: 'BA01', configured: true }
  let deny = false
  await page.route('**/api/v1/nginx-browser/servers', route => route.fulfill({ json: { code: 200, data: [server, alternate] } }))
  await page.route(/\/api\/v1\/nginx-browser(?:\?|$)/, async (route) => {
    const query = new URL(route.request().url()).searchParams
    if (query.get('server') === alternate.key) {
      await route.fulfill({ status: 503, json: { code: 503 } })
    }
    else if (deny) {
      await route.fulfill({ status: 403, json: { code: 403 } })
    }
    else {
      await route.fulfill({ json: directory(query.get('path') ?? '') })
    }
  })
  await page.goto('/nginx-browser')
  await expect(page.locator('.file-pane')).toContainText('样例目录')
  await page.locator('.node-select').click()
  await page.getByRole('option', { name: 'BA01' }).click()
  await expect(page.getByText('目录读取失败，仍显示上次成功读取的内容。')).toBeVisible()
  await expect(page.getByLabel('当前位置')).toHaveText('默认节点')
  await expect(page.locator('.file-pane')).toContainText('样例目录')

  deny = true
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.getByText('当前账号没有文件浏览权限。')).toBeVisible()
  await expect(page.locator('.file-pane')).not.toContainText('样例目录')
  await expect(page.getByRole('textbox', { name: '目录路径' })).toBeDisabled()
})
