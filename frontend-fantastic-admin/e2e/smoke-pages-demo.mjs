import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import process from 'node:process'
import { chromium } from '@playwright/test'
import { preview } from 'vite'

async function smokePagesDemo() {
  const origin = 'http://127.0.0.1:4177'
  const appUrl = `${origin}/MRR/`
  const server = await preview({
    mode: 'pages',
    preview: { host: '127.0.0.1', port: 4177, strictPort: true },
  })
  const outboundApiRequests = []
  server.httpServer.prependListener('request', (request) => {
    if (request.url?.startsWith('/proxy/')) {
      outboundApiRequests.push(request.url)
    }
  })
  let browser

  try {
    const indexResponse = await fetch(appUrl)
    assert.equal(indexResponse.status, 200, 'Pages 子路径应返回入口 HTML')
    const html = await indexResponse.text()
    assert.match(html, /(?:src|href)="\/MRR\//, '打包资源必须使用 /MRR/ 作为 base')
    assert.doesNotMatch(html, /(?:src|href)="\/assets\//, '不能使用根路径资产链接')

    browser = await chromium.launch({ headless: true })
    const page = await browser.newPage()
    const pageErrors = []
    page.on('pageerror', error => pageErrors.push(error.message))

    const response = await page.goto(`${appUrl}#/records`, { waitUntil: 'domcontentloaded' })
    assert.equal(response?.status(), 200, 'hash 深链必须落在项目入口')
    await page.waitForFunction(() => Boolean(document.querySelector('#app')?.children.length), null, { timeout: 45_000 })

    const mockResponses = await page.evaluate(async () => {
      const [user, scans] = await Promise.all([
        fetch('/proxy/api/v1/auth/me').then(response => response.json()),
        fetch('/proxy/api/v1/scan/page?page=1&size=3').then(response => response.json()),
      ])
      return { user, scans }
    })
    assert.equal(mockResponses.user?.data?.username, 'admin', '用户信息必须来自浏览器 Mock')
    assert.equal(mockResponses.scans?.data?.list?.length, 3, '扫描记录必须由浏览器 Mock 返回')

    const blocked = await page.evaluate(async () => {
      const path = '/proxy/api/v1/pages-smoke-unmocked'
      const fetchBlocked = await fetch(path, { method: 'POST', body: 'synthetic-demo-only' }).then(() => false, () => true)
      const xhrBlocked = await new Promise((resolve) => {
        const request = new XMLHttpRequest()
        request.open('POST', path)
        request.timeout = 5_000
        request.onerror = () => resolve(true)
        request.onload = () => resolve(false)
        request.ontimeout = () => resolve(false)
        request.send('synthetic-demo-only')
      })
      return { fetchBlocked, xhrBlocked }
    })
    assert.deepEqual(blocked, { fetchBlocked: true, xhrBlocked: true }, '未模拟的 API 请求必须由浏览器拦截')

    await page.reload({ waitUntil: 'domcontentloaded' })
    await page.waitForFunction(() => Boolean(document.querySelector('#app')?.children.length), null, { timeout: 45_000 })
    assert.match(page.url(), /\/MRR\/#\/records/, '刷新必须保留 hash 路由')
    const filePage = await browser.newPage()
    filePage.on('pageerror', error => pageErrors.push(error.message))
    await filePage.goto(`${appUrl}#/nginx-browser`)
    await filePage.getByText('样例目录', { exact: true }).first().click({ timeout: 15_000 })
    await filePage.getByRole('button', { name: '打开文件夹' }).click({ timeout: 15_000 })
    await filePage.getByText('存储说明.txt', { exact: true }).first().click({ timeout: 15_000 })
    const previewText = filePage.getByRole('region', { name: '内容预览' }).getByText('MRR 文件浏览演示数据', { exact: false })
    await previewText.waitFor({ timeout: 15_000 })

    const [download] = await Promise.all([
      filePage.waitForEvent('download', { timeout: 15_000 }),
      filePage.getByRole('button', { name: '下载', exact: true }).click(),
    ])
    assert.equal(download.suggestedFilename(), '存储说明.txt')
    assert.match(await readFile(await download.path(), 'utf8'), /MRR 文件浏览演示数据/)

    assert.deepEqual(outboundApiRequests, [], 'API 请求不能到达 Pages 静态服务器')
    assert.deepEqual(pageErrors, [], '浏览器运行不应出现未处理异常')
    process.stdout.write('Pages 静态演示冒烟通过：子路径、hash 刷新、浏览器 Mock API 及文件预览下载\n')
  }
  finally {
    await browser?.close()
    await new Promise((resolve, reject) => server.httpServer.close(error => error ? reject(error) : resolve()))
  }
}

smokePagesDemo().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
