import assert from 'node:assert/strict'
import { chromium } from '@playwright/test'
import { preview } from 'vite'

const origin = 'http://127.0.0.1:4177'
const appUrl = `${origin}/MRR/`
const server = await preview({
  mode: 'pages',
  preview: { host: '127.0.0.1', port: 4177, strictPort: true },
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

  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForFunction(() => Boolean(document.querySelector('#app')?.children.length), null, { timeout: 45_000 })
  assert.match(page.url(), /\/MRR\/#\/records/, '刷新必须保留 hash 路由')
  assert.deepEqual(pageErrors, [], '浏览器运行不应出现未处理异常')
  console.log('Pages 静态演示冒烟通过：子路径、hash 刷新与浏览器 Mock API')
}
finally {
  await browser?.close()
  await new Promise((resolve, reject) => server.httpServer.close(error => error ? reject(error) : resolve()))
}
